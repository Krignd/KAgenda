import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ============================================================================
// release 签名配置
//
// 私钥与口令**绝不入库**（`.gitignore` 已忽略 *.jks / *.keystore / keystore.properties）：
// 口令写在仓库外的 `KAgenda/local.properties`（已被 .gitignore 忽略）里，形如
//
//     KAGENDA_STORE_FILE=../krignd-release-key.jks   # 相对 KAgenda/ 目录，也可写绝对路径
//     KAGENDA_STORE_PASSWORD=……
//     KAGENDA_KEY_ALIAS=……
//     KAGENDA_KEY_PASSWORD=……
//
// · 四项齐全 → release 用正式签名；
// · 一项都没写 → 退回 AGP 默认 debug 签名（保证克隆公开仓库的人也能直接构建）；
// · 只写了一半 → **直接报错**（避免误以为用了正式签名，其实是 debug 包）。
// ============================================================================
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingProp(key: String): String? =
    signingProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

val signingKeys = listOf(
    "KAGENDA_STORE_FILE",
    "KAGENDA_STORE_PASSWORD",
    "KAGENDA_KEY_ALIAS",
    "KAGENDA_KEY_PASSWORD",
)
val signingConfiguredCount = signingKeys.count { signingProp(it) != null }
require(signingConfiguredCount == 0 || signingConfiguredCount == signingKeys.size) {
    "local.properties 里的签名配置不完整：${signingKeys.joinToString("/")} 需要同时填写（当前填了 $signingConfiguredCount 项）"
}
val useReleaseSigning = signingConfiguredCount == signingKeys.size

android {
    namespace = "com.kstudio.agenda"
    compileSdk = 35

    signingConfigs {
        if (useReleaseSigning) {
            create("kagendaRelease") {
                storeFile = rootProject.file(signingProp("KAGENDA_STORE_FILE")!!)
                storePassword = signingProp("KAGENDA_STORE_PASSWORD")
                keyAlias = signingProp("KAGENDA_KEY_ALIAS")
                keyPassword = signingProp("KAGENDA_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.kstudio.agenda"
        minSdk = 26          // Android 8.0+（向下兼容；悬浮窗 TYPE_APPLICATION_OVERLAY 即 API 26 起）
        targetSdk = 35
        // 版本命名逻辑（2026.9 起）：年份.月份 v序号；versionCode = 年×10000 + 月×100 + 序号
        // - 正式版：无后缀，如 `2026.9 v2.0.7`；补丁：`2026.9 v2.0.7.1`
        // - 预览版：用户要求加功能、能跑但用户还没自己检查 → 版本号后加 `(preN)`（N 递增），如 `2026.9 v2.0.8(pre1)`
        // - 预览版经用户检查、修完 bug → 转正式版，**去掉后缀**（`2026.9 v2.0.8`）
        // - 修订版：正式版之后用户又发现需要改的地方 → 版本号后加 `(revN)`，如 `2026.9 v2.0.8(rev1)`
        //   注意：(revN) 只接在版本号后，**不叠加在 (preN) 之后**；预览阶段自己发现的修复留在同一个 (preN) 里
        // - versionCode 只增不减：2026.10 预览版已用到 20261003（v1(pre3)），
        //   转正式版继续 +1 → 20261004
        versionCode = 20261004
        versionName = "2026.10 v1"
        // WebView 相关无 NDK 需求
    }

    buildTypes {
        release {
            // 性能优先：R8 代码压缩/混淆 + 资源收缩
            // 签名：有本地私钥用正式签名，否则退回 debug 签名（可直接安装并覆盖调试包）
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (useReleaseSigning) {
                logger.lifecycle("[KAgenda] release 使用 local.properties 里的正式签名")
                signingConfigs.getByName("kagendaRelease")
            } else {
                logger.lifecycle("[KAgenda] 未配置正式签名（local.properties 缺少 KAGENDA_*），本次 release 使用 debug 签名")
                signingConfigs.getByName("debug")
            }
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
