package com.example.grabitTest

import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.PriorityQueue

/**
 * TTS 메시지가 겹치지 않도록 우선순위 큐로 순차 재생.
 * 숫자가 작을수록 높은 우선순위 (0 = 안전/긴급, 1 = 일반, 2 = 저우선순위).
 */
class TtsPriorityQueue(
    private val tts: TTSManager,
    private val onSpeak: (String, (() -> Unit)?) -> Unit = { text, onDone ->
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, onDone)
    }
) {
    companion object {
        private const val TAG = "TtsPriorityQueue"
        const val PRIORITY_URGENT = 0
        const val PRIORITY_NORMAL = 1
        const val PRIORITY_LOW = 2
    }

    private data class Item(val priority: Int, val text: String, val ts: Long = System.currentTimeMillis()) :
        Comparable<Item> {
        override fun compareTo(other: Item): Int = compareBy<Item> { it.priority }.thenBy { it.ts }.compare(this, other)
    }

    private val queue = PriorityQueue<Item>()
    private var isPlaying = false

    fun enqueue(text: String, priority: Int = PRIORITY_NORMAL) {
        if (text.isBlank()) return
        synchronized(queue) {
            queue.add(Item(priority, text))
            Log.d(TAG, "TTS 큐 enqueue size=${queue.size} priority=$priority")
            drainLocked()
        }
    }

    private fun drainLocked() {
        if (isPlaying || queue.isEmpty()) return
        val item = queue.poll() ?: return
        isPlaying = true
        Log.d(TAG, "TTS 큐 재생 시작: ${item.text.take(40)}...")
        onSpeak(item.text) {
            isPlaying = false
            Log.d(TAG, "TTS 큐 재생 종료, 남은 개수=${queue.size}")
            synchronized(queue) {
                if (queue.isNotEmpty()) drainLocked()
            }
        }
    }

    fun clear() {
        synchronized(queue) { queue.clear() }
    }

    fun isBusy(): Boolean = synchronized(queue) { isPlaying || queue.isNotEmpty() }
}
