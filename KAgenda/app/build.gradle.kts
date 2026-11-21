import java.util.Properties
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.android.apksig.ApkSigner

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
    dynamicFeatures += setOf(":engine_ocr", ":engine_pdf", ":engine_ai")
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
        // The base configuration owns ABI filtering for all dynamic feature APKs.
        providers.gradleProperty("kagenda.engineAbi").orNull?.let { ndk { abiFilters += it } }
        testInstrumentationRunner = providers.gradleProperty("kagenda.testRunner")
            .getOrElse("com.kstudio.agenda.LocalAiDeviceTestRunner")
        // 版本命名逻辑（2026.9 起）：年份.月份 v序号；versionCode = 年×10000 + 月×100 + 序号
        // - 正式版：无后缀，如 `2026.9 v2.0.7`；补丁：`2026.9 v2.0.7.1`
        // - 预览版：用户要求加功能、能跑但用户还没自己检查 → 版本号后加 `(preN)`（N 递增），如 `2026.9 v2.0.8(pre1)`
        // - 预览版经用户检查、修完 bug → 转正式版，**去掉后缀**（`2026.9 v2.0.8`）
        // - 修订版：正式版之后用户又发现需要改的地方 → 版本号后加 `(revN)`，如 `2026.9 v2.0.8(rev1)`
        //   注意：(revN) 只接在版本号后，**不叠加在 (preN) 之后**；预览阶段自己发现的修复留在同一个 (preN) 里
        // - versionCode 只增不减：2026.10 预览版已用到 20261003（v1(pre3)），
        //   正式版 20261004（v1）→ 修订版 20261005 → v1.1 继续 +1。
        versionCode = 20261103
        versionName = "2026.11 v1.1(pre1)"

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

    testOptions {
        unitTests.all {
            it.systemProperty("kagenda.assets", project.file("src/main/assets").absolutePath)
            it.systemProperty("kagenda.runners", project.layout.buildDirectory.dir("reports/warehouse-runners").get().asFile.absolutePath)
        }
    }
    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("generated/engine-test-assets"))

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
    // ML Kit's optional split manifest references this small shared version resource.
    // Keeping the basement resource in the base does not require Google services on the device.
    implementation("com.google.android.gms:play-services-basement:18.4.0")
    // Previously scheduled SDK callbacks may outlive an optional split across app upgrades.
    // Keep their small implementation in the base; OCR code, models and native libraries stay optional.
    implementation("com.google.android.datatransport:transport-runtime:2.2.6")
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
    implementation(libs.haze)
    debugImplementation(libs.androidx.ui.tooling)

    // 设置存储
    implementation(libs.androidx.datastore.preferences)

    // 后台定时任务（课表提醒兜底调度 / 自动刷新）
    implementation(libs.androidx.work.runtime.ktx)

    // 单元测试（解析器等纯 JVM 逻辑）
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.google.code.gson:gson:2.11.0")
    androidTestImplementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Produce per-ABI optional APKs from the same universal build and R8 mapping.
// Signing credentials stay inside Gradle's existing signing configuration.
tasks.register("stageOptionalEngineRelease") {
    dependsOn("assembleRelease", ":engine_ocr:assembleRelease", ":engine_pdf:assembleRelease", ":engine_ai:assembleRelease")
    doLast {
        require(!providers.gradleProperty("kagenda.engineAbi").isPresent) { "Build a universal base for publication" }
        require(useReleaseSigning) { "Formal signing configuration required" }
        val signing = android.signingConfigs.getByName("kagendaRelease")
        val store = KeyStore.getInstance(signing.storeType ?: KeyStore.getDefaultType())
        signing.storeFile!!.inputStream().use { store.load(it, signing.storePassword!!.toCharArray()) }
        val key = store.getKey(signing.keyAlias, signing.keyPassword!!.toCharArray()) as PrivateKey
        val certificates = store.getCertificateChain(signing.keyAlias).map { it as X509Certificate }
        val signer = ApkSigner.SignerConfig.Builder("KAgenda", key, certificates).build()
        val version = android.defaultConfig.versionCode!!
        val output = layout.buildDirectory.dir("engine-release-staged/$version").get().asFile.apply { mkdirs() }
        val zipalign = File(android.sdkDirectory, "build-tools/${android.buildToolsVersion}/" + if (System.getProperty("os.name").startsWith("Windows")) "zipalign.exe" else "zipalign")
        for (engine in listOf("ocr", "pdf", "ai")) {
            val folder = rootProject.file("engine-$engine/build/outputs/apk/release")
            val apk = folder.listFiles()!!.single { it.extension == "apk" }
            val abis = when (engine) {
                "ocr" -> listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                "ai" -> listOf("arm64-v8a", "x86_64")
                else -> listOf("any")
            }
            for (abi in abis) {
                val target = File(output, "engine-$engine-$version-$abi.apk")
                if (abi == "any") { apk.copyTo(target, overwrite = true); continue }
                val unsigned = File(output, "unsigned-$engine-$abi.apk")
                val aligned = File(output, "aligned-$engine-$abi.apk")
                ZipFile(apk).use { input ->
                    ZipOutputStream(unsigned.outputStream().buffered()).use { zip ->
                        for (entry in input.entries()) {
                            if (entry.name.startsWith("lib/") && entry.name.split('/').getOrNull(1) != abi) continue
                            if (Regex("META-INF/.*\\.(SF|RSA|DSA|EC)", RegexOption.IGNORE_CASE).matches(entry.name) || entry.name == "META-INF/MANIFEST.MF") continue
                            val copy = ZipEntry(entry.name).apply {
                                method = entry.method; time = entry.time
                                if (method == ZipEntry.STORED) { size = entry.size; compressedSize = entry.size; crc = entry.crc }
                            }
                            zip.putNextEntry(copy)
                            if (!entry.isDirectory) input.getInputStream(entry).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                val process = ProcessBuilder(zipalign.absolutePath, "-P", "16", "-f", "4", unsigned.absolutePath, aligned.absolutePath).inheritIO().start()
                check(process.waitFor() == 0) { "Component ZIP alignment failed" }
                ApkSigner.Builder(listOf(signer)).setInputApk(aligned).setOutputApk(target)
                    .setMinSdkVersion(26).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign()
                check(unsigned.delete()); check(aligned.delete())
            }
        }
        logger.lifecycle("Signed component APKs staged in $output")
    }
}
