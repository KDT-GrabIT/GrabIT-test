package com.example.grabitTest.data.synonym

import android.util.Log
import com.example.grabitTest.config.MongoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 대답/상품 근접단어 통합 저장소.
 * - API 설정 시: MongoDB(백엔드)에서 조회 후 메모리 캐시.
 * - API 미설정 시: 기본 대답 목록만 사용 (로컬 동작).
 * - 성공적으로 로드한 뒤에만 lastPartialText 스타일로 캐시 초기화하지 않고 유지.
 */
object SynonymRepository {

    private const val TAG = "SynonymRepository"

    /** 기본 긍정 대답 (API 없을 때/API 항목 보강용) */
    private val defaultYesWords = listOf("예", "네", "응", "맞", "맞아", "맞아요", "yes", "y", "그래", "좋아")
    /** 기본 부정 대답 */
    private val defaultNoWords = listOf("아니", "아니요", "틀렸", "다른", "no", "n")

    /** keyword(정규화) -> 해당 키워드의 근접단어 목록. type=answer */
    private val answerProximityCache = ConcurrentHashMap<String, List<String>>()
    /** 정규화된 근접단어 -> "yes" | "no" (대답 판정용) */
    private var answerNormalizedToKind: Map<String, String> = emptyMap()

    /** classId -> ProductProximityDoc. type=product */
    private val productProximityCache = ConcurrentHashMap<String, ProductProximityDoc>()
    /** 정규화된 proximity word -> classId (STT 매칭용) */
    private var productNormalizedToClassId: Map<String, String> = emptyMap()

    private var lastLoadSuccess = false

    /**
     * API가 설정되어 있으면 원격에서 로드하고, 없으면 기본 대답만 적용.
     * 메인 스레드에서 호출하지 말고, 코루틴/백그라운드에서 호출 권장.
     */
    suspend fun loadFromRemote() = withContext(Dispatchers.IO) {
        loadDefaultAnswers()
        if (!MongoConfig.isApiConfigured()) {
            Log.d(TAG, "유의어 원격 로드: API 미설정, 기본 대답만 사용")
            lastLoadSuccess = true
            return@withContext
        }
        Log.d(TAG, "유의어 원격 로드 시작")
        try {
            val api = SynonymApi.create()
            val answers = api.getAnswerProximityWords()
            val products = api.getProductProximityWords()
            mergeAnswerDocs(answers.items)
            mergeProductDocs(products.items)
            lastLoadSuccess = true
            Log.d(TAG, "유의어 원격 로드 성공")
        } catch (e: Exception) {
            Log.e(TAG, "유의어 원격 로드 실패, 기본값 유지", e)
            lastLoadSuccess = false
        }
    }

    private fun loadDefaultAnswers() {
        answerProximityCache["yes"] = defaultYesWords
        answerProximityCache["no"] = defaultNoWords
        rebuildAnswerNormalizedMap()
    }

    private fun mergeAnswerDocs(docs: List<AnswerProximityDoc>) {
        if (docs.isEmpty()) return
        docs.forEach { doc ->
            val key = doc.keyword.trim().lowercase().replace(" ", "")
            if (key.isNotEmpty()) {
                val words = doc.proximityWords.map { it.trim() }.filter { it.isNotBlank() }
                if (words.isNotEmpty()) {
                    answerProximityCache[key] = words
                }
            }
        }
        rebuildAnswerNormalizedMap()
    }

    private fun rebuildAnswerNormalizedMap() {
        val map = mutableMapOf<String, String>()
        answerProximityCache.forEach { (kind, words) ->
            val normKind = if (kind == "yes" || kind.contains("긍정")) "yes" else "no"
            words.forEach { w ->
                val n = normalize(w)
                if (n.isNotEmpty()) map[n] = normKind
            }
        }
        defaultYesWords.forEach { map[normalize(it)] = "yes" }
        defaultNoWords.forEach { map[normalize(it)] = "no" }
        answerNormalizedToKind = map
    }

    private fun mergeProductDocs(docs: List<ProductProximityDoc>) {
        if (docs.isEmpty()) return
        docs.forEach { doc ->
            if (doc.classId.isNotBlank()) {
                productProximityCache[doc.classId] = doc
            }
        }
        rebuildProductNormalizedMap()
    }

    private fun rebuildProductNormalizedMap() {
        val map = mutableMapOf<String, String>()
        productProximityCache.values.forEach { doc ->
            val id = doc.classId
            listOf(doc.displayName).plus(doc.proximityWords).forEach { w ->
                val n = normalize(w)
                if (n.isNotEmpty()) map[n] = id
            }
        }
        productNormalizedToClassId = map
    }

    private fun normalize(s: String): String = s.trim().replace(" ", "").lowercase()

    // --- 대답 판정 (VoiceFlowController 등에서 사용) ---

    /** 정규화된 입력이 긍정(예)이면 true */
    fun isYesAnswer(text: String): Boolean = answerNormalizedToKind[normalize(text)] == "yes"

    /** 정규화된 입력이 부정(아니오)이면 true */
    fun isNoAnswer(text: String): Boolean = answerNormalizedToKind[normalize(text)] == "no"

    /** 모든 긍정 근접단어 (중복 제거) */
    fun getAllYesWords(): Set<String> = answerProximityCache["yes"]?.toSet().orEmpty() + defaultYesWords.toSet()

    /** 모든 부정 근접단어 */
    fun getAllNoWords(): Set<String> = answerProximityCache["no"]?.toSet().orEmpty() + defaultNoWords.toSet()

    // --- 상품 매칭 (ProductDictionary와 병행 사용 가능) ---

    /** STT 텍스트로 classId 찾기 (근접단어만 사용). 없으면 null */
    fun findClassByProximity(sttText: String): String? {
        val n = normalize(sttText)
        if (n.isEmpty()) return null
        productNormalizedToClassId[n]?.let { return it }
        return productNormalizedToClassId.entries
            .filter { (aliasNorm, _) -> aliasNorm.contains(n) || n.contains(aliasNorm) }
            .maxByOrNull { (aliasNorm, _) -> aliasNorm.length }
            ?.value
    }

    /** classId에 대한 표시명 (원격 데이터 우선) */
    fun getDisplayName(classId: String): String? = productProximityCache[classId]?.displayName

    fun isLoaded(): Boolean = answerNormalizedToKind.isNotEmpty()
    fun lastLoadSuccess(): Boolean = lastLoadSuccess
}
