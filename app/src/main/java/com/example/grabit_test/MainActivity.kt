package com.example.grabitTest

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.RectF
import com.example.grabitTest.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    // AI 모델
    private var yoloxInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var handLandmarker: HandLandmarker? = null

    // LIVE_STREAM: Hands 결과는 콜백으로 옴 → 최신 값만 보관
    private val latestHandsResult = AtomicReference<HandLandmarkerResult?>(null)

    // FPS 측정
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    private var lastYoloxMaxConf = 0f

    /** 클래스 인덱스 → 이름 (assets/classes.txt, 한 줄에 하나, # 무시) */
    private var classLabels: List<String> = emptyList()

    enum class SearchState { SEARCHING, LOCKED }
    private var searchState = SearchState.SEARCHING
    private var frozenBox: OverlayView.DetectionBox? = null
    private var frozenImageWidth = 0
    private var frozenImageHeight = 0
    /** State B에서 계속 추적할 타겟 라벨 (락 시점의 box.label) */
    private var lockedTargetLabel: String = ""

    /** 같은 객체가 연속 N프레임 감지됐을 때만 고정 (잘못된 클래스 방지) */
    private val LOCK_CONFIRM_FRAMES = 3
    private var pendingLockBox: OverlayView.DetectionBox? = null
    private var pendingLockCount = 0

    /** LOCKED 시 YOLOX 검증+보정: N프레임마다 1회 실행 (자이로 + 시각 보정) */
    private val VALIDATION_INTERVAL = 4
    private val VALIDATION_FAIL_LIMIT = 3
    private var validationFailCount = 0
    private var lockedFrameCount = 0

    private lateinit var gyroManager: GyroTrackingManager
    private val opticalFlowTracker = OpticalFlowTracker()

    private var wasOccluded = false  // occlusion → non-occlusion 전환 시 gyro 동기화용

    private val TARGET_CONFIDENCE_THRESHOLD = 0.6f  // 60% 이상 확신 시에만 고정 (잘못된 클래스 방지)
    private val TARGET_ANY = "모든 상품"
    private val currentTargetLabel = AtomicReference<String>("")

    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val REQUEST_CODE_PERMISSIONS = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        loadClassLabels()
        initYOLOX()
        initMediaPipeHands()
        setupTargetSpinner()
        initGyroTrackingManager()

        binding.startSearchBtn.setOnClickListener { onStartSearchClicked() }
        binding.resetBtn.setOnClickListener { gyroManager.resetToSearchingFromUI() }

        if (allPermissionsGranted()) {
            binding.startSearchBtn.visibility = View.VISIBLE
        } else {
            binding.startSearchBtn.visibility = View.GONE
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun onStartSearchClicked() {
        binding.startSearchBtn.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        binding.overlayView.visibility = View.VISIBLE
        startCamera()
    }

    private fun initGyroTrackingManager() {
        gyroManager = GyroTrackingManager(
            context = this,
            onBoxUpdate = { update: BoxUpdate ->
                runOnUiThread {
                    val box = OverlayView.DetectionBox(
                        label = lockedTargetLabel,
                        confidence = 0.9f,
                        rect = update.rect,
                        topLabels = listOf(lockedTargetLabel to 90),
                        rotationDegrees = update.rotationDegrees
                    )
                    frozenBox = box
                    binding.overlayView.setDetections(listOf(box), frozenImageWidth, frozenImageHeight)
                }
            },
            onTrackingLost = { transitionToSearching() }
        )
    }

    private fun initMediaPipeHands() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, image ->
                    latestHandsResult.set(result)
                }
                .setErrorListener { e ->
                    Log.e(TAG, "Hands LIVE_STREAM error", e)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)
            Log.d(TAG, "✓ MediaPipe Hands 초기화 성공")
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe Hands 초기화 실패", e)
        }
    }

    private fun setupTargetSpinner() {
        val spinnerItems = listOf(TARGET_ANY) + classLabels
        binding.targetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spinnerItems)
        currentTargetLabel.set(TARGET_ANY)
        binding.targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                currentTargetLabel.set(spinnerItems.getOrNull(pos) ?: "")
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun getTargetLabel(): String = currentTargetLabel.get()

    /** LOCKED 시 시각 보정: 이전 박스와 IoU가 가장 큰 타겟 detection 반환. 없으면 null
     * @param minConfidence occlusion 시 더 낮은 기준 사용 (예: 0.15f) */
    private fun findTrackedTarget(
        detections: List<OverlayView.DetectionBox>,
        targetLabel: String,
        prevBox: OverlayView.DetectionBox?,
        minConfidence: Float = TARGET_CONFIDENCE_THRESHOLD * 0.5f
    ): OverlayView.DetectionBox? {
        val target = targetLabel.trim()
        if (target.isBlank()) return null

        val candidates = detections.filter { d ->
            (d.label.trim().equals(target, ignoreCase = true) ||
                d.topLabels.any { it.first.trim().equals(target, ignoreCase = true) }) &&
            d.confidence >= minConfidence
        }.map { d ->
            if (d.label.trim().equals(target, ignoreCase = true)) d
            else {
                val conf = d.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f) ?: d.confidence
                d.copy(label = target, confidence = conf, topLabels = listOf(target to (conf * 100).toInt()))
            }
        }

        if (candidates.isEmpty()) return null
        return if (prevBox != null) {
            candidates.maxByOrNull { iou(it.rect, prevBox.rect) }
        } else {
            candidates.maxByOrNull { it.confidence }
        }
    }

    /** 타겟에 맞는 detection만 반환. "모든 상품"이면 전부, 특정 상품이면 해당 상품만. */
    private fun filterDetectionsByTarget(detections: List<OverlayView.DetectionBox>, targetLabel: String): List<OverlayView.DetectionBox> {
        val target = targetLabel.trim()
        if (target.isBlank() || target == TARGET_ANY) return detections

        return detections
            .mapNotNull { box ->
                val targetConf = when {
                    box.label.trim().equals(target, ignoreCase = true) -> box.confidence
                    else -> box.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f)
                }
                if (targetConf != null && targetConf >= TARGET_CONFIDENCE_THRESHOLD) {
                    if (box.label.trim().equals(target, ignoreCase = true)) box
                    else box.copy(
                        label = target,
                        confidence = targetConf,
                        topLabels = listOf(target to (targetConf * 100).toInt())
                    )
                } else null
            }
    }

    /** 타겟이 primary label 또는 topLabels에 있고 confidence >= 70%인 detection 중 최고 확률 선택.
     *  "모든 상품" 선택 시: confidence >= 70%인 detection 중 최고 확률 반환 */
    private fun findTargetMatch(detections: List<OverlayView.DetectionBox>, targetLabel: String): OverlayView.DetectionBox? {
        val target = targetLabel.trim()
        if (target.isBlank()) return null

        if (target == TARGET_ANY) {
            return detections.filter { it.confidence >= TARGET_CONFIDENCE_THRESHOLD }.maxByOrNull { it.confidence }
        }

        return detections
            .mapNotNull { box ->
                val targetConf = when {
                    box.label.trim().equals(target, ignoreCase = true) -> box.confidence
                    else -> box.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f)
                }
                if (targetConf != null && targetConf >= TARGET_CONFIDENCE_THRESHOLD) {
                    if (box.label.trim().equals(target, ignoreCase = true)) box
                    else box.copy(
                        label = target,
                        confidence = targetConf,
                        topLabels = listOf(target to (targetConf * 100).toInt())
                    )
                } else null
            }
            .maxByOrNull { it.confidence }
    }

    private fun transitionToLocked(box: OverlayView.DetectionBox, imageWidth: Int, imageHeight: Int) {
        runOnUiThread {
            searchState = SearchState.LOCKED
            lockedTargetLabel = box.label
            validationFailCount = 0
            frozenImageWidth = imageWidth
            frozenImageHeight = imageHeight
            binding.resetBtn.visibility = View.VISIBLE
            binding.overlayView.setDetections(listOf(box), imageWidth, imageHeight)
            binding.overlayView.setFrozen(true)
            binding.yoloxStatus.text = "🔒 고정: ${box.label} (자이로)"
            binding.handsStatus.text = "📐 자이로: ON"
            Toast.makeText(this, "타겟 고정 → 자이로 추적 모드", Toast.LENGTH_SHORT).show()
        }
    }

    private fun transitionToSearching() {
        runOnUiThread {
            searchState = SearchState.SEARCHING
            lockedTargetLabel = ""
            pendingLockBox = null
            pendingLockCount = 0
            validationFailCount = 0
            wasOccluded = false
            opticalFlowTracker.reset()
            gyroManager.stopTracking()
            binding.resetBtn.visibility = View.GONE
            binding.overlayView.setDetections(emptyList(), 0, 0)
            binding.overlayView.setFrozen(false)
            binding.yoloxStatus.text = "🔍 탐색 중..."
            binding.handsStatus.text = "📐 자이로: OFF"
        }
    }

    /** assets/classes.txt 로드 (한 줄 = 한 클래스, 0번째 줄 = class 0). # 시작 줄 무시. */
    private fun loadClassLabels() {
        try {
            assets.open("classes.txt").use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    classLabels = reader.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                }
            }
            Log.d(TAG, "클래스 라벨 ${classLabels.size}개 로드")
        } catch (e: Exception) {
            Log.d(TAG, "classes.txt 없음 또는 로드 실패 → Object_N 표시", e)
            classLabels = emptyList()
        }
    }

    private fun getClassLabel(classId: Int): String {
        return classLabels.getOrNull(classId)?.takeIf { it.isNotBlank() } ?: "Object_$classId"
    }

    /** YOLOX preproc: BGR, 0~255 float. NCHW [1,3,H,W] 시 채널 선 출력 */
    private fun bitmapToFloatBuffer(bitmap: Bitmap, size: Int, isNchw: Boolean = false): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        if (isNchw) {
            for (c in 0 until 3) {  // B, G, R
                val shift = when (c) { 0 -> 0; 1 -> 8; else -> 16 }
                for (i in pixels.indices) {
                    buffer.putFloat(((pixels[i] shr shift) and 0xFF).toFloat())
                }
            }
        } else {
            for (p in pixels) {
                buffer.putFloat((p and 0xFF).toFloat())
                buffer.putFloat(((p shr 8) and 0xFF).toFloat())
                buffer.putFloat(((p shr 16) and 0xFF).toFloat())
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun initYOLOX() {
        try {
            val modelFilename = "yolox_nano_49cls_float16.tflite"
            val modelFile = loadModelFile(modelFilename)
            val options = Interpreter.Options()
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                options.setAllowFp16PrecisionForFp32(true)
                Log.d(TAG, "🚀 GPU 가속 켜짐 (FP16)")
                runOnUiThread { binding.yoloxStatus.text = "📦 YOLOX: GPU (FP16)" }
            } catch (e: Exception) {
                Log.e(TAG, "❌ GPU 실패 -> CPU 전환", e)
                options.setNumThreads(4)
                gpuDelegate = null
                runOnUiThread { binding.yoloxStatus.text = "📦 YOLOX: CPU" }
            }
            yoloxInterpreter = Interpreter(modelFile, options)
            val inputShape = yoloxInterpreter!!.getInputTensor(0).shape()
            val inputSize = if (inputShape.size >= 3 && inputShape[1] == 3) inputShape[2] else inputShape[1]
            Log.d(TAG, "YOLOX 로드 | $modelFilename | 입력 ${inputSize}x${inputSize}")
        } catch (e: Exception) {
            Log.e(TAG, "YOLOX 초기화 실패", e)
            runOnUiThread { binding.yoloxStatus.text = "Error: Init Failed" }
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        return buffer
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // ImageAnalysis
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            // 카메라 선택
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "카메라 바인딩 실패", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // YOLOX 진단 로그: 3초마다 한 번만 출력 (로그 과다 방지)
    private var lastYoloxDiagTimeMs = 0L
    private var firstInferenceLogged = false
    private var lastTargetMatchDiagMs = 0L

    // 이미지 분석기
    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val startTime = System.currentTimeMillis()

            // Bitmap 변환 (YUV_420_888 → RGB)
            var bitmap = imageProxy.toBitmap()
            if (bitmap == null) {
                imageProxy.close()
                return
            }
            // 세로 모드에서 카메라 센서는 보통 가로 → 회전 정보 적용해 모델 입력이 화면과 맞도록 함
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                bitmap = BitmapUtils.rotateBitmap(bitmap, rotationDegrees) ?: bitmap
            }

            val w = bitmap.width
            val h = bitmap.height
            val inferenceTime = System.currentTimeMillis() - startTime

            // MediaPipe Hands: LIVE_STREAM → 비동기 전달만 하고 즉시 return (블로킹 없음)
            sendFrameToHands(bitmap, imageProxy.imageInfo.timestamp)

            when (searchState) {
                SearchState.SEARCHING -> {
                    val detections = runYOLOX(bitmap)
                    val matched = findTargetMatch(detections, getTargetLabel())
                    if (matched != null && matched.confidence >= TARGET_CONFIDENCE_THRESHOLD) {
                        // 같은 클래스가 연속 감지됐는지 확인 (잘못된 클래스 방지)
                        val prev = pendingLockBox
                        if (prev != null && prev.label == matched.label &&
                            RectF.intersects(matched.rect, prev.rect)) {
                            pendingLockCount++
                            if (pendingLockCount >= LOCK_CONFIRM_FRAMES) {
                                pendingLockBox = null
                                pendingLockCount = 0
                                transitionToLocked(matched, w, h)
                                frozenBox = matched
                                frozenImageWidth = w
                                frozenImageHeight = h
                                wasOccluded = false
                                opticalFlowTracker.reset()
                                gyroManager.startTracking(matched.rect, w, h)
                                lockedFrameCount = 0
                                validationFailCount = 0
                                updateFPS()
                                imageProxy.close()
                                return
                            }
                        } else {
                            pendingLockBox = matched
                            pendingLockCount = 1
                        }
                    } else {
                        pendingLockBox = null
                        pendingLockCount = 0
                    }
                    displayResults(filterDetectionsByTarget(detections, getTargetLabel()), inferenceTime, w, h)
                }
                SearchState.LOCKED -> {
                    // YOLOX: N프레임마다 검증 + 시각 보정 (자이로 드리프트 보정)
                    lockedFrameCount++
                    val shouldValidate = (lockedFrameCount % VALIDATION_INTERVAL) == 0
                    var inferMs = System.currentTimeMillis() - startTime

                    val handsOverlap = frozenBox?.let { box ->
                        isHandOverlappingBox(latestHandsResult.get(), box.rect, w, h)
                    } ?: false

                    gyroManager.suspendUpdates = handsOverlap

                    if (handsOverlap) {
                        // occlusion: optical flow로 화면 이동량 추적 → 박스도 동일 이동
                        val handRect = mergedHandRect(latestHandsResult.get(), w, h)
                        val flow = opticalFlowTracker.update(bitmap, handRect, frozenBox?.rect)
                        flow?.let { (dx, dy) ->
                            val box = frozenBox ?: return@let
                            val r = box.rect
                            val newRect = RectF(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
                            gyroManager.correctPosition(newRect)
                            frozenBox = box.copy(rect = newRect)
                        }
                    } else {
                        // occlusion 해제 → gyro 기저 동기화
                        if (wasOccluded) {
                            frozenBox?.rect?.let { gyroManager.correctPosition(it) }
                        }
                        if (shouldValidate) {
                            val detections = runYOLOX(bitmap)
                            inferMs = System.currentTimeMillis() - startTime
                            val minConf = TARGET_CONFIDENCE_THRESHOLD * 0.5f
                            val tracked = findTrackedTarget(detections, lockedTargetLabel, frozenBox, minConf)
                            if (tracked != null) {
                                validationFailCount = 0
                                gyroManager.correctPosition(tracked.rect)
                                frozenBox = tracked.copy(rotationDegrees = 0f)
                                frozenImageWidth = w
                                frozenImageHeight = h
                            } else {
                                validationFailCount++
                                if (validationFailCount >= VALIDATION_FAIL_LIMIT) {
                                    validationFailCount = 0
                                    gyroManager.resetToSearchingFromUI()
                                }
                            }
                        }
                    }
                    wasOccluded = handsOverlap

                    val box = frozenBox
                    val boxes = if (box != null) listOf(box) else emptyList()
                    displayResults(boxes, inferMs, frozenImageWidth, frozenImageHeight)
                }
            }

            updateFPS()
            imageProxy.close()
        }

        @androidx.camera.core.ExperimentalGetImage
        private fun ImageProxy.toBitmap(): Bitmap? {
            val image = this.image ?: return null
            // YUV_420_888 → Bitmap 변환 (간단 버전)
            // 실제로는 더 최적화된 변환 필요
            return BitmapUtils.yuv420ToBitmap(image)
        }
    }

    /** 화면이 거의 안 보일 정도로 어두우면 true (손으로 가림 등) */
    private fun isImageTooDark(bitmap: Bitmap, threshold: Int = 35): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return true
        val step = max(1, min(w, h) / 20)
        var sum = 0L
        var count = 0
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val p = bitmap.getPixel(x, y)
                sum += ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
                count++
            }
        }
        if (count == 0) return true
        val avg = (sum / count) / 3
        return avg < threshold
    }

    private fun runYOLOX(bitmap: Bitmap): List<OverlayView.DetectionBox> {
        if (yoloxInterpreter == null) return emptyList()

        try {
            val inputShape = yoloxInterpreter!!.getInputTensor(0).shape()
            val isNchw = inputShape.size >= 4 && inputShape[1] == 3
            val inputSize = when {
                isNchw -> inputShape[2]
                inputShape.size >= 4 -> inputShape[1]
                else -> inputShape.last()
            }
            val expectedBytes = 4 * inputSize * inputSize * 3
            val preprocBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToFloatBuffer(preprocBitmap, inputSize, isNchw)
            if (inputBuffer.remaining() != expectedBytes) {
                Log.e(TAG, "YOLOX 입력 버퍼 크기 불일치: ${inputBuffer.remaining()} != $expectedBytes")
                return emptyList()
            }
            val inferStart = System.currentTimeMillis()
            val outputShape = yoloxInterpreter!!.getOutputTensor(0).shape()
            val output = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
            yoloxInterpreter!!.run(inputBuffer, output)
            val inferMs = System.currentTimeMillis() - inferStart
            if (isImageTooDark(bitmap)) {
                return emptyList()
            }
            val detections = parseYOLOXOutput(
                output[0],
                outputShape[1],
                outputShape[2],
                bitmap.width,
                bitmap.height,
                inputSize
            )
            if (!firstInferenceLogged) {
                firstInferenceLogged = true
                Log.d(TAG, "YOLOX 첫 추론 | 입력 bitmap ${bitmap.width}x${bitmap.height} → resize ${inputSize}x${inputSize} | 추론 ${inferMs}ms")
            }
            val now = System.currentTimeMillis()
            if (now - lastYoloxDiagTimeMs >= 3000) {
                lastYoloxDiagTimeMs = now
                Log.d(TAG, "YOLOX 진단 | 추론 ${inferMs}ms | 감지 ${detections.size}개 maxConf=${String.format("%.2f", lastYoloxMaxConf)}")
            }
            return detections
        } catch (e: Exception) {
            Log.e(TAG, "YOLOX 추론 실패", e)
            return emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap, size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        for (pixelValue in pixels) {
            // [수정] FP16 모델은 0~255 값을 0.0~1.0으로 '정규화' 해줘야 합니다!
            // 기존 코드: buffer.putFloat(...) -> 틀림
            // 수정 코드: 255.0f로 나누기

            buffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f) // R
            buffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)  // G
            buffer.putFloat((pixelValue and 0xFF) / 255.0f)          // B
        }

        return buffer
    }

    private fun parseYOLOXOutput(
        output: Array<FloatArray>,
        dim1: Int,
        dim2: Int,
        imageWidth: Int,
        imageHeight: Int,
        inputSize: Int
    ): List<OverlayView.DetectionBox> {
        val numBoxes: Int
        val boxSize: Int
        val isRowMajor: Boolean

        if (dim1 >= dim2) {
            numBoxes = dim1
            boxSize = dim2
            isRowMajor = true
        } else {
            numBoxes = dim2
            boxSize = dim1
            isRowMajor = false
        }

        val isNormalized = if (isRowMajor && output.isNotEmpty() && output[0].size >= 4) {
            maxOf(output[0][0], output[0][1], output[0][2], output[0][3]) <= 1.5f
        } else if (!isRowMajor && output.size >= 4) {
            maxOf(output[0][0], output[1][0], output[2][0], output[3][0]) <= 1.5f
        } else true

        if (!yoloxShapeLogged) {
            yoloxShapeLogged = true
            Log.d(TAG, "YOLOX shape: dim1=$dim1 dim2=$dim2 → numBoxes=$numBoxes boxSize=$boxSize")
        }

        val candidates = mutableListOf<OverlayView.DetectionBox>()
        // 4(box)+1(obj)+N(cls): 85(80), 58(53), 55(50), 54(49)
        val hasObjectness = (boxSize == 85 || boxSize == 58 || boxSize == 55 || boxSize == 54)
        val scoreStart = if (hasObjectness) 5 else 4
        val classCount = (boxSize - scoreStart).coerceAtLeast(1)

        val useSingleScoreFormat = (numBoxes <= 100 && boxSize >= 5)
        val minConfidenceToShow = when {
            useSingleScoreFormat -> 0.35f
            hasObjectness -> 0.45f
            else -> 0.78f
        }

        var maxConfidenceSeen = 0f

        for (j in 0 until numBoxes) {
            val v0: Float
            val v1: Float
            val v2: Float
            val v3: Float
            val classScores = FloatArray(classCount)
            var singleScore = 0f

            if (isRowMajor) {
                val row = output[j]
                if (row.size < boxSize) continue
                v0 = row[0]; v1 = row[1]; v2 = row[2]; v3 = row[3]
                if (row.size > 4) singleScore = row[4]
                for (c in 0 until classCount) classScores[c] = row[scoreStart + c]
            } else {
                v0 = output[0][j]; v1 = output[1][j]; v2 = output[2][j]; v3 = output[3][j]
                if (dim1 > 4) singleScore = output[4][j]
                for (c in 0 until classCount) classScores[c] = output[scoreStart + c][j]
            }

            val objScore = singleScore.coerceIn(0f, 1f)
            val topK = 3
            val topClassIds = classScores.indices
                .map { c ->
                    var rs = classScores[c]
                    if (rs > 1f || rs < 0f) rs = 1f / (1f + exp(-rs))
                    c to (objScore * rs).coerceIn(0f, 1f)
                }
                .sortedByDescending { it.second }
                .take(topK)
            val confidence = topClassIds.firstOrNull()?.second ?: 0f
            if (confidence > maxConfidenceSeen) maxConfidenceSeen = confidence
            if (confidence < minConfidenceToShow) continue

            // 50% 이상인 클래스만 표시 (상위 3개 중에서)
            val topLabels = topClassIds
                .filter { (_, conf) -> conf >= 0.5f }
                .map { (cid, conf) ->
                    getClassLabel(cid) to (conf * 100).toInt()
                }

            // 좌표: 정규화 0~1이고 v2>v0, v3>v1 이면 (x1,y1,x2,y2). 아니면 (cx,cy,w,h).
            val left: Float
            val top: Float
            val right: Float
            val bottom: Float
            if (isNormalized && v2 > v0 && v3 > v1) {
                left = (v0 * imageWidth).coerceIn(0f, imageWidth.toFloat())
                top = (v1 * imageHeight).coerceIn(0f, imageHeight.toFloat())
                right = (v2 * imageWidth).coerceIn(0f, imageWidth.toFloat())
                bottom = (v3 * imageHeight).coerceIn(0f, imageHeight.toFloat())
            } else {
                if (isNormalized) {
                    val cx = v0.coerceIn(0f, 1f) * imageWidth
                    val cy = v1.coerceIn(0f, 1f) * imageHeight
                    val w = (v2.coerceIn(0f, 1f) * imageWidth) / 2f
                    val h = (v3.coerceIn(0f, 1f) * imageHeight) / 2f
                    left = max(0f, cx - w)
                    top = max(0f, cy - h)
                    right = min(imageWidth.toFloat(), cx + w)
                    bottom = min(imageHeight.toFloat(), cy + h)
                } else {
                    val cx = v0 / inputSize * imageWidth
                    val cy = v1 / inputSize * imageHeight
                    val w = (v2 / inputSize * imageWidth) / 2f
                    val h = (v3 / inputSize * imageHeight) / 2f
                    left = max(0f, cx - w)
                    top = max(0f, cy - h)
                    right = min(imageWidth.toFloat(), cx + w)
                    bottom = min(imageHeight.toFloat(), cy + h)
                }
            }

            if (right <= left || bottom <= top) continue

            candidates.add(
                OverlayView.DetectionBox(
                    label = topLabels.firstOrNull()?.first ?: "",
                    confidence = confidence,
                    rect = android.graphics.RectF(left, top, right, bottom),
                    topLabels = topLabels
                )
            )
        }

        lastYoloxMaxConf = maxConfidenceSeen
        return nms(candidates, iouThreshold = 0.6f)
    }

    private fun nms(boxes: List<OverlayView.DetectionBox>, iouThreshold: Float): List<OverlayView.DetectionBox> {
        val sorted = boxes.sortedByDescending { it.confidence }
        val picked = mutableListOf<OverlayView.DetectionBox>()
        val used = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (used[i]) continue
            picked.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                if (iou(sorted[i].rect, sorted[j].rect) > iouThreshold) used[j] = true
            }
        }
        return picked
    }

    /** 손 랜드마크(정규화 0~1) → 이미지 좌표 RectF */
    private fun handLandmarksToRect(landmarks: List<NormalizedLandmark>, imageWidth: Int, imageHeight: Int): RectF {
        if (landmarks.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        val xs = landmarks.map { it.x().coerceIn(0f, 1f) * imageWidth }
        val ys = landmarks.map { it.y().coerceIn(0f, 1f) * imageHeight }
        return RectF(xs.min(), ys.min(), xs.max(), ys.max())
    }

    /** 손 랜드마크들 → 모든 손을 포함하는 단일 RectF (optical flow 마스킹용) */
    private fun mergedHandRect(handsResult: HandLandmarkerResult?, imageWidth: Int, imageHeight: Int): RectF? {
        val landmarks = handsResult?.landmarks() ?: return null
        if (landmarks.isEmpty()) return null
        var union: RectF? = null
        for (hand in landmarks) {
            val r = handLandmarksToRect(hand, imageWidth, imageHeight)
            union = if (union == null) r else {
                val u = RectF(union)
                u.union(r)
                u
            }
        }
        return union
    }

    /** 손이 박스와 겹치면 true (occlusion) */
    private fun isHandOverlappingBox(handsResult: HandLandmarkerResult?, boxRect: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val landmarks = handsResult?.landmarks() ?: return false
        if (imageWidth <= 0 || imageHeight <= 0) return false
        return landmarks.any { hand ->
            val handRect = handLandmarksToRect(hand, imageWidth, imageHeight)
            iou(handRect, boxRect) > 0.1f
        }
    }

    /** LIVE_STREAM: 프레임만 넘기고 즉시 return. 결과는 resultListener 콜백으로 옴. */
    private fun sendFrameToHands(bitmap: Bitmap, timestampNs: Long) {
        if (handLandmarker == null) return
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = timestampNs / 1_000_000
            handLandmarker!!.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Hands detectAsync 실패", e)
        }
    }

    private fun iou(a: android.graphics.RectF, b: android.graphics.RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val interArea = interW * interH
        val areaA = a.width() * a.height()
        val areaB = b.width() * b.height()
        return interArea / (areaA + areaB - interArea + 1e-6f)
    }

    private fun displayResults(
        detections: List<OverlayView.DetectionBox>,
        inferenceTime: Long,
        imageWidth: Int,
        imageHeight: Int
    ) {
        runOnUiThread {
            binding.overlayView.setDetections(detections, imageWidth, imageHeight)
            binding.overlayView.setHands(latestHandsResult.get())

            when (searchState) {
                SearchState.SEARCHING -> {
                    binding.yoloxStatus.text = "🔍 탐색: ${getTargetLabel()} (${detections.size}개 감지)"
                    binding.handsStatus.text = "📐 자이로: OFF"
                }
                SearchState.LOCKED -> {
                    val box = frozenBox
                    if (box != null) {
                        binding.yoloxStatus.text = "🔒 고정: ${box.label} (자이로)"
                    }
                    binding.handsStatus.text = "📐 자이로: ON"
                }
            }

            binding.inferenceTime.text = "⏱️ Inference: ${inferenceTime}ms"
        }
    }

    private fun updateFPS() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFpsTime >= 1000) {
            val fps = frameCount * 1000 / (currentTime - lastFpsTime)
            runOnUiThread {
                binding.fpsText.text = "FPS: $fps"
            }
            frameCount = 0
            lastFpsTime = currentTime
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                binding.startSearchBtn.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        yoloxInterpreter?.close()
        gpuDelegate?.close()
        handLandmarker?.close()
        if (::gyroManager.isInitialized) gyroManager.stopTracking()
    }

    companion object {
        private const val TAG = "GrabIT_Test"
        private var yoloxShapeLogged = false
    }
}