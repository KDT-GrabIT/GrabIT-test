package com.example.grabitTest.data.synonym

import com.example.grabitTest.config.MongoConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * synonym-api 서버 유의어/근접단어 API.
 * - BASE_URL/synonyms/answers -> 대답 근접단어
 * - BASE_URL/synonyms/products -> 상품 근접단어
 * (현재 서버는 인증 헤더 없이 동작)
 */
interface SynonymApi {

    @GET("synonyms/answers")
    suspend fun getAnswerProximityWords(): AnswerProximityResponse

    @GET("synonyms/products")
    suspend fun getProductProximityWords(): ProductProximityResponse

    companion object {
        private const val TIMEOUT_SEC = 15L

        fun create(): SynonymApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
                .addInterceptor(
                    HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
                )
                .build()

            val baseUrl = MongoConfig.BASE_URL.let {
                if (it.endsWith("/")) it else "$it/"
            }
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SynonymApi::class.java)
        }
    }
}
