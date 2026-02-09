package com.example.grabitTest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Android SpeechRecognizer 기반 STT(음성→텍스트) 매니저
 */
class STTManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "STTManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    fun init(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer 사용 불가")
            onError("이 기기에서는 음성 인식을 지원하지 않습니다.")
            return false
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
        Log.d(TAG, "STTManager 초기화 완료")
        return true
    }

    fun startListening() {
        val sr = speechRecognizer
        if (sr == null) {
            onError("SpeechRecognizer가 초기화되지 않았습니다.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toString())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.KOREAN.toString())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, Locale.KOREAN.toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            sr.startListening(intent)
            isListening = true
            onListeningChanged(true)
            Log.d(TAG, "STT 듣기 시작")
        } catch (e: Exception) {
            Log.e(TAG, "startListening 실패", e)
            onError("음성 인식 시작 실패: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            isListening = false
            onListeningChanged(false)
            Log.d(TAG, "STT 듣기 중지")
        } catch (e: Exception) {
            Log.e(TAG, "stopListening 실패", e)
        }
    }

    fun isListening(): Boolean = isListening

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            onListeningChanged(false)
            Log.d(TAG, "STTManager 해제")
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            isListening = false
            onListeningChanged(false)
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "오디오 에러"
                SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한 부족"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
                SpeechRecognizer.ERROR_NO_MATCH -> "인식 결과 없음"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식기 사용 중"
                SpeechRecognizer.ERROR_SERVER -> "서버 에러"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말 없음 타임아웃"
                else -> "알 수 없는 에러 (코드: $error)"
            }
            Log.e(TAG, "STT onError: $msg")
            isListening = false
            onListeningChanged(false)
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_CLIENT) {
                onError(msg)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrBlank()) {
                Log.d(TAG, "STT 결과: $text")
                onResult(text)
            } else {
                Log.d(TAG, "STT 결과 없음")
                onError("인식된 말이 없습니다.")
            }
            isListening = false
            onListeningChanged(false)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrBlank()) {
                Log.d(TAG, "STT 부분 결과: $text")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
