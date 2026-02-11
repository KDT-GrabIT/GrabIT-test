package com.example.grabitTest.config

/**
 * synonym-api 서버 설정.
 * - BASE_URL만 사용. 비우면 API 호출 안 하고 로컬(기본 대답 + product_dictionary)만 사용.
 * - MongoDB 연결문자열은 앱에 넣지 말고, synonym-api 폴더의 .env 에만 넣으세요.
 */
object MongoConfig {

    /** synonym-api 서버 주소. 에뮬레이터=10.0.2.2:3000, 실제 기기=PC IP:포트 (synonym-api는 기본 3000). */
    const val BASE_URL: String = "http://192.168.9.1:8000"

    /** API 사용 여부 (BASE_URL이 비어있지 않으면 원격 조회 시도) */
    fun isApiConfigured(): Boolean = BASE_URL.isNotBlank()
}
