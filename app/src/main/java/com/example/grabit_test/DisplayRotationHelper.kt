package com.example.grabitTest

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.Session

/**
 * ARCore 화면 회전·뷰포트 크기 관리 헬퍼 (Google ARCore 샘플 표준 준수).
 * display.rotation 감지 및 session.setDisplayGeometry() 호출 지원.
 */
class DisplayRotationHelper(context: Context) : DisplayManager.DisplayListener {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val display: Display =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

    @Volatile
    private var viewportChanged = false

    @Volatile
    private var viewportWidth = 0

    @Volatile
    private var viewportHeight = 0

    /** Display 리스너 등록. Activity.onResume()에서 호출 권장 */
    fun onResume() {
        displayManager.registerDisplayListener(this, null)
    }

    /** Display 리스너 해제. Activity.onPause()에서 호출 권장 */
    fun onPause() {
        displayManager.unregisterDisplayListener(this)
    }

    /**
     * Surface 크기 변경 시 호출. SurfaceHolder.Callback.surfaceChanged 등에서 호출.
     * 이후 updateSessionIfNeeded()에서 session.setDisplayGeometry()가 호출됨.
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    /**
     * Surface/Display 변경이 있으면 Session의 display geometry를 갱신.
     * setDisplayGeometry(rotation, width, height) 호출. 매 프레임 session.update() 전에 호출 권장.
     */
    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged && viewportWidth > 0 && viewportHeight > 0) {
            val rotation = display.rotation
            session.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    /** 현재 display rotation (Surface.ROTATION_*). setDisplayGeometry에 전달되는 값과 동일. */
    fun getRotation(): Int = display.rotation

    /** 회전을 도(degree)로 반환. 0, 90, 180, 270 */
    fun getRotationDegrees(): Int = when (display.rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}
    override fun onDisplayChanged(displayId: Int) {
        viewportChanged = true
    }
}
