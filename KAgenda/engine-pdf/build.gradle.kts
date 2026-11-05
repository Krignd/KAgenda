plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.kstudio.agenda.engine.pdf"
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
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
