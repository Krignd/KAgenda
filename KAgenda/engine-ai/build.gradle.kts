plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.kstudio.agenda.engine.ai"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        providers.gradleProperty("kagenda.engineAbi").orNull?.let { ndk { abiFilters += it } }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    ndkVersion = "27.2.12479018"
    defaultConfig { externalNativeBuild { cmake {
        arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
        providers.gradleProperty("kagenda.llamaSource").orNull?.let { arguments += "-DKAGENDA_LLAMA_SOURCE=$it" }
    } } }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
dependencies {
    implementation(project(":app"))

}
