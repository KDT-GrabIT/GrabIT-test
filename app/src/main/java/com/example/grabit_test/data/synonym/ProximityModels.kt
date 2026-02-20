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
    /**
     * 현재 DB 스키마: 문서 최상단 width (cm)
     * 임시 안정화: 서버에서 width가 숫자/문자열/빈문자열 등으로 내려와도 파싱 에러를 피하기 위해 Any? 사용.
     * TODO(temp): 서버 width 타입 정리 후 Float?로 되돌리기.
     */
    @SerializedName("width") val width: Any? = null,
    /** 서버 DB의 상품 치수(size). width/length/height는 문자열 또는 빈값으로 내려올 수 있음 */
    @SerializedName("size") val size: ProductSize? = null,
    @SerializedName("type") val type: String = "product"
)

data class ProductSize(
    @SerializedName("width") val width: String? = null,
    @SerializedName("length") val length: String? = null,
    @SerializedName("height") val height: String? = null
)

/** API 응답: 대답 목록 */
data class AnswerProximityResponse(
    @SerializedName("items") val items: List<AnswerProximityDoc> = emptyList()
)

/** API 응답: 상품 목록 */
data class ProductProximityResponse(
    @SerializedName("items") val items: List<ProductProximityDoc> = emptyList()
)

/** Product dimensions payload from /product-dimensions */
data class ProductDimensionDoc(
    @SerializedName("class_id") val classId: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("width") val width: Float? = null,
    @SerializedName("width_cm") val widthCm: Float? = null,
    @SerializedName("size") val size: ProductSize? = null
)

data class ProductDimensionResponse(
    @SerializedName("items") val items: List<ProductDimensionDoc> = emptyList()
)
