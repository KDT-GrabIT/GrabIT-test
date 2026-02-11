package com.example.grabitTest.data.synonym

import com.google.gson.annotations.SerializedName

/**
 * MongoDB/API 유의어·근접단어 문서 구조.
 * - 대답용: keyword(예: "예") + proximityWords(네, 응, 맞아 등)
 * - 상품용: classId + displayName + proximityWords(포카리, 포카리 스웨트 등)
 */

/** 대답 근접단어 1건 (확인 질문에 대한 긍정/부정 등) */
data class AnswerProximityDoc(
    @SerializedName("keyword") val keyword: String = "",
    @SerializedName("proximity_words") val proximityWords: List<String> = emptyList(),
    @SerializedName("type") val type: String = "answer"
)

/** 상품 근접단어 1건 (STT 인식용) */
data class ProductProximityDoc(
    @SerializedName("class_id") val classId: String = "",
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("proximity_words") val proximityWords: List<String> = emptyList(),
    @SerializedName("type") val type: String = "product"
)

/** API 응답: 대답 목록 */
data class AnswerProximityResponse(
    @SerializedName("items") val items: List<AnswerProximityDoc> = emptyList()
)

/** API 응답: 상품 목록 */
data class ProductProximityResponse(
    @SerializedName("items") val items: List<ProductProximityDoc> = emptyList()
)
