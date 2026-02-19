package com.example.grabitTest.config

/**
 * synonym-api 서버 설정.
 * - BASE_URL만 사용. 비우면 API 호출 안 하고 로컬(기본 대답 + product_dictionary)만 사용.
 * - MongoDB 연결문자열은 앱에 넣지 말고, synonym-api 폴더의 .env 에만 넣으세요.
 */
object MongoConfig {

    /** synonym-api 서버 주소. 비우면 원격 조회 안 함. 에뮬레이터=http://10.0.2.2:3000, 실제 기기=PC IP:3000
     *  Mac에서 PC IP 확인: 터미널에서 `ipconfig getifaddr en0` (Wi‑Fi) 또는 `ipconfig getifaddr en1` 등 */
    const val BASE_URL: String = "" // 예: "http://10.0.2.2:3000" 또는 "http://192.168.x.x:3000"

    /** API 사용 여부 (BASE_URL이 비어있지 않으면 원격 조회 시도) */
    fun isApiConfigured(): Boolean = BASE_URL.isNotBlank()
}
