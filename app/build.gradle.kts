plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.npuclassifier"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.npuclassifier"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "3.0-phase3"
        ndk { abiFilters += "arm64-v8a" }
    }
    // keep model files uncompressed so they can be memory-mapped
    androidResources { noCompress += "tflite" }
    androidResources { noCompress += "onnx" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" } // matches Kotlin 1.9.24
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
}
