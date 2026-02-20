package com.example.grabit_test.data.sync

import android.content.Context
import android.util.Log
import com.example.grabitTest.config.MongoConfig
import com.example.grabitTest.data.synonym.AnswerProximityDoc
import com.example.grabitTest.data.synonym.ProductDimensionDoc
import com.example.grabitTest.data.synonym.ProductProximityDoc
import com.example.grabitTest.data.synonym.SynonymApi
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DataSyncManager"
private const val LOCAL_SYNONYMS_FILE = "local_synonyms.json"
private const val LOCAL_DIMENSIONS_FILE = "local_dimensions.json"

data class LocalSynonymsPayload(
    val answers: List<AnswerProximityDoc> = emptyList(),
    val products: List<ProductProximityDoc> = emptyList()
)

data class LocalDimensionsPayload(
    val items: List<ProductDimensionDoc> = emptyList()
)

/**
 * Synchronizes remote server data into app local files (filesDir).
 * If network fetch fails, existing local files are kept unchanged.
 */
object DataSyncManager {
    private val gson = Gson()

    suspend fun syncAll(context: Context) = withContext(Dispatchers.IO) {
        if (!MongoConfig.isApiConfigured()) {
            Log.d(TAG, "API is not configured. Skip remote sync.")
            return@withContext
        }

        val api = SynonymApi.create()

        syncSynonyms(context, api)
        syncDimensions(context, api)
    }

    private suspend fun syncSynonyms(context: Context, api: SynonymApi) {
        try {
            val answers = api.getAnswerProximityWords().items
            val products = api.getProductProximityWords().items
            val payload = LocalSynonymsPayload(answers = answers, products = products)
            writeText(File(context.filesDir, LOCAL_SYNONYMS_FILE), gson.toJson(payload))
            Log.i(TAG, "Synonyms synced: answers=${answers.size}, products=${products.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Synonyms sync failed. Keep existing local file.", e)
        }
    }

    private suspend fun syncDimensions(context: Context, api: SynonymApi) {
        try {
            val items = api.getProductDimensions().items
            val payload = LocalDimensionsPayload(items = items)
            writeText(File(context.filesDir, LOCAL_DIMENSIONS_FILE), gson.toJson(payload))
            Log.i(TAG, "Dimensions synced: items=${items.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Dimensions sync failed. Keep existing local file.", e)
        }
    }

    private fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }
}

