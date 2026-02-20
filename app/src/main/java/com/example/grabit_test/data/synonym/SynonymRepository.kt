package com.example.grabitTest.data.synonym

import android.content.Context
import android.util.Log
import com.example.grabitTest.config.MongoConfig
import com.example.grabit_test.data.sync.LocalSynonymsPayload
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 대답/상품 근접단어 통합 저장소.
 * - API 설정 시: MongoDB(백엔드)에서 조회 후 메모리 캐시.
 * - API 미설정 시: 기본 대답 목록만 사용 (로컬 동작).
 * - 성공적으로 로드한 뒤에만 lastPartialText 스타일로 캐시 초기화하지 않고 유지.
 */
object SynonymRepository {

    private const val TAG = "SynonymRepository"
    private const val LOCAL_FILE = "local_synonyms.json"
    private val gson = Gson()

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
    /** classId -> 서버에서 내려온 상품 너비(mm). size.width(cm)를 mm로 환산해서 저장 */
    private var productWidthMmByClassId: Map<String, Float> = emptyMap()

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

    /**
     * Loads synonyms from filesDir/local_synonyms.json.
     * Falls back to default answer words if file is missing or invalid.
     */
    suspend fun loadFromLocal(context: Context) = withContext(Dispatchers.IO) {
        loadDefaultAnswers()
        try {
            val file = File(context.filesDir, LOCAL_FILE)
            if (!file.exists()) {
                Log.d(TAG, "Local synonyms file not found. Using default answers only.")
                return@withContext
            }
            val payload = gson.fromJson(file.readText(Charsets.UTF_8), LocalSynonymsPayload::class.java)
            mergeAnswerDocs(payload.answers)
            mergeProductDocs(payload.products)
            lastLoadSuccess = true
            Log.i(TAG, "Loaded local synonyms: answers=${payload.answers.size}, products=${payload.products.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local synonyms. Keeping default answers.", e)
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
        rebuildProductWidthMap()
    }

    /** 서버의 size.width(cm)를 mm로 변환해 classId별로 캐시 */
    private fun rebuildProductWidthMap() {
        val map = mutableMapOf<String, Float>()
        productProximityCache.values.forEach { doc ->
            // 1순위: 현재 DB 스키마(최상단 width), 2순위: 이전 size.width
            val widthCm = doc.width ?: doc.size?.width?.trim()?.takeIf { it.isNotEmpty() }?.toFloatOrNull()
            if (widthCm != null && widthCm > 0f) {
                map[doc.classId] = widthCm * 10f
            }
        }
        productWidthMmByClassId = map
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

    /** 서버 DB(상품 size) 기반 물리 너비(mm). 없으면 null */
    fun getPhysicalWidthMm(classId: String): Float? = productWidthMmByClassId[classId]

    /**
     * Local-first product class resolver.
     * If local cache misses, it queries /synonyms/search as a remote fallback.
     */
    suspend fun findClassByNameWithFallback(spokenText: String): String? = withContext(Dispatchers.IO) {
        findClassByProximity(spokenText)?.let { return@withContext it }
        if (!MongoConfig.isApiConfigured()) return@withContext null

        return@withContext try {
            val items = SynonymApi.create().searchProducts(spokenText, topK = 5).items
            if (items.isNotEmpty()) {
                mergeProductDocs(items)
                items.first().classId.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote fallback search failed", e)
            null
        }
    }

    fun isLoaded(): Boolean = answerNormalizedToKind.isNotEmpty()
    fun lastLoadSuccess(): Boolean = lastLoadSuccess
}
