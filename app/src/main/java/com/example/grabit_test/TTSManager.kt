package com.example.grabitTest

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Android TextToSpeech 기반 TTS(텍스트→음성) 매니저
 */
class TTSManager(
    private val context: Context,
    private val onReady: () -> Unit = {},
    private val onSpeakDone: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "TTSManager"
        private const val UTTERANCE_ID = "TTSManager_utterance"
        /** onDone 미호출 시 폴백 (일부 기기에서 TTS 콜백 누락 방지) */
        private const val SPEAK_DONE_TIMEOUT_MS = 8000L
    }

    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeakDoneCallback: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var speakDoneTimeoutRunnable: Runnable? = null

    fun init(callback: (Boolean) -> Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.KOREAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "한국어 TTS 미지원, 기본 언어 사용")
                }
                textToSpeech?.setSpeechRate(0.82f)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        cancelSpeakDoneTimeout()
                        pendingSpeakDoneCallback?.invoke()
                        pendingSpeakDoneCallback = null
                        onSpeakDone()
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS onError: utteranceId=$utteranceId")
                        onError("TTS 재생 오류")
                    }
                })
                isReady = true
                onReady()
                callback(true)
            } else {
                Log.e(TAG, "TTS 초기화 실패")
                callback(false)
                onError("TTS 초기화 실패")
            }
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, onDone: (() -> Unit)? = null) {
        if (!isReady || text.isBlank()) {
            Log.w(TAG, "speak: 준비되지 않았거나 텍스트가 비어있음")
            onDone?.invoke()
            return
        }

        cancelSpeakDoneTimeout()
        pendingSpeakDoneCallback = onDone

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        }

        @Suppress("DEPRECATION")
        val result = textToSpeech?.speak(text.trim(), queueMode, params, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "speak 실패")
            cancelSpeakDoneTimeout()
            pendingSpeakDoneCallback = null
            onError("음성 출력 실패")
        } else {
            speakDoneTimeoutRunnable = Runnable {
                Log.w(TAG, "TTS onDone 타임아웃 (폴백)")
                speakDoneTimeoutRunnable = null
                pendingSpeakDoneCallback?.invoke()
                pendingSpeakDoneCallback = null
                onSpeakDone()
            }
            handler.postDelayed(speakDoneTimeoutRunnable!!, SPEAK_DONE_TIMEOUT_MS)
        }
    }

    private fun cancelSpeakDoneTimeout() {
        speakDoneTimeoutRunnable?.let { handler.removeCallbacks(it) }
        speakDoneTimeoutRunnable = null
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun isSpeaking(): Boolean = textToSpeech?.isSpeaking == true

    fun release() {
        try {
            cancelSpeakDoneTimeout()
            pendingSpeakDoneCallback = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isReady = false
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
    }
}
