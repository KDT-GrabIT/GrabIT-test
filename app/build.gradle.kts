plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.grabitTest"
    compileSdk = 35

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }

        defaultConfig {
            applicationId = "com.example.grabitTest"
            minSdk = 24
            targetSdk = 35
            versionCode = 1
            versionName = "1.0"
        }

        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        kotlinOptions {
            jvmTarget = "1.8"
        }

        buildFeatures {
            viewBinding = true
        }
    }

    dependencies {
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("com.google.android.material:material:1.11.0")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")

        // 1. [수정] MediaPipe (0.10.14 -> 0.10.26)
        // 이전 버전(0.10.14)은 16KB를 지원하지 않아 에러의 주원인이 됩니다.
        implementation("com.google.mediapipe:tasks-vision:0.10.26")

        // 2. [수정] CameraX (1.3.1 -> 1.4.1)
        // 'libimage_processing_util_jni.so' 에러 해결을 위해 1.4.0 이상 필수
        implementation("androidx.camera:camera-camera2:1.4.1")
        implementation("androidx.camera:camera-lifecycle:1.4.1")
        implementation("androidx.camera:camera-view:1.4.1")

        // 3. [수정] TFLite -> LiteRT (16KB 지원 버전으로 교체)
        // 기존 2.14.0은 16KB 미지원으로 빌드 에러 유발함
        implementation("com.google.ai.edge.litert:litert:1.4.1")
        implementation("com.google.ai.edge.litert:litert-gpu:1.4.1")
        // Support 라이브러리도 LiteRT 버전으로 맞춤 (선택사항이나 충돌 방지 권장)
        implementation("com.google.ai.edge.litert:litert-support:1.4.1")
    }
}