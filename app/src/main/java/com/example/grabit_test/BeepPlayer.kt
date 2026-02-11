package com.example.grabitTest

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import android.os.Handler
import android.os.Looper
import kotlin.math.sin
import kotlin.math.PI

/**
 * 삐 소리 재생 (시각장애인 음성 안내 시그널용)
 * 1초 동안 이어지는 "삐~~~~" 한 번 재생 (AudioTrack 사인파 사용)
 */
class BeepPlayer {
    companion object {
        private const val TAG = "BeepPlayer"
        /** 삐 소리 길이: 1초 (삐~~~~) */
        private const val BEEP_DURATION_MS = 1000
        private const val SAMPLE_RATE = 44100
        private const val BEEP_FREQ_HZ = 880
        /** 볼륨 0~1 (원래 ToneGenerator 80 수준에 맞춤) */
        private const val AMPLITUDE = 0.35
    }

    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    fun init(): Boolean {
        return try {
            val numSamples = (SAMPLE_RATE * BEEP_DURATION_MS / 1000.0).toInt()
            val buffer = ShortArray(numSamples)
            val angularFreq = 2.0 * PI * BEEP_FREQ_HZ / SAMPLE_RATE
            for (i in 0 until numSamples) {
                buffer[i] = (AMPLITUDE * sin(angularFreq * i) * Short.MAX_VALUE).toInt().toShort()
            }
            val bufferSizeBytes = buffer.size * 2
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            audioTrack?.write(buffer, 0, buffer.size)
            true
        } catch (e: Exception) {
            Log.e(TAG, "BeepPlayer 초기화 실패", e)
            false
        }
    }

    /**
     * 삐 소리 한 번 길게 재생 후 onDone 콜백 호출
     */
    fun playBeep(onDone: () -> Unit = {}) {
        val at = audioTrack
        if (at == null) {
            Log.w(TAG, "AudioTrack 없음, 콜백 즉시 호출")
            onDone()
            return
        }
        try {
            at.stop()
            at.reloadStaticData()
            at.play()
            handler.postDelayed({
                try {
                    at.stop()
                } catch (_: Exception) {}
                onDone()
            }, BEEP_DURATION_MS.toLong() + 50)
        } catch (e: Exception) {
            Log.e(TAG, "Beep 재생 실패", e)
            onDone()
        }
    }

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            handler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "release 실패", e)
        }
    }
}
