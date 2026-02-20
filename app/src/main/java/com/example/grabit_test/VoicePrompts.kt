package com.example.grabitTest

/**
 * 음성 UX용 TTS 문구 상수 (실제 사용 중인 것만)
 */
object VoicePrompts {
    const val PROMPT_IDLE_TOUCH = "화면을 터치해서 시작해주세요."
    const val PROMPT_PRODUCT_RECOGNITION_FAILED =
        "상품명을 인식하지 못했어요. 화면을 터치해서 다시 시작해주세요."
    const val PROMPT_TOUCH_RESTART = "그럼 화면을 터치해서 다시 시작해주세요."
    const val PROMPT_ASK_GRASP_CONFIRM = "상품에 닿았나요? 맞으면 예라고 말씀해주세요."
    /** TOUCH_CONFIRM 긍정 대답 후 완전 종료 시 TTS */
    const val PROMPT_FOUND_AND_END = "물건을 찾았습니다. 안내를 종료합니다"
}

