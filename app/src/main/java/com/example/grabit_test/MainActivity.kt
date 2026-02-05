package com.example.grabitTest

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.grabitTest.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    // AI 모델
    private var yoloxInterpreter: Interpreter? = null
    // [추가] GPU 델리게이트 변수
    private var gpuDelegate: GpuDelegate? = null
    private var handLandmarker: HandLandmarker? = null

    // FPS 측정
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val REQUEST_CODE_PERMISSIONS = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 권한 확인
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // AI 모델 초기화
        initYOLOX()
        initMediaPipeHands()
    }

    private fun initYOLOX() {
        try {
            // [파일명 확인] assets 폴더에 이 파일이 꼭 있어야 합니다.
            val modelFilename = "yolox_nano_640_gpu_fp16.tflite"
            val modelFile = loadModelFile(modelFilename)

            val options = Interpreter.Options()

            // 🚀 GPU 가속 활성화 (FP16 모델용)
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                options.setAllowFp16PrecisionForFp32(true) // FP16 연산 허용

                Log.d(TAG, "🚀 GPU 가속 켜짐 (FP16)")
                runOnUiThread { binding.yoloxStatus.text = "📦 YOLOX: GPU (FP16)" }
            } catch (e: Exception) {
                Log.e(TAG, "❌ GPU 실패 -> CPU 전환", e)
                options.setNumThreads(4)
                gpuDelegate = null
                runOnUiThread { binding.yoloxStatus.text = "📦 YOLOX: CPU" }
            }

            yoloxInterpreter = Interpreter(modelFile, options)

        } catch (e: Exception) {
            Log.e(TAG, "YOLOX 초기화 실패", e)
            runOnUiThread { binding.yoloxStatus.text = "Error: Init Failed" }
        }
    }

    private fun initMediaPipeHands() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)

            Log.d(TAG, "✓ MediaPipe Hands 초기화 성공")
            runOnUiThread {
                binding.handsStatus.text = "🖐️ Hands: Ready"
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe Hands 초기화 실패", e)
            runOnUiThread {
                binding.handsStatus.text = "🖐️ Hands: Failed"
            }
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
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
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // [필수] 밀리면 버리기
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
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

    // 이미지 분석기
    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val startTime = System.currentTimeMillis()

            // Bitmap 변환
            val bitmap = imageProxy.toBitmap()
            if (bitmap == null) {
                imageProxy.close()
                return
            }

            // 1. YOLOX 추론
            val detections = runYOLOX(bitmap)

            // 2. MediaPipe Hands 추론
            val handsResult = runHands(bitmap, imageProxy.imageInfo.timestamp)

            // 3. 결과 표시
            val inferenceTime = System.currentTimeMillis() - startTime
            displayResults(detections, handsResult, inferenceTime)

            // FPS 계산
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

    private fun runYOLOX(bitmap: Bitmap): List<OverlayView.DetectionBox> {
        if (yoloxInterpreter == null) return emptyList()

        try {
            // 입력 크기 확인 (YOLOX-nano는 보통 416x416)
            val inputShape = yoloxInterpreter!!.getInputTensor(0).shape()
            val inputSize = inputShape[1]

            // 전처리 (위에서 수정한 함수 사용)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resizedBitmap, inputSize)

            // 출력 텐서 모양 확인
            val outputTensor = yoloxInterpreter!!.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            // 로그 확인 필수! -> Logcat에서 "Output Shape" 검색
            // Log.d(TAG, "Output Shape: ${outputShape.contentToString()}")

            // 출력 버퍼 생성 (동적으로 크기 할당)
            val output = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

            try {
                // [수정] 닫힌 인터프리터 실행 방지
                if (yoloxInterpreter != null) {
                    yoloxInterpreter!!.run(inputBuffer, output)
                } else {
                    return emptyList()
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "YOLOX 인터프리터가 이미 닫혔습니다. (앱 종료 중)")
                return emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "YOLOX 실행 중 오류 발생", e)
                return emptyList()
            }

            // 후처리
            return parseYOLOXOutput(output[0], bitmap.width, bitmap.height, inputSize)

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
            val r = (pixelValue shr 16 and 0xFF).toFloat()
            val g = (pixelValue shr 8 and 0xFF).toFloat()
            val b = (pixelValue and 0xFF).toFloat()

            // [수정] 정규화 제거 및 RGB 순서 적용
            // YOLOX 모델이 onnx2tf로 변환될 때 보통 정규화가 내장되거나 0-255 입력을 기대합니다.
            // 기존: BGR + 정규화 -> 점수 0.00001 (실패)
            // 변경: RGB + 0~255 범위 (나누기 X, 빼기 X)
            buffer.putFloat((b - 103.53f) / 57.375f)
            // G (Green)
            buffer.putFloat((g - 116.28f) / 57.12f)
            // R (Red)
            buffer.putFloat((r - 123.675f) / 58.395f)
        }
        buffer.rewind()

        return buffer
    }

    private fun parseYOLOXOutput(
        output: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int,
        inputSize: Int
    ): List<OverlayView.DetectionBox> {
        val detections = mutableListOf<OverlayView.DetectionBox>()
        val confidenceThreshold = 0.2f
        if (output.isNotEmpty()) {
            val firstBox = output[0]
            Log.d(TAG, "Raw Output Sample: [${firstBox[0]}, ${firstBox[1]}, ${firstBox[2]}, ${firstBox[3]}, Obj:${firstBox[4]}]")
        }
        var detectedCount = 0

        // YOLOX 출력 파싱 (형식에 따라 다름)
        // 일반적으로: [num_boxes, 85] (x, y, w, h, objectness, class_scores...)
        for (i in output.indices) {
            val box = output[i]
            val confidence = box[4] // objectness

            if (confidence > confidenceThreshold) {
                val cx = box[0] / inputSize * imageWidth
                val cy = box[1] / inputSize * imageHeight
                val w = box[2] / inputSize * imageWidth
                val h = box[3] / inputSize * imageHeight

                val left = max(0f, cx - w / 2)
                val top = max(0f, cy - h / 2)
                val right = min(imageWidth.toFloat(), cx + w / 2)
                val bottom = min(imageHeight.toFloat(), cy + h / 2)

                // 클래스 찾기
                val classScores = box.sliceArray(5 until box.size)
                val classId = classScores.indices.maxByOrNull { classScores[it] } ?: 0
                val finalScore = classScores[classId] * confidence

                if (finalScore > confidenceThreshold) {
                    detections.add(
                        OverlayView.DetectionBox(
                            label = "Class $classId", // 클래스 이름 매핑 전 임시 라벨
                            confidence = finalScore,
                            rect = android.graphics.RectF(left, top, right, bottom)
                        )
                    )
                    detectedCount++
                }
            }
        }
        Log.d(TAG, "최종 탐지된 개수: $detectedCount")
        return detections
    }

    private fun runHands(bitmap: Bitmap, timestamp: Long): HandLandmarkerResult? {
        if (handLandmarker == null) return null

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            return handLandmarker!!.detectForVideo(mpImage, timestamp / 1_000_000) // ns → ms
        } catch (e: Exception) {
            Log.e(TAG, "Hands 추론 실패", e)
            return null
        }
    }

    private fun displayResults(
        detections: List<OverlayView.DetectionBox>,
        handsResult: HandLandmarkerResult?,
        inferenceTime: Long
    ) {
        runOnUiThread {
            // 오버레이 업데이트
            binding.overlayView.setDetections(detections)
            binding.overlayView.setHands(handsResult)

            // 상태 텍스트 업데이트
            binding.yoloxStatus.text = "📦 YOLOX: ${detections.size} objects"

            val handsCount = handsResult?.landmarks()?.size ?: 0
            binding.handsStatus.text = "🖐️ Hands: $handsCount detected"

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
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
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

    }

    companion object {
        private const val TAG = "GrabIT_Test"
    }
}