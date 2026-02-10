plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.grabitTest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.grabitTest"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // NDK r28: 16KB 페이지 크기 기본 지원 (libc++_shared.so 등)
        ndkVersion = "28.0.12433566"
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

    packaging {
        jniLibs {
            useLegacyPackaging = true
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
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    // 1. MediaPipe 0.10.26 (16KB 지원)
    implementation("com.google.mediapipe:tasks-vision:0.10.26")

    // 2. CameraX 1.4.1
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // 3. LiteRT (16KB 지원)
    implementation("com.google.ai.edge.litert:litert:1.4.1")
    implementation("com.google.ai.edge.litert:litert-gpu:1.4.1")
    implementation("com.google.ai.edge.litert:litert-support:1.4.1")

    // 4. OpenCV 4.12.0 (16KB 페이지 호환, 4.9.0은 미지원)
    implementation("org.opencv:opencv:4.12.0")
}
