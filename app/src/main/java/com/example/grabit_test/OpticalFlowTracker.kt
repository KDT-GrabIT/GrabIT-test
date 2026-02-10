package com.example.grabitTest

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

/**
 * Sparse optical flow (Lucas-Kanade)로 화면 이동량 (dx, dy) 추정.
 * occlusion 시 배경 motion에 따라 박스 위치 보정에 사용.
 */
class OpticalFlowTracker {

    private var prevGray: Mat? = null
    private var prevPts: MatOfPoint2f? = null
    private var isInitialized = false
    private var lastImageWidth = 0
    private var lastImageHeight = 0

    /** 최대 특징점 개수 */
    private val maxCorners = 80
    /** 품질 임계값 (0.01~1.0) */
    private val qualityLevel = 0.1
    /** 특징점 간 최소 거리 (px) */
    private val minDistance = 15.0
    /** 흐름 추적에 사용할 최소 유효점 비율 */
    private val minValidRatio = 0.3f
    /** 원본 픽셀 기준: 이 크기 미만 이동은 노이즈로 무시 (0 반환) */
    private val noiseThresholdPx = 4f
    /** IQR 이상치 제거: Q1 - k*IQR ~ Q3 + k*IQR 밖이면 제외 */
    private val iqrK = 1.5f
    /** 처리용 축소 크기 (속도 최적화) */
    private val processWidth = 320
    private val processHeight = 240

    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV 초기화 실패")
        }
    }

    /**
     * 프레임 처리 후 화면 이동량 (dx, dy) 반환.
     * @param bitmap 현재 프레임
     * @param excludeHandRect 손 영역 (마스킹, image 좌표계)
     * @param excludeBoxRect 타겟 박스 영역 (마스킹, image 좌표계)
     * @return Pair(dx, dy) 또는 null (초기화 필요/유효 포인트 부족)
     */
    fun update(
        bitmap: Bitmap,
        excludeHandRect: RectF?,
        excludeBoxRect: RectF?
    ): Pair<Float, Float>? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val scaleX = w.toFloat() / processWidth
        val scaleY = h.toFloat() / processHeight

        val smallBitmap = Bitmap.createScaledBitmap(bitmap, processWidth, processHeight, true)
        val frame = Mat()
        Utils.bitmapToMat(smallBitmap, frame)
        smallBitmap.recycle()

        val gray = Mat()
        when (frame.channels()) {
            3 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY)
            4 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGBA2GRAY)
            else -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_RGB2GRAY)
        }

        val mask = createExcludeMask(processWidth, processHeight, excludeHandRect, excludeBoxRect, scaleX, scaleY)

        return try {
            if (!isInitialized) {
                val corners = MatOfPoint()
                Imgproc.goodFeaturesToTrack(
                    gray, corners, maxCorners, qualityLevel, minDistance,
                    mask, 3, false, 0.04
                )
                val pts = corners.toArray()
                mask.release()
                if (pts.isEmpty()) {
                    prevGray = gray
                    lastImageWidth = w
                    lastImageHeight = h
                    frame.release()
                    null
                } else {
                    prevPts = pointsToMatOfPoint2f(pts)
                    prevGray = gray
                    isInitialized = true
                    lastImageWidth = w
                    lastImageHeight = h
                    frame.release()
                    null
                }
            } else {
                val nextPts = MatOfPoint2f()
                val status = MatOfByte()
                val err = MatOfFloat()
                Video.calcOpticalFlowPyrLK(prevGray, gray, prevPts!!, nextPts, status, err)

                val prevArr = prevPts!!.toArray()
                val nextArr = nextPts.toArray()
                val statusArr = status.toArray()

                val validDx = mutableListOf<Float>()
                val validDy = mutableListOf<Float>()
                for (i in prevArr.indices) {
                    if (i < statusArr.size && statusArr[i].toInt() == 1) {
                        val dx = (nextArr[i].x - prevArr[i].x).toFloat()
                        val dy = (nextArr[i].y - prevArr[i].y).toFloat()
                        validDx.add(dx)
                        validDy.add(dy)
                    }
                }

                prevGray?.release()
                prevGray = gray
                prevPts?.release()
                prevPts = nextPts
                status.release()
                err.release()

                frame.release()
                mask.release()

                if (validDx.size < (prevArr.size * minValidRatio)) {
                    isInitialized = false
                    prevPts?.release()
                    prevPts = null
                    null
                } else {
                    // IQR로 이상치 제거 후 median
                    val (dxList, dyList) = filterOutliersIqr(validDx, validDy)
                    if (dxList.size < (validDx.size * 0.5f)) {
                        null
                    } else {
                        val medDx = median(dxList)
                        val medDy = median(dyList)
                        val scaledDx = medDx * scaleX
                        val scaledDy = medDy * scaleY
                        // 아주 작은 이동은 노이즈로 무시
                        if (kotlin.math.abs(scaledDx) < noiseThresholdPx && kotlin.math.abs(scaledDy) < noiseThresholdPx) {
                            null
                        } else {
                            Pair(scaledDx, scaledDy)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Optical flow 오류", e)
            isInitialized = false
            prevGray?.release()
            prevPts?.release()
            prevGray = null
            prevPts = null
            null
        }
    }

    private fun createExcludeMask(
        pw: Int, ph: Int,
        handRect: RectF?,
        boxRect: RectF?,
        scaleX: Float,
        scaleY: Float
    ): Mat {
        val mask = Mat(ph, pw, org.opencv.core.CvType.CV_8UC1, Scalar(255.0))
        fun exclude(r: RectF?) {
            if (r == null) return
            val left = (r.left / scaleX).toInt().coerceIn(0, pw - 1)
            val top = (r.top / scaleY).toInt().coerceIn(0, ph - 1)
            val right = (r.right / scaleX).toInt().coerceIn(0, pw)
            val bottom = (r.bottom / scaleY).toInt().coerceIn(0, ph)
            if (left < right && top < bottom) {
                Imgproc.rectangle(mask, org.opencv.core.Point(left.toDouble(), top.toDouble()),
                    org.opencv.core.Point(right.toDouble(), bottom.toDouble()),
                    Scalar(0.0), -1)
            }
        }
        exclude(handRect)
        exclude(boxRect)
        return mask
    }

    private fun pointsToMatOfPoint2f(pts: Array<Point>): MatOfPoint2f {
        return MatOfPoint2f(*pts)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    /** IQR 기반 이상치 제거: (dx, dy) 쌍 중 dx 또는 dy가 IQR 범위 밖이면 제외 */
    private fun filterOutliersIqr(dxList: List<Float>, dyList: List<Float>): Pair<List<Float>, List<Float>> {
        if (dxList.size != dyList.size || dxList.size < 4) return dxList to dyList
        fun bounds(list: List<Float>): Pair<Float, Float> {
            val sorted = list.sorted()
            val q1 = sorted[sorted.size / 4]
            val q3 = sorted[(3 * sorted.size) / 4]
            val iqr = (q3 - q1).coerceAtLeast(1e-6f)
            val lo = q1 - iqrK * iqr
            val hi = q3 + iqrK * iqr
            return lo to hi
        }
        val (dxLo, dxHi) = bounds(dxList)
        val (dyLo, dyHi) = bounds(dyList)
        val outDx = mutableListOf<Float>()
        val outDy = mutableListOf<Float>()
        for (i in dxList.indices) {
            val dx = dxList[i]
            val dy = dyList[i]
            if (dx in dxLo..dxHi && dy in dyLo..dyHi) {
                outDx.add(dx)
                outDy.add(dy)
            }
        }
        return outDx to outDy
    }

    fun reset() {
        prevGray?.release()
        prevPts?.release()
        prevGray = null
        prevPts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "OpticalFlowTracker"
    }
}
