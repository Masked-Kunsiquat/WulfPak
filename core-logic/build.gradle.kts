plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.github.maskedkunisquat.wulfpak.core.logic"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":core-data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // On-device embedding (Snowflake Arctic XS TFLite)
    implementation(libs.litert)

    // On-device LLM (Gemma 3 1B LiteRT-LM)
    implementation(libs.litertlm.android)

    // Background embedding worker
    implementation(libs.androidx.work.runtime.ktx)
}
