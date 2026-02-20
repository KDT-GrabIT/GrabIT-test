package com.example.grabitTest

import android.speech.tts.TextToSpeech
import java.util.PriorityQueue

/**
 * TTS 메시지가 겹치지 않도록 우선순위 큐로 순차 재생.
 * 숫자가 작을수록 높은 우선순위 (0 = 안전/긴급, 1 = 일반, 2 = 저우선순위).
 */
class TtsPriorityQueue(
    private val tts: TTSManager,
    /** 큐에서 꺼낸 메시지는 QUEUE_ADD로 재생해 기존 재생을 끊지 않음(삐리삐리 방지). */
    private val onSpeak: (String, (() -> Unit)?) -> Unit = { text, onDone ->
        tts.speak(text, TextToSpeech.QUEUE_ADD, onDone)
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
    /** 방금 재생한 문구와 큐에 이미 있는 문구는 중복 등록하지 않아 삐리삐리(레이더 소리) 방지 */
    private var lastSpokenText: String? = null

    fun enqueue(text: String, priority: Int = PRIORITY_NORMAL) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        synchronized(queue) {
            if (queue.any { it.text == trimmed } || trimmed == lastSpokenText) {
                return
            }
            queue.add(Item(priority, trimmed))
            drainLocked()
        }
    }

    private fun drainLocked() {
        if (isPlaying || queue.isEmpty()) return
        val item = queue.poll() ?: return
        isPlaying = true
        lastSpokenText = item.text
        onSpeak(item.text) {
            isPlaying = false
            synchronized(queue) {
                if (queue.isNotEmpty()) drainLocked()
            }
        }
    }

    fun clear() {
        synchronized(queue) {
            queue.clear()
            lastSpokenText = null
        }
    }

    fun isBusy(): Boolean = synchronized(queue) { isPlaying || queue.isNotEmpty() }
}
