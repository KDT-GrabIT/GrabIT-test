package com.example.grabitTest

import android.content.Context
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

private const val NS2S = 1.0f / 1_000_000_000f
private const val MIN_ROTATION_THRESHOLD = 0.02f
private const val SENSITIVITY_FACTOR = 0.8f
private const val SMOOTHING_ALPHA = 0.15f

/** 가속도 데드존: 0.1 미만은 노이즈로 무시 */
private const val ACCEL_DEADZONE = 0.1f
/** 속도 감쇠(마찰력): 매 프레임 velocity *= VELOCITY_DAMPING */
private const val VELOCITY_DAMPING = 0.8f
/** 물체까지 거리 가정 (팔 뻗은 거리, m) */
private const val ASSUMED_DISTANCE_M = 0.5f
/** 화면 밖 허용 여유(px): 이 정도까지 벗어나도 바로 해제하지 않음 */
private const val OUT_OF_BOUNDS_MARGIN = 150f
/** 연속 N프레임 화면 밖이어야 고정 해제 (노이즈로 인한 급격한 해제 방지) */
private const val OUT_OF_BOUNDS_FRAMES_TO_LOSE = 15

class GyroTrackingManager(
    private val context: Context,
    private val onBoxUpdate: (RectF) -> Unit,
    private val onTrackingLost: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private var initialRotationMatrix = FloatArray(9)
    private var currentRotationMatrix = FloatArray(9)
    private var initialRect = RectF()
    private var currentSmoothedRect = RectF()

    var isLocked = false
    private var timestamp: Long = 0
    private var outOfBoundsCount = 0

    // 화면 크기 및 FOV
    private var screenWidth = 1080f
    private var screenHeight = 2340f
    private var horizontalFov = Math.toRadians(79.0).toFloat()
    private var verticalFov = Math.toRadians(60.0).toFloat()
    private var pixelsPerRadianX = 0f
    private var pixelsPerRadianY = 0f

    // 가속도 적분용 (Sensor Fusion)
    private var velocityX = 0f
    private var velocityY = 0f
    private var distanceX = 0f  // m
    private var distanceY = 0f  // m
    private var lastTimestampAccel = 0L

    fun startTracking(rect: RectF, width: Int, height: Int) {
        initialRect.set(rect)
        currentSmoothedRect.set(rect)

        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        pixelsPerRadianX = screenWidth / horizontalFov
        pixelsPerRadianY = screenHeight / verticalFov

        velocityX = 0f
        velocityY = 0f
        distanceX = 0f
        distanceY = 0f
        lastTimestampAccel = 0L

        isLocked = true
        timestamp = 0
        outOfBoundsCount = 0

        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopTracking() {
        isLocked = false
        sensorManager.unregisterListener(this)
    }

    fun resetToSearchingFromUI() {
        stopTracking()
        onTrackingLost()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isLocked || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> processLinearAcceleration(event)
            Sensor.TYPE_ROTATION_VECTOR -> processRotationVector(event)
        }
    }

    private fun processLinearAcceleration(event: SensorEvent) {
        var ax = event.values.getOrNull(0) ?: 0f
        var ay = event.values.getOrNull(1) ?: 0f

        if (abs(ax) < ACCEL_DEADZONE) ax = 0f
        if (abs(ay) < ACCEL_DEADZONE) ay = 0f

        val now = event.timestamp
        if (lastTimestampAccel == 0L) {
            lastTimestampAccel = now
            return
        }
        val dt = (now - lastTimestampAccel) * NS2S
        lastTimestampAccel = now
        if (dt <= 0f || dt > 1f) return

        velocityX += ax * dt
        velocityY += ay * dt

        velocityX *= VELOCITY_DAMPING
        velocityY *= VELOCITY_DAMPING

        distanceX += velocityX * dt
        distanceY += velocityY * dt
    }

    private fun ensureRotationVector4(values: FloatArray): FloatArray {
        if (values.size >= 4) return values
        val x = values.getOrNull(0) ?: 0f
        val y = values.getOrNull(1) ?: 0f
        val z = values.getOrNull(2) ?: 0f
        val w = kotlin.math.sqrt((1f - x * x - y * y - z * z).coerceAtLeast(0f))
        return floatArrayOf(x, y, z, w)
    }

    private fun processRotationVector(event: SensorEvent) {
        val rv = ensureRotationVector4(event.values)
        SensorManager.getRotationMatrixFromVector(currentRotationMatrix, rv)

        val adjustedRotationMatrix = FloatArray(9)
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 ->
                System.arraycopy(currentRotationMatrix, 0, adjustedRotationMatrix, 0, 9)
            Surface.ROTATION_90 ->
                SensorManager.remapCoordinateSystem(currentRotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, adjustedRotationMatrix)
            Surface.ROTATION_270 ->
                SensorManager.remapCoordinateSystem(currentRotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, adjustedRotationMatrix)
            else ->
                System.arraycopy(currentRotationMatrix, 0, adjustedRotationMatrix, 0, 9)
        }

        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjustedRotationMatrix, orientation)

        if (timestamp == 0L) {
            initialRotationMatrix = adjustedRotationMatrix.clone()
            timestamp = event.timestamp
            return
        }

        val initialOrientation = FloatArray(3)
        SensorManager.getOrientation(initialRotationMatrix, initialOrientation)

        var deltaYaw = orientation[0] - initialOrientation[0]
        var deltaPitch = orientation[1] - initialOrientation[1]

        if (deltaYaw > Math.PI) deltaYaw -= (2 * Math.PI).toFloat()
        if (deltaYaw < -Math.PI) deltaYaw += (2 * Math.PI).toFloat()
        if (deltaPitch > Math.PI) deltaPitch -= (2 * Math.PI).toFloat()
        if (deltaPitch < -Math.PI) deltaPitch += (2 * Math.PI).toFloat()

        if (abs(deltaYaw) < MIN_ROTATION_THRESHOLD) deltaYaw = 0f
        if (abs(deltaPitch) < MIN_ROTATION_THRESHOLD) deltaPitch = 0f

        val rotationShiftX = deltaYaw * pixelsPerRadianX * SENSITIVITY_FACTOR
        val rotationShiftY = deltaPitch * pixelsPerRadianY * SENSITIVITY_FACTOR

        val pixelsPerMeterX = pixelsPerRadianX / ASSUMED_DISTANCE_M
        val pixelsPerMeterY = pixelsPerRadianY / ASSUMED_DISTANCE_M
        val translationShiftX = distanceX * pixelsPerMeterX
        val translationShiftY = distanceY * pixelsPerMeterY

        val targetX = initialRect.left - rotationShiftX - translationShiftX
        val targetY = initialRect.top - rotationShiftY - translationShiftY

        val newLeft = currentSmoothedRect.left + (targetX - currentSmoothedRect.left) * SMOOTHING_ALPHA
        val newTop = currentSmoothedRect.top + (targetY - currentSmoothedRect.top) * SMOOTHING_ALPHA

        currentSmoothedRect.set(newLeft, newTop, newLeft + initialRect.width(), newTop + initialRect.height())

        val margin = OUT_OF_BOUNDS_MARGIN
        val isOutOfBounds = currentSmoothedRect.right < -margin || currentSmoothedRect.left > screenWidth + margin ||
            currentSmoothedRect.bottom < -margin || currentSmoothedRect.top > screenHeight + margin

        if (isOutOfBounds) {
            outOfBoundsCount++
            if (outOfBoundsCount >= OUT_OF_BOUNDS_FRAMES_TO_LOSE) {
                stopTracking()
                onTrackingLost()
            } else {
                onBoxUpdate(currentSmoothedRect)
            }
        } else {
            outOfBoundsCount = 0
            onBoxUpdate(currentSmoothedRect)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
