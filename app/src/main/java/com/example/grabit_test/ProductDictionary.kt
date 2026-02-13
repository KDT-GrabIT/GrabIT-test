package com.example.grabitTest

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * 상품 클래스명 ↔ 한국어 매핑 (product_dictionary.json)
 * - STT 결과(한국어/별칭) → 영어 클래스명
 * - 영어 클래스명 → TTS/표시용 한국어 (tts_ko)
 */
object ProductDictionary {

    private const val TAG = "ProductDictionary"
    private const val ASSET_FILE = "product_dictionary.json"

    data class ProductEntry(
        val aliases: List<String>,
        val ttsKo: String
    )

    /** 상품별 물리 너비(mm). 거리 추정·줌 로직용 */
    data class ProductInfo(
        val classLabel: String,
        val physicalWidthMm: Float,
        val displayNameKo: String
    )

    /** key: 영어 클래스명, value: ProductEntry */
    private var mapByClass: Map<String, ProductEntry> = emptyMap()

    /** alias/한국어 (정규화) → 영어 클래스명. 로드 시 빌드 */
    private var aliasToClass: Map<String, String> = emptyMap()

    /**
     * assets/product_dictionary.json 로드.
     * Context (Application 또는 Activity)에서 호출.
     */
    fun load(context: Context): Boolean {
        return try {
            context.assets.open(ASSET_FILE).use { stream ->
                val json = InputStreamReader(stream, Charsets.UTF_8).readText()
                val root = JSONObject(json)
                val byClass = mutableMapOf<String, ProductEntry>()
                val byAlias = mutableMapOf<String, String>()

                for (key in root.keys()) {
                    val obj = root.getJSONObject(key)
                    val aliases = mutableListOf<String>()
                    if (obj.has("aliases")) {
                        val arr = obj.getJSONArray("aliases")
                        for (i in 0 until arr.length()) {
                            aliases.add(arr.getString(i))
                        }
                    }
                    val ttsKo = if (obj.has("tts_ko")) obj.getString("tts_ko") else key
                    byClass[key] = ProductEntry(aliases = aliases, ttsKo = ttsKo)

                    val keyNorm = normalizeForMatch(key)
                    byAlias[keyNorm] = key
                    for (a in aliases) {
                        byAlias[normalizeForMatch(a)] = key
                    }
                }

                mapByClass = byClass
                aliasToClass = byAlias
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "로드 실패", e)
            false
        }
    }

    /** 매칭용 정규화: 공백 제거, 소문자(영문), trim */
    private fun normalizeForMatch(s: String): String {
        return s.trim().replace(" ", "").lowercase()
    }

    /**
     * STT 결과(한국어/별칭/영어 클래스명)로 영어 클래스명 찾기.
     * @return 매칭되는 영어 클래스명, 없으면 null
     */
    fun findClassByStt(sttText: String): String? {
        if (sttText.isBlank()) return null
        val norm = normalizeForMatch(sttText)
        if (norm.isEmpty()) return null
        // 1) 정확 일치
        aliasToClass[norm]?.let { return it }
        // 2) 퍼지 매칭: alias가 norm을 포함하거나 norm이 alias를 포함. 가장 긴 매칭 우선(오매칭 방지)
        return aliasToClass.entries
            .filter { (aliasNorm, _) -> aliasNorm.contains(norm) || norm.contains(aliasNorm) }
            .maxByOrNull { (aliasNorm, _) -> aliasNorm.length }
            ?.value
    }

    /**
     * 영어 클래스명에 대한 TTS/표시용 한국어.
     * @return tts_ko 값, 없으면 영어 클래스명 그대로
     */
    fun getDisplayNameKo(englishClass: String): String {
        if (englishClass.isBlank()) return englishClass
        return mapByClass[englishClass]?.ttsKo ?: englishClass
    }

    /**
     * 영어 클래스명의 별칭 목록.
     */
    fun getAliases(englishClass: String): List<String> {
        return mapByClass[englishClass]?.aliases ?: emptyList()
    }

    /** 로드된 모든 영어 클래스명 */
    fun allClassNames(): Set<String> = mapByClass.keys.toSet()

    /** 로드 여부 */
    fun isLoaded(): Boolean = mapByClass.isNotEmpty()

    /**
     * 물리적 너비(mm) — 원거리 거리/걸음 안내용.
     * 500ml_pet 65, 250ml_can 53, 355ml_can 65, snack_bag_normal 170,
     * snack_bag_large 220, snack_box_12pack 250, 기타 100.
     */
    fun getPhysicalWidthMm(label: String): Float {
        if (label.isBlank()) return 100f
        val lower = label.trim().lowercase()
        return when {
            // snack_box_12pack 250mm
            lower.contains("12pack") || (lower.contains("moncher") && lower.contains("cacao")) -> 250f
            // snack_bag_large 220mm
            lower.contains("heim") || lower.contains("daije") || lower.contains("gosomi") ||
            lower.contains("chic_choc") || lower.contains("margaret") || lower.contains("matdongsan") -> 220f
            // 250ml_can 53mm
            lower.contains("250ml") -> 53f
            // 355ml_can 65mm
            lower.contains("355ml") -> 65f
            // 500ml_pet 65mm (500ml, 238ml, 600ml 등 PET)
            lower.contains("500ml") || lower.contains("238ml") || lower.contains("600ml") ||
            lower.contains("drink") || lower.contains("tea") || lower.contains("soda") ||
            lower.contains("200ml") || lower.contains("mccol") || lower.contains("17tea") -> 65f
            // snack_bag_normal 170mm (일반 봉지/슬림 포함)
            lower.contains("pringles") || lower.contains("pepero") || lower.contains("ml").not() -> 170f
            else -> 100f
        }
    }

    /** ProductInfo 반환 (표시명 + physicalWidthMm) */
    fun getProductInfo(englishClass: String): ProductInfo? {
        if (englishClass.isBlank()) return null
        val entry = mapByClass[englishClass] ?: return null
        return ProductInfo(
            classLabel = englishClass,
            physicalWidthMm = getPhysicalWidthMm(englishClass),
            displayNameKo = entry.ttsKo
        )
    }
}
