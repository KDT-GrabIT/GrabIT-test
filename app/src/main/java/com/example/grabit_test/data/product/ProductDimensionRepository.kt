package com.example.grabit_test.data.product

import android.content.Context
import android.util.Log
import com.example.grabitTest.data.synonym.ProductDimensionDoc
import com.example.grabit_test.data.sync.LocalDimensionsPayload
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ProductDimensionRepo"
private const val LOCAL_FILE = "local_dimensions.json"
private const val ASSET_FALLBACK_FILE = "product_dimensions.json"

/**
 * In-memory product width cache for distance estimation.
 * Source priority:
 * 1) filesDir/local_dimensions.json (synced from server)
 * 2) assets/product_dimensions.json (fallback)
 */
object ProductDimensionRepository {
    private val gson = Gson()
    @Volatile
    private var widthMmByClassId: Map<String, Float> = emptyMap()

    suspend fun loadFromLocal(context: Context) = withContext(Dispatchers.IO) {
        try {
            val localFile = File(context.filesDir, LOCAL_FILE)
            if (localFile.exists()) {
                val payload = gson.fromJson(localFile.readText(Charsets.UTF_8), LocalDimensionsPayload::class.java)
                buildCache(payload.items)
                Log.i(TAG, "Loaded widths from local file: ${widthMmByClassId.size}")
                return@withContext
            }

            // Fallback for first install / offline bootstrap
            context.assets.open(ASSET_FALLBACK_FILE).use { stream ->
                val text = stream.reader(Charsets.UTF_8).readText()
                val payload = gson.fromJson(text, LocalDimensionsPayload::class.java)
                buildCache(payload.items)
                Log.i(TAG, "Loaded widths from asset fallback: ${widthMmByClassId.size}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local dimensions cache", e)
        }
    }

    fun getWidthMmByClassId(classId: String): Float? = widthMmByClassId[classId]

    private fun buildCache(items: List<ProductDimensionDoc>) {
        val map = mutableMapOf<String, Float>()
        items.forEach { doc ->
            val classId = doc.classId.trim()
            if (classId.isBlank()) return@forEach
            val widthCm = doc.width ?: doc.widthCm ?: doc.size?.width?.trim()?.toFloatOrNull()
            if (widthCm != null && widthCm > 0f) {
                map[classId] = widthCm * 10f
            }
        }
        widthMmByClassId = map
    }
}
