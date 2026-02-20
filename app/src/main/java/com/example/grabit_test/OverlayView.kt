package com.example.grabitTest

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 그릴 데이터
    private var detectionBoxes = listOf<DetectionBox>()
    private var handLandmarks = listOf<List<NormalizedLandmark>>()
    private var imageWidth = 0
    private var imageHeight = 0
    private var isFrozen = false

    /** 디버그: near-contact 엄지·검지 중간점 (이미지 픽셀). null이면 그리지 않음 */
    private var touchDebugPx: Pair<Float, Float>? = null
    /** 디버그: 터치 중간점 그리기 켜기 (개발용 토글) */
    private var showTouchDebug = true

    // Paint 객체들
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    /** LOCKED 상태: 실선 녹색 ("내가 잡고 있다" 느낌) */
    private val lockedBoxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    // 좌표 텍스트용 (크게, 화면 하단 고정 영역에 표시)
    private val coordTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    private val coordBgPaint = Paint().apply {
        color = Color.argb(220, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val handPointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val handLinePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 3f
    }

    /** near-contact 판정용 엄지·검지 중간점 (디버그용 작은 점) */
    private val touchMidpointPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    data class DetectionBox(
        val label: String,
        val confidence: Float,
        val rect: RectF,
        val topLabels: List<Pair<String, Int>> = emptyList(),  // (클래스명, 퍼센트) 상위 N개
        val rotationDegrees: Float = 0f  // 롤 각도(도): 휴대폰 옆으로 눕힌 만큼 박스도 회전
    )

    companion object {
        private const val TAG_OVERLAY = "OverlayView"
    }

    // YOLOX 결과 설정 (이미지 크기 전달 시 박스를 뷰 좌표로 스케일링)
    fun setDetections(boxes: List<DetectionBox>, srcImageWidth: Int = 0, srcImageHeight: Int = 0) {
        detectionBoxes = boxes
        imageWidth = srcImageWidth
        imageHeight = srcImageHeight
        invalidate()
    }

    // MediaPipe Hands 결과 설정
    fun setHands(results: HandLandmarkerResult?) {
        handLandmarks = results?.landmarks() ?: emptyList()
        invalidate()
    }

    /** 고정 모드 (State B): 박스가 고정되어 추적하지 않음 */
    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
        invalidate()
    }

    /** 디버그: 엄지·검지 중간점(이미지 픽셀) 설정. null이면 표시 안 함 */
    fun setTouchDebugPoint(px: Float?, py: Float?) {
        touchDebugPx = if (px != null && py != null) Pair(px, py) else null
        invalidate()
    }

    /** 디버그: 터치 중간점 그리기 토글 */
    fun setShowTouchDebug(show: Boolean) {
        showTouchDebug = show
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 이미지 → 뷰 좌표 스케일 (박스가 화면에 맞게 보이도록)
        val scaleX = if (imageWidth > 0) width.toFloat() / imageWidth else 1f
        val scaleY = if (imageHeight > 0) height.toFloat() / imageHeight else 1f

        // 1. YOLOX 박스만 그리기 (객체 이름 표시 없음)
        val paint = if (isFrozen) lockedBoxPaint else boxPaint
        detectionBoxes.forEachIndexed { index, box ->
            val r = box.rect
            val left = r.left * scaleX
            val top = r.top * scaleY
            val right = r.right * scaleX
            val bottom = r.bottom * scaleY
            val rot = box.rotationDegrees

            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            canvas.save()
            if (kotlin.math.abs(rot) > 0.5f) canvas.rotate(rot, cx, cy)
            canvas.drawRect(left, top, right, bottom, paint)
            canvas.restore()
        }

        // MediaPipe Hands 그리기 (정규화 좌표 → 뷰 좌표, 세로 화면에 맞게 보정)
        handLandmarks.forEach { hand ->
            hand.forEach { landmark ->
                val (px, py) = handLandmarkToView(landmark.x(), landmark.y())
                canvas.drawCircle(px, py, 8f, handPointPaint)
            }
            drawHandConnections(canvas, hand)
        }

        // 디버그: near-contact 엄지·검지 중간점 (이미지 픽셀 → 뷰 좌표)
        if (showTouchDebug && touchDebugPx != null && imageWidth > 0 && imageHeight > 0) {
            val (ix, iy) = touchDebugPx!!
            val vx = ix * scaleX
            val vy = iy * scaleY
            canvas.drawCircle(vx, vy, 12f, touchMidpointPaint)
        }
    }

    /** 손 랜드마크 (정규화 0~1) → 뷰 좌표. 회전 없이 12시(세로) 정렬 */
    private fun handLandmarkToView(nx: Float, ny: Float): Pair<Float, Float> {
        val px = nx * width
        val py = ny * height
        return Pair(px, py)
    }

    private fun drawHandConnections(canvas: Canvas, hand: List<NormalizedLandmark>) {
        val connections = listOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
            Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
            Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
            Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20)
        )

        connections.forEach { (start, end) ->
            val (sx, sy) = handLandmarkToView(hand[start].x(), hand[start].y())
            val (ex, ey) = handLandmarkToView(hand[end].x(), hand[end].y())
            canvas.drawLine(sx, sy, ex, ey, handLinePaint)
        }
    }

    fun clear() {
        detectionBoxes = emptyList()
        handLandmarks = emptyList()
        invalidate()
    }
}