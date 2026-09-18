plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kstudio.agenda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kstudio.agenda"
        minSdk = 35          // Android 15+（用户要求）
        targetSdk = 35
        // 版本命名逻辑（2026.9 起）：年份.月份 v序号；versionCode = 年×10000 + 月×100 + 序号
        versionCode = 20260902
        versionName = "2026.9 v2"
        // WebView 相关无 NDK 需求
    }

    buildTypes {
        release {
            // 性能优先：R8 代码压缩/混淆 + 资源收缩；用 debug 签名，可直接安装并覆盖调试包
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM 统一版本
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // 设置存储
    implementation(libs.androidx.datastore.preferences)

    // 后台定时任务（课表提醒兜底调度 / 自动刷新）
    implementation(libs.androidx.work.runtime.ktx)

    // 单元测试（解析器等纯 JVM 逻辑）
    testImplementation("junit:junit:4.13.2")
}
