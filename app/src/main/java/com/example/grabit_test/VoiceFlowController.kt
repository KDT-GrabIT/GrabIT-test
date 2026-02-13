package com.example.grabitTest

import android.graphics.RectF
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.grabitTest.data.synonym.SynonymRepository

/**
 * 시각장애인을 위한 음성 인터랙션 상태 머신
 * 흐름: 안내 → 삐 → 상품명 말하기 → 확인 질문 → 삐 → 음성 확인 → 탐색 → 결과 안내
 */
class VoiceFlowController(
    private val ttsManager: TTSManager,
    private val onStateChanged: (VoiceFlowState, String) -> Unit,
    private val onSystemAnnounce: (String) -> Unit = {},
    private val onRequestStartStt: () -> Unit,
    private val onStartSearch: (productName: String) -> Unit,
    private val onProductNameEntered: (productName: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "VoiceFlowController"

        // 안내 멘트
        const val MSG_APP_START =
            "쇼핑 보조가 시작되었습니다. 도움말이 필요하면 '도움말'이라고 말씀해주세요."

        const val MSG_ASK_PRODUCT = "찾으시는 상품을 말씀해주세요."

        const val MSG_HELP =
            "이 앱은 음성으로 상품을 찾아드립니다. 안내에 따라 상품 이름을 말씀하시고, 확인 질문에 예 또는 아니요로 답하시면 됩니다. " +
            "'다시'라고 말씀하시면 마지막 안내를 다시 들으실 수 있습니다."

        fun msgConfirmProduct(productName: String) =
            "찾으시는 상품이 ${productName} 맞습니까? 맞으면 '예'라고 말해주세요."

        fun msgSearching(productName: String) = "${productName}을 찾고 있습니다. 잠시만 기다려주세요."

        const val MSG_CAMERA_ON = "카메라가 켜졌습니다."

        fun msgSearchResult(productName: String) = "${productName}를 찾았습니다."

        const val MSG_SEARCH_FAILED =
            "상품을 찾지 못했습니다. 다시 찾으시겠습니까?"
    }

    enum class VoiceFlowState {
        APP_START,
        WAITING_PRODUCT_NAME,
        CONFIRM_PRODUCT,
        WAITING_CONFIRMATION,
        SEARCHING_PRODUCT,
        SEARCH_RESULT,
        SEARCH_FAILED
    }

    var currentState: VoiceFlowState = VoiceFlowState.APP_START
        private set

    private var lastSpokenText: String = ""
    private var currentProductName: String = ""
    /** STT 세션 종료 시각. 4초 이내에는 자동 탐지 TTS(거리/방향 안내) 무시용 */
    private var lastSttEndTime = 0L

    /** 앱 시작 시 호출: 상태만 APP_START로 두고, STT는 시작하지 않음 (화면 터치 후 startProductNameInput 호출) */
    fun start() {
        transitionTo(VoiceFlowState.APP_START)
    }

    /** 화면 터치 후 호출: 찾으시는 상품을 말씀해주세요 → STT 시작 (STT에서 "삐 소리가 나면 말씀해주세요" + 삐 재생) */
    fun startProductNameInput() {
        transitionTo(VoiceFlowState.WAITING_PRODUCT_NAME)
        speak(MSG_ASK_PRODUCT) {
            onRequestStartStt()
        }
    }

    /** STT 결과 처리 */
    fun onSttResult(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        when (currentState) {
            VoiceFlowState.WAITING_PRODUCT_NAME -> handleProductNameReceived(normalized)
            VoiceFlowState.WAITING_CONFIRMATION -> handleConfirmationReceived(normalized)
            VoiceFlowState.APP_START,
            VoiceFlowState.CONFIRM_PRODUCT -> {
                if (isRepeatCommand(normalized)) repeatLast()
            }
            VoiceFlowState.SEARCHING_PRODUCT -> {
                if (isRepeatCommand(normalized)) repeatLast()
            }
            VoiceFlowState.SEARCH_RESULT,
            VoiceFlowState.SEARCH_FAILED -> {
                if (isRepeatCommand(normalized)) repeatLast()
                else if (isHelpCommand(normalized)) speakHelp()
            }
        }
    }

    private fun handleProductNameReceived(text: String) {
        when {
            isHelpCommand(text) -> speakHelp()
            isRepeatCommand(text) -> repeatLast()
            else -> {
                currentProductName = text
                transitionTo(VoiceFlowState.CONFIRM_PRODUCT)
                onProductNameEntered(currentProductName)
                val msg = msgConfirmProduct(currentProductName)
                speak(msg) {
                    transitionTo(VoiceFlowState.WAITING_CONFIRMATION)
                    onRequestStartStt()
                }
            }
        }
    }

    private fun handleConfirmationReceived(text: String) {
        val normalized = text.trim()
        val isYes = isConfirmationYes(normalized)
        var branchChosen = "INIT"
        var startSearchCalled = false
        val currentTargetSnapshot = currentProductName

        if (isYes) {
            branchChosen = "YES"
            transitionTo(VoiceFlowState.SEARCHING_PRODUCT)
            val msg = msgSearching(currentProductName)
            speak(msg)
            onStartSearch(currentProductName)
            startSearchCalled = true
        } else {
            branchChosen = "NON_YES_RESET"
            resetToAppStartWithRestartPrompt()
        }

    }

    private fun isConfirmationYes(text: String): Boolean {
        if (SynonymRepository.isLoaded()) {
            return SynonymRepository.isYesAnswer(text)
        }
        val t = text.trim().lowercase().replace(" ", "")
        return t.contains("예") || t.contains("네") || t.contains("내") || t.contains("맞") || t.contains("응") ||
            t == "yes" || t == "y" || t.contains("그래") || t.contains("좋아") ||
            t == "네" || t == "내" || t == "예"
    }

    private fun isConfirmationNo(text: String): Boolean {
        val t = text.trim().lowercase().replace(" ", "")
        return t.contains("아니") || t.contains("틀렸") || t.contains("다른") ||
            t == "no" || t == "n"
    }

    private fun isHelpCommand(text: String) =
        text.contains("도움말") || text.contains("도움") || text.equals("help", ignoreCase = true)

    private fun isRepeatCommand(text: String) =
        text.contains("다시") || text.contains("재생") || text.equals("repeat", ignoreCase = true)

    private fun resetToAppStartWithRestartPrompt() {
        currentProductName = ""
        transitionTo(VoiceFlowState.APP_START)
        // 확인 단계 이후에는 YES가 아닐 경우 언제나 초기 화면으로 복귀 + 동일 안내 멘트
        ttsManager.stop()
        speak(VoicePrompts.PROMPT_TOUCH_RESTART)
    }

    /** 확인/재입력 버튼 클릭 (접근성용 보조) */
    fun onConfirmClicked() {
        if (currentState == VoiceFlowState.CONFIRM_PRODUCT || currentState == VoiceFlowState.WAITING_CONFIRMATION) {
            resetToAppStartWithRestartPrompt()
        }
    }

    /** 재입력 버튼 클릭 */
    fun onReinputClicked() {
        when (currentState) {
            VoiceFlowState.CONFIRM_PRODUCT,
            VoiceFlowState.WAITING_CONFIRMATION -> {
                currentProductName = ""
                transitionTo(VoiceFlowState.WAITING_PRODUCT_NAME)
                speak(MSG_ASK_PRODUCT) {
                    onRequestStartStt()
                }
            }
            VoiceFlowState.SEARCH_FAILED -> {
                transitionTo(VoiceFlowState.WAITING_PRODUCT_NAME)
                speak(MSG_ASK_PRODUCT) {
                    onRequestStartStt()
                }
            }
            else -> {}
        }
    }

    /** 탐색 완료 */
    fun onSearchComplete(
        success: Boolean,
        detectedClass: String? = null,
        boxRect: RectF? = null,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ) {
        if (success) {
            transitionTo(VoiceFlowState.SEARCH_RESULT)
            val displayName = if (!detectedClass.isNullOrBlank() && ProductDictionary.isLoaded())
                ProductDictionary.getDisplayNameKo(detectedClass)
            else
                currentProductName
            val baseMsg = msgSearchResult(displayName)
            val guidance = if (boxRect != null && imageWidth > 0 && imageHeight > 0) {
                buildPositionGuidance(boxRect, imageWidth, imageHeight)
            } else null
            speak(if (guidance != null) "$baseMsg $guidance" else baseMsg)
        } else {
            transitionTo(VoiceFlowState.SEARCH_FAILED)
            speak(MSG_SEARCH_FAILED)
        }
    }

    /** 위치 안내를 주기적으로 반복할 때 호출 (상품명 + 방향/거리) */
    fun announcePosition(displayName: String, boxRect: RectF, imageWidth: Int, imageHeight: Int) {
        val guidance = buildPositionGuidance(boxRect, imageWidth, imageHeight)
        speak("$displayName. $guidance")
    }

    /**
     * 사용자 기준 방향만 짧게 안내 (5초마다 재생)
     */
    private fun buildPositionGuidance(box: RectF, w: Int, h: Int): String {
        val centerX = (box.left + box.right) / 2f / w
        return when {
            centerX < 0.3f -> "왼쪽."
            centerX < 0.45f -> "조금 왼쪽."
            centerX < 0.55f -> "정면."
            centerX < 0.7f -> "조금 오른쪽."
            else -> "오른쪽."
        }
    }

    /** 다시 찾기 */
    fun onRetrySearch() {
        if (currentState != VoiceFlowState.SEARCH_FAILED && currentState != VoiceFlowState.SEARCH_RESULT) return
        transitionTo(VoiceFlowState.WAITING_PRODUCT_NAME)
        speak(MSG_ASK_PRODUCT) {
            onRequestStartStt()
        }
    }

    private fun speakHelp() {
        speak(MSG_HELP)
        if (currentState == VoiceFlowState.APP_START || currentState == VoiceFlowState.WAITING_PRODUCT_NAME) {
            transitionTo(VoiceFlowState.WAITING_PRODUCT_NAME)
        }
    }

    fun repeatLast() {
        if (lastSpokenText.isNotBlank()) {
            speak(lastSpokenText)
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        lastSpokenText = text
        ttsManager.speak(text, TextToSpeech.QUEUE_FLUSH, onDone)
    }

    private fun transitionTo(state: VoiceFlowState) {
        currentState = state
        val stateLabel = when (state) {
            VoiceFlowState.APP_START -> "앱 시작"
            VoiceFlowState.WAITING_PRODUCT_NAME -> "상품명 입력 대기"
            VoiceFlowState.CONFIRM_PRODUCT -> "상품 확인"
            VoiceFlowState.WAITING_CONFIRMATION -> "확인 대기"
            VoiceFlowState.SEARCHING_PRODUCT -> "탐색 중"
            VoiceFlowState.SEARCH_RESULT -> "탐색 결과"
            VoiceFlowState.SEARCH_FAILED -> "탐색 실패"
        }
        onStateChanged(state, stateLabel)
    }

    fun announceSystem(message: String) {
        lastSpokenText = message
        onSystemAnnounce(message)
        ttsManager.speak(message)
    }

    fun getCurrentProductName(): String = currentProductName

    /** STT 대기 종료 또는 TOUCH_CONFIRM 해제 직후 호출. 4초간 자동 안내 TTS 및 TOUCH_CONFIRM 재진입 차단 */
    fun notifySttEnded() {
        lastSttEndTime = System.currentTimeMillis()
    }

    /** 사용자 대화 직후 4초 이내이면 true. 이 구간에는 자동 탐지 TTS·TOUCH_CONFIRM 재진입 무시 */
    fun isInSttBreathingRoom(): Boolean =
        (System.currentTimeMillis() - lastSttEndTime) < 4000L

    /** 완전 리셋 시 breathing room 해제 (resetGlobalState 등에서 호출) */
    fun resetBreathingRoom() {
        lastSttEndTime = 0L
    }
}
