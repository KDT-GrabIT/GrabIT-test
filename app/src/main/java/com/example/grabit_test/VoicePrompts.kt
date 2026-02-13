package com.example.grabitTest

/**
 * 음성 UX용 TTS 문구 상수 모음 (최종 단순화 버전)
 */
object VoicePrompts {
    const val PROMPT_IDLE_TOUCH = "화면을 터치해서 시작해주세요."
    const val PROMPT_SAY_PRODUCT = "찾으시는 상품명을 말씀해주세요."
    const val PROMPT_PRODUCT_RECOGNITION_FAILED =
        "상품명을 인식하지 못했어요. 화면을 터치해서 다시 시작해주세요."
    const val PROMPT_CONFIRM_YES_ONLY_PREFIX =
        "찾으시는 상품이"
    const val PROMPT_CONFIRM_YES_ONLY_SUFFIX =
        "맞습니까? 맞으면 예라고 말해주세요."
    const val PROMPT_TOUCH_RESTART = "그럼 화면을 터치해서 다시 시작해주세요."
    const val PROMPT_TOUCH_NOW = "지금 잡으세요."
    const val PROMPT_DONE = "완료되었습니다."
    /** TOUCH_CONFIRM 긍정 대답 후 완전 종료 시 TTS */
    const val PROMPT_FOUND_AND_END = "물건을 찾았습니다. 안내를 종료합니다"
}

