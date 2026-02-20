package com.example.grabitTest

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Android SpeechRecognizer 기반 STT(음성→텍스트) 매니저
 * beepPlayer를 주입하면 startListening() 시 삐 소리만 재생 후 음성녹음 시작.
 */
class STTManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onErrorWithCode: ((String, Int) -> Unit)? = null,
    private val onListeningChanged: (Boolean) -> Unit = {},
    private val onPartialResult: ((String) -> Unit)? = null,
    private val beepPlayer: BeepPlayer? = null,
    /** 음성 인식이 끝난 원인(디버깅용). onEndOfSpeech/onError/onResults 시 로그 + 이 콜백으로 전달 */
    private val onListeningEndedReason: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "STT"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var audioManager: AudioManager? = null
    private var lastPartialText: String? = null

    fun init(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "STT 초기화 실패: SpeechRecognizer 사용 불가")
            onError("이 기기에서는 음성 인식을 지원하지 않습니다.")
            return false
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return true
    }
    
    /**
     * 음성 인식 시작. beepPlayer가 있으면 삐 소리만 재생 후 음성녹음 시작.
     */
    fun startListening() {
        // 비프 재생 전부터 "듣는 중" 표시 (엔진이 실제로 켜지기 전에도 사용자 피드백)
        onListeningChanged(true)
        val sr = speechRecognizer
        if (sr == null) {
            Log.e(TAG, "STT 시작 실패: speechRecognizer=null")
            onListeningChanged(false)
            onError("SpeechRecognizer가 초기화되지 않았습니다.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "STT 시작 실패: RECORD_AUDIO 권한 없음")
            onListeningChanged(false)
            onError("마이크 권한이 필요합니다.")
            return
        }
        if (beepPlayer != null) {
            beepPlayer.playBeep { doStartListening() }
        } else {
            doStartListening()
        }
    }

    private fun doStartListening() {
        val sr = speechRecognizer ?: run {
            onListeningChanged(false)
            return
        }
        try {
            if (isListening) sr.stopListening()
        } catch (_: Exception) {}
        isListening = false
        audioManager?.requestAudioFocus(
            { },
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
        }
        try {
            sr.startListening(intent)
            isListening = true
            onListeningChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "STT 시작 예외", e)
            isListening = false
            onListeningChanged(false)
            onError("음성 인식 시작 실패: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            isListening = false
            onListeningChanged(false)
        } catch (e: Exception) {
            Log.e(TAG, "stopListening 실패", e)
        }
    }

    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
            isListening = false
            onListeningChanged(false)
        } catch (e: Exception) {
            Log.e(TAG, "cancelListening 실패", e)
        }
    }

    fun isListening(): Boolean = isListening

    /** NO_MATCH/타임아웃 전 마지막 부분 인식 텍스트 (사용자에게 뭐가 들렸는지 보여주기용) */
    fun getLastPartialText(): String? = lastPartialText

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            onListeningChanged(false)
            audioManager?.abandonAudioFocus(null)
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            onListeningEndedReason("onEndOfSpeech")
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
            onListeningEndedReason("onError($error)")
            isListening = false
            onListeningChanged(false)
            onErrorWithCode?.invoke(msg, error)
            if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                error != SpeechRecognizer.ERROR_CLIENT &&
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                onError(msg)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            val finalText = when {
                !text.isNullOrBlank() -> text
                !lastPartialText.isNullOrBlank() -> lastPartialText
                else -> null
            }
            if (!finalText.isNullOrBlank()) {
                onResult(finalText)
                lastPartialText = null
            } else {
                onError("인식된 말이 없습니다.")
            }
            onListeningEndedReason(if (!finalText.isNullOrBlank()) "onResults" else "onResults(빈 결과)")
            isListening = false
            onListeningChanged(false)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            if (text.isNotBlank()) lastPartialText = text
            onPartialResult?.invoke(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
