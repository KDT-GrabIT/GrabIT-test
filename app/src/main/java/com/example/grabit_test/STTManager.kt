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
 * beepPlayer와 speakPrompt를 주입하면 startListening() 시: "삐 소리가 나면 말씀해주세요" (TTS) → 삐 → 음성녹음 시작.
 */
class STTManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onErrorWithCode: ((String, Int) -> Unit)? = null,
    private val onListeningChanged: (Boolean) -> Unit = {},
    private val onPartialResult: ((String) -> Unit)? = null,
    private val beepPlayer: BeepPlayer? = null,
    private val speakPrompt: ((String, () -> Unit) -> Unit)? = null
) {
    companion object {
        private const val TAG = "STT"
        private const val DEFAULT_PROMPT = "삐 소리가 나면 말씀해주세요."
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var audioManager: AudioManager? = null
    private var lastPartialText: String? = null

    fun init(): Boolean {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        Log.d(TAG, "STT_INIT isRecognitionAvailable=$available")
        if (!available) {
            Log.e(TAG, "STT_INIT 실패: SpeechRecognizer 사용 불가")
            onError("이 기기에서는 음성 인식을 지원하지 않습니다.")
            return false
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
        Log.d(TAG, "STT_INIT speechRecognizerCreated=${speechRecognizer != null}")

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        Log.d(TAG, "STT_INIT audioManagerCreated=${audioManager != null}")
        Log.d(TAG, "STT_INIT 완료")
        return true
    }
    
    /**
     * 음성 인식 시작. beepPlayer/speakPrompt가 있으면: "삐 소리가 나면 말씀해주세요" (TTS) → 삐 → 음성녹음 시작.
     */
    fun startListening() {
        val sr = speechRecognizer
        if (sr == null) {
            Log.e(TAG, "STT_START 실패: speechRecognizer=null")
            onError("SpeechRecognizer가 초기화되지 않았습니다.")
            return
        }
        val permissionState =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "STT_START 실패: RECORD_AUDIO 권한 없음")
            onError("마이크 권한이 필요합니다.")
            return
        }
        if (beepPlayer != null && speakPrompt != null) {
            Log.d(TAG, "STT_START → prompt → beep → doStartListening")
            speakPrompt?.invoke(DEFAULT_PROMPT) {
                beepPlayer?.playBeep {
                    doStartListening()
                }
            }
        } else {
            doStartListening()
        }
    }

    private fun doStartListening() {
        val sr = speechRecognizer ?: return
        try {
            if (isListening) sr.stopListening()
        } catch (_: Exception) {}
        isListening = false
        onListeningChanged(false)
        audioManager?.let { am ->
            am.requestAudioFocus(
                { },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
        }
        try {
            sr.startListening(intent)
            isListening = true
            onListeningChanged(true)
            Log.d(TAG, "STT_START_OK ts=${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "STT_START 예외", e)
            onError("음성 인식 시작 실패: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            isListening = false
            onListeningChanged(false)
            val ts = System.currentTimeMillis()
            Log.d(TAG, "STT_STOP ts=$ts isListeningAfter=$isListening")
        } catch (e: Exception) {
            Log.e(TAG, "stopListening 실패", e)
        }
    }

    /** 필요 시 외부에서 명시적으로 취소 호출용 (로그/포커스 정리 포함) */
    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
            isListening = false
            onListeningChanged(false)
            val ts = System.currentTimeMillis()
            Log.d(TAG, "STT_CANCEL ts=$ts isListeningAfter=$isListening")
        } catch (e: Exception) {
            Log.e(TAG, "cancelListening 실패", e)
        }
    }

    fun isListening(): Boolean = isListening

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            onListeningChanged(false)
            audioManager?.abandonAudioFocus(null)
            Log.d(TAG, "STT_RELEASE")
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
    }

    private fun errorCodeToString(error: Int): String =
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            else -> "ERROR_UNKNOWN($error)"
        }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "STT_CB onReadyForSpeech ts=${System.currentTimeMillis()}")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "STT_CB onBeginningOfSpeech ts=${System.currentTimeMillis()}")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 로그 과다 방지: 레벨은 필요 시에만 확인
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "STT_CB onEndOfSpeech ts=${System.currentTimeMillis()}")
            isListening = false
            onListeningChanged(false)
        }

        override fun onError(error: Int) {
            val codeName = errorCodeToString(error)
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
            Log.e(TAG, "STT_CB onError code=$error($codeName), msg=$msg ts=${System.currentTimeMillis()}")
            isListening = false
            onListeningChanged(false)
            onErrorWithCode?.invoke(msg, error)
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_CLIENT) {
                onError(msg)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.joinToString(
                prefix = "[",
                postfix = "]"
            ) { String.format("%.2f", it) }
            val text = matches?.firstOrNull()?.trim()
            val finalText = when {
                !text.isNullOrBlank() -> text
                !lastPartialText.isNullOrBlank() -> lastPartialText
                else -> null
            }

            Log.d(
                TAG,
                "STT_CB onResults rawList=$matches confidences=$confidences text='$text' lastPartialText='$lastPartialText' finalText='$finalText' ts=${System.currentTimeMillis()}"
            )

            if (!finalText.isNullOrBlank()) {
                onResult(finalText)
                // 최종 결과를 정상 처리한 뒤에만 partial 캐시를 초기화
                lastPartialText = null
            } else {
                Log.d(TAG, "STT_CB onResults NO_MATCH (finalText empty) ts=${System.currentTimeMillis()}")
                onError("인식된 말이 없습니다.")
            }
            isListening = false
            onListeningChanged(false)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrBlank()) {
                lastPartialText = text
                onPartialResult?.invoke(text)
            }
            Log.d(
                TAG,
                "STT_CB onPartialResults rawList=$matches lastPartialText='$lastPartialText' ts=${System.currentTimeMillis()}"
            )
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
