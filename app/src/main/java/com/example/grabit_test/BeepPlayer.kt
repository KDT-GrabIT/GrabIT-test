package com.example.grabitTest

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.os.Handler
import android.os.Looper

/**
 * 삐 소리 재생 (시각장애인 음성 안내 시그널용)
 */
class BeepPlayer {
    companion object {
        private const val TAG = "BeepPlayer"
        private const val BEEP_DURATION_MS = 150
        private const val VOLUME = 80
    }

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())

    fun init(): Boolean {
        return try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            Log.d(TAG, "BeepPlayer 초기화 완료")
            true
        } catch (e: Exception) {
            Log.e(TAG, "BeepPlayer 초기화 실패", e)
            false
        }
    }

    /**
     * 삐 소리 재생 후 onDone 콜백 호출
     */
    fun playBeep(onDone: () -> Unit = {}) {
        val tg = toneGenerator
        if (tg == null) {
            Log.w(TAG, "ToneGenerator 없음, 콜백 즉시 호출")
            onDone()
            return
        }
        try {
            tg.startTone(ToneGenerator.TONE_CDMA_PIP, BEEP_DURATION_MS)
            handler.postDelayed({
                onDone()
            }, BEEP_DURATION_MS.toLong() + 50)
        } catch (e: Exception) {
            Log.e(TAG, "Beep 재생 실패", e)
            onDone()
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            handler.removeCallbacksAndMessages(null)
            Log.d(TAG, "BeepPlayer 해제")
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
    }
}
