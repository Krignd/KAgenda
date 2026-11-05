plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.kstudio.agenda.engine.ocr"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        providers.gradleProperty("kagenda.engineAbi").orNull?.let { ndk { abiFilters += it } }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":app"))
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
