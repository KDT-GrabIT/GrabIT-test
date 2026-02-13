plugins {
    // AGP 8.5.1+ 필수: 16KB 페이지 크기 호환성 (Google Play 2025.11.01 요구사항)
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
}