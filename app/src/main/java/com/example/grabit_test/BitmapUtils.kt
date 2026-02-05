package com.example.grabitTest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BitmapUtils {

    /**
     * CameraX의 YUV 이미지를 안드로이드 Bitmap으로 변환
     */
    fun yuv420ToBitmap(image: Image): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * [핵심 추가] Bitmap을 YOLOX 모델 입력용 ByteBuffer로 변환 (정규화 포함)
     * - 리사이즈 (640x640)
     * - 정규화: (Pixel - Mean) / Std
     */
    fun bitmapToByteBuffer(
        bitmap: Bitmap,
        inputSize: Int = 640
    ): ByteBuffer {
        // Float32 (4바이트) * 가로 * 세로 * 3채널(RGB)
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        // 1. 모델 크기에 맞춰 리사이즈
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val intValues = IntArray(inputSize * inputSize)

        // 2. 픽셀 데이터 추출
        scaledBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // 3. YOLOX 표준 정규화 값 (ImageNet 기준)
        // 이 값이 적용되어야 점수가 정상적으로 나옵니다.
        val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f) // R, G, B
        val STD = floatArrayOf(58.395f, 57.12f, 57.375f)    // R, G, B

        for (pixelValue in intValues) {
            // Android Bitmap은 ARGB 순서 -> RGB 추출
            val r = (pixelValue shr 16 and 0xFF).toFloat()
            val g = (pixelValue shr 8 and 0xFF).toFloat()
            val b = (pixelValue and 0xFF).toFloat()

            // 4. 정규화 후 Buffer에 담기 (순서: RGB)
            // 만약 이렇게 해도 점수가 낮으면 아래 순서를 B, G, R 로 바꿔야 합니다.
            byteBuffer.putFloat((r - MEAN[0]) / STD[0])
            byteBuffer.putFloat((g - MEAN[1]) / STD[1])
            byteBuffer.putFloat((b - MEAN[2]) / STD[2])
        }

        return byteBuffer
    }
}