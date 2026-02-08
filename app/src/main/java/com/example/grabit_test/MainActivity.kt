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

    // FPS 측정
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    // YOLOX 출력 shape (화면에 표시용, Logcat 대신)
    private var yoloxShapeInfo: String? = null
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

    private val TARGET_CONFIDENCE_THRESHOLD = 0.6f  // 60% 이상 확신 시에만 고정 (잘못된 클래스 방지)
    private val TARGET_ANY = "모든 상품"
    private val currentTargetLabel = AtomicReference<String>("")

    private lateinit var gyroManager: GyroTrackingManager

    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val REQUEST_CODE_PERMISSIONS = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        loadClassLabels()
        initYOLOX()
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
            onBoxUpdate = { rect: RectF ->
                runOnUiThread {
                    val box = OverlayView.DetectionBox(
                        label = lockedTargetLabel,
                        confidence = 0.9f,
                        rect = rect,
                        topLabels = listOf(lockedTargetLabel to 90)
                    )
                    frozenBox = box
                    binding.overlayView.setDetections(listOf(box), frozenImageWidth, frozenImageHeight)
                }
            },
            onTrackingLost = { transitionToSearching() }
        )
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
            val modelFilename = "yolox_nano_640_gpu_fp16_background.tflite"
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
                                gyroManager.startTracking(matched.rect, w, h)
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
                    val box = frozenBox
                    val boxes = if (box != null) listOf(box) else emptyList()
                    displayResults(boxes, inferenceTime, frozenImageWidth, frozenImageHeight)
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
            yoloxShapeInfo = "YOLOX shape: dim1=$dim1 dim2=$dim2 → numBoxes=$numBoxes boxSize=$boxSize"
            yoloxShapeLogged = true
            // 첫 행 raw 값 (모델 출력 형식 확인용)
            if (isRowMajor && output.isNotEmpty() && output[0].size >= 6) {
                val r = output[0]
                Log.d(TAG, "YOLOX 출력 형식 | isNormalized=$isNormalized | row0: v0=${r[0]} v1=${r[1]} v2=${r[2]} v3=${r[3]} obj=${r.getOrNull(4)} clsMax=${r.getOrNull(5)}")
            }
        }

        val candidates = mutableListOf<OverlayView.DetectionBox>()
        val hasObjectness = (boxSize == 85 || boxSize == 58 || boxSize == 55)  // 80/53/50 classes
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

            val detailLines = detections.take(10).mapIndexed { i, d ->
                "${i + 1}) ${d.label} conf=${String.format("%.2f", d.confidence)} L=${d.rect.left.toInt()},T=${d.rect.top.toInt()},R=${d.rect.right.toInt()},B=${d.rect.bottom.toInt()}"
            }
            val debugText = buildString {
                yoloxShapeInfo?.let { append(it).append("\n") }
                if (detailLines.isNotEmpty()) {
                    append(if (searchState == SearchState.LOCKED) "고정된 영역:" else "감지상세(클래스|신뢰도|좌표):")
                    append("\n")
                    append(detailLines.joinToString("\n"))
                }
            }
            if (debugText.isNotBlank()) binding.yoloxShapeDebug.text = debugText
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
        gyroManager.stopTracking()
    }

    companion object {
        private const val TAG = "GrabIT_Test"
        private var yoloxShapeLogged = false
    }
}