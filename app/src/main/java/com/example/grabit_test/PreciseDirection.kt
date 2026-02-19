package com.example.grabitTest

import kotlin.math.PI
import kotlin.math.atan2

/**
 * 시각장애인 가이드: 화면 중심 대비 객체 위치를 시계 방향(1~12)과 각도(우측/좌측 n도, 위/아래 n도)로 변환.
 * 수치 계산은 소수점 둘째 자리까지 정밀.
 */
object PreciseDirection {

    /** centerXNorm, centerYNorm: 0~1 (화면 기준, 0.5가 중앙). 반환: (시침 1~12, 수평도, 수직도). */
    fun getPreciseDirection(centerXNorm: Float, centerYNorm: Float): PreciseDirectionResult {
        val dx = (centerXNorm - 0.5f).toDouble()
        val dy = (0.5 - centerYNorm).toDouble()  // 위쪽이 양
        val angleRad = atan2(dx, dy)
        var hour = 12.0 - (angleRad * 6.0 / PI)
        if (hour <= 0) hour += 12.0
        if (hour > 12) hour -= 12.0
        val clockHour = hour.toInt().coerceIn(1, 12)

        val horizontalDeg = ((centerXNorm - 0.5f) * 60f).round2()
        val verticalDeg = ((0.5f - centerYNorm) * 60f).round2()

        return PreciseDirectionResult(clockHour, horizontalDeg, verticalDeg)
    }

    /** "N시 방향, 우측 n도 지점에 물체가 있습니다" 형식 (시계 + 각도). */
    fun formatImmediateGuidance(clockHour: Int, horizontalDeg: Float, verticalDeg: Float): String {
        val hStr = when {
            horizontalDeg > 0f -> "우측 ${horizontalDeg.toInt()}도"
            horizontalDeg < 0f -> "좌측 ${(-horizontalDeg).toInt()}도"
            else -> "정면"
        }
        val vStr = when {
            verticalDeg > 0f -> "위로 ${verticalDeg.toInt()}도"
            verticalDeg < 0f -> "아래로 ${(-verticalDeg).toInt()}도"
            else -> ""
        }
        val part = if (vStr.isEmpty()) hStr else "$hStr, $vStr"
        return "${clockHour}시 방향, ${part} 지점에 물체가 있습니다."
    }

    /** "N시 방향으로 손을 뻗으세요" (근거리 모드용). */
    fun formatProximityReach(clockHour: Int): String =
        "팔을 뻗으면 닿을 거리입니다. ${clockHour}시 방향으로 손을 뻗으세요."

    private fun Float.round2(): Float = (this * 100f).toInt() / 100f
}

data class PreciseDirectionResult(
    val clockHour: Int,
    val horizontalDeg: Float,
    val verticalDeg: Float
)
