package com.kstudio.agenda.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kstudio.agenda.BuildConfig
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 更新站点返回的版本信息（`latest.json`）。字段说明见「相关文件\更新站点部署指南.md」 */
data class UpdateInfo(
    /** 新版本的 versionCode（与本地 [BuildConfig.VERSION_CODE] 比较，严格大于才算有新版本） */
    val versionCode: Int,
    /** 新版本的展示名，如 `2026.10 v1.1` */
    val versionName: String,
    /** APK 下载地址（必须 https） */
    val apkUrl: String,
    /** APK 字节数（0 = 站点未提供，界面显示“大小未知”） */
    val sizeBytes: Long,
    /** APK 的 sha256（空 = 不校验） */
    val sha256: String,
    /** 更新说明（可空） */
    val notes: String,
)

/** 更新状态（顶部小字与「设置 → 关于」共用同一份状态） */
sealed interface UpdateUi {
    /** 尚未检查 */
    data object Idle : UpdateUi

    data object Checking : UpdateUi

    /** 已是最新版本 */
    data object UpToDate : UpdateUi

    /** 有新版本，尚未下载 */
    data class Available(val info: UpdateInfo) : UpdateUi

    /** 正在下载 */
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateUi

    /** 已下载完成、等待安装 */
    data class Downloaded(val info: UpdateInfo, val fileBytes: Long) : UpdateUi

    /** 出错（仅手动检查/下载时展示，自动检查静默失败） */
    data class Error(val message: String) : UpdateUi
}

/** [AppUpdater.install] 的结果 */
sealed interface InstallStart {
    /** 已拉起系统安装器 */
    data object Launched : InstallStart

    /** 缺少「安装未知应用」权限，需引导用户去系统设置开启 */
    data object NeedPermission : InstallStart

    /** 更新包不可用（文件缺失/被清理），需要重新下载 */
    data object Unavailable : InstallStart
}

/**
 * 应用内自动更新（检查 → 下载 → 安装）。
 *
 * ## 站点约定（部署方法见「相关文件\更新站点部署指南.md」）
 * - 版本清单：`https://20071009.xyz/KAgenda/latest.json`（字段见 [UpdateInfo]）
 * - APK：清单里的 `apkUrl`（必须 https，建议同一域名；APK 必须用与已装应用**相同的签名**）
 *
 * ## 设计要点
 * - 只用 [HttpURLConnection]，不引第三方网络库；
 * - 下载到 `filesDir/updates/`，通过已有 FileProvider 交给系统安装器安装
 *   （需要 `REQUEST_INSTALL_PACKAGES` 声明 + 用户在系统设置里允许本应用安装应用）；
 * - 自动检查按 [AUTO_CHECK_INTERVAL_MS] 节流（时间戳存 SharedPreferences，跨进程生效）；
 *   自动检查失败**保持静默**（只在日志里记），手动检查才把错误显示到界面；
 * - 已下载但未安装的包在应用重启后仍能被识别（按文件名里的 versionCode 判断），
 *   并且一旦当前版本已不低于它就会被清理掉。
 */
object AppUpdater {

    /** 版本清单地址（部署指南里要求站点提供这个路径） */
    const val MANIFEST_URL = "https://20071009.xyz/KAgenda/latest.json"

    private const val TAG = "Update"

    /** 自动检查的最小间隔（6 小时） */
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    /** 下载目录名（filesDir 下；已加入 file_paths.xml 供 FileProvider 分享） */
    private const val DIR_NAME = "updates"

    private const val PREF_NAME = "app_updater"
    private const val KEY_LAST_AUTO_CHECK = "last_auto_check_at"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val state: StateFlow<UpdateUi> = _state.asStateFlow()

    private var job: Job? = null

    // ---------------------------------------------------------------- 检查

    /**
     * 检查更新。
     *
     * @param auto true = 应用启动时的自动检查：按 [AUTO_CHECK_INTERVAL_MS] 节流，
     *             失败不打扰用户；false = 用户手动点击，每次都真正请求并把错误显示出来。
     */
    fun check(context: Context, auto: Boolean = false) {
        val appContext = context.applicationContext
        if (auto) {
            val last = prefs(appContext).getLong(KEY_LAST_AUTO_CHECK, 0L)
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        }
        // 正在下载/已下载完成时不要用「检查结果」覆盖当前状态（否则下载进度会丢）
        val cur = _state.value
        if (cur is UpdateUi.Downloading || cur is UpdateUi.Downloaded) return

        job?.cancel()
        _state.value = UpdateUi.Checking
        job = scope.launch {
            try {
                if (auto) prefs(appContext).edit().putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).apply()
                val info = fetchManifest()
                if (info.versionCode <= BuildConfig.VERSION_CODE) {
                    cleanDownloaded(appContext, keepVersionCode = 0)
                    _state.value = UpdateUi.UpToDate
                    AppLog.i(TAG, "已是最新版本（本地 ${BuildConfig.VERSION_CODE}，站点 ${info.versionCode}）")
                    return@launch
                }
                // 已经下载过同一个新版本 → 直接进入「待安装」
                val existing = downloadedFile(appContext, info.versionCode)
                cleanDownloaded(appContext, keepVersionCode = info.versionCode)
                if (existing != null) {
                    _state.value = UpdateUi.Downloaded(info, existing.length())
                    AppLog.i(TAG, "发现已下载的更新包：${existing.name}（${formatBytes(existing.length())}）")
                } else {
                    _state.value = UpdateUi.Available(info)
                    AppLog.i(TAG, "发现新版本：${info.versionName}(${info.versionCode}) ${formatBytes(info.sizeBytes)}")
                }
            } catch (e: Throwable) {
                val msg = e.message ?: e.javaClass.simpleName
                AppLog.w(TAG, "检查更新失败：$msg")
                _state.value = if (auto) UpdateUi.Idle else UpdateUi.Error(msg)
            }
        }
    }

    /** 下载最新版本的 APK（需先处于 [UpdateUi.Available]） */
    fun download(context: Context) {
        val appContext = context.applicationContext
        val info = (_state.value as? UpdateUi.Available)?.info ?: return
        job?.cancel()
        _state.value = UpdateUi.Downloading(info, 0)
        job = scope.launch {
            val dir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
            // 先写 .part，下完再改名：避免半截文件被当成「已下载」
            val part = File(dir, "kagenda-${info.versionCode}.apk.part")
            val target = downloadedFile(appContext, info.versionCode)!!
            try {
                var read = 0L
                val conn = open(info.apkUrl)
                try {
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var lastPercent = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                read += n
                                val percent = if (total > 0) {
                                    ((read * 100) / total).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    _state.value = UpdateUi.Downloading(info, percent)
                                }
                            }
                            out.flush()
                        }
                    }
                } finally {
                    runCatching { conn.disconnect() }
                }

                // 完整性校验：大小（站点给了就必须对得上）+ sha256（站点给了就必须对得上）
                if (info.sizeBytes > 0 && read != info.sizeBytes) {
                    throw IllegalStateException("下载不完整（$read/${info.sizeBytes} 字节）")
                }
                if (info.sha256.isNotBlank()) {
                    val actual = sha256Of(part)
                    if (!actual.equals(info.sha256.trim(), ignoreCase = true)) {
                        throw IllegalStateException("校验失败（sha256 不一致）")
                    }
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) throw IllegalStateException("无法保存下载文件")
                cleanDownloaded(appContext, keepVersionCode = info.versionCode)
                _state.value = UpdateUi.Downloaded(info, target.length())
                AppLog.i(TAG, "更新包下载完成：${target.name}（${formatBytes(target.length())}）")
            } catch (e: Throwable) {
                runCatching { part.delete() }
                val msg = e.message ?: e.javaClass.simpleName
                AppLog.w(TAG, "下载更新失败：$msg")
                _state.value = UpdateUi.Error(msg)
            }
        }
    }

    // ---------------------------------------------------------------- 安装

    /**
     * 拉起系统安装器安装已下载的更新包。
     *
     * 必须先声明 `REQUEST_INSTALL_PACKAGES`，且用户在系统设置里允许本应用「安装未知应用」；
     * 未允许时返回 [InstallStart.NeedPermission]，由界面引导用户去开启。
     */
    fun install(context: Context): InstallStart {
        val appContext = context.applicationContext
        val downloaded = _state.value as? UpdateUi.Downloaded ?: return InstallStart.Unavailable
        val file = downloadedFile(appContext, downloaded.info.versionCode) ?: return InstallStart.Unavailable
        if (!file.exists() || file.length() <= 0L) {
            _state.value = UpdateUi.Available(downloaded.info)
            return InstallStart.Unavailable
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            AppLog.w(TAG, "缺少「安装未知应用」权限，引导用户去系统设置")
            return InstallStart.NeedPermission
        }
        return try {
            val uri: Uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            AppLog.i(TAG, "已拉起系统安装器：${file.name}")
            InstallStart.Launched
        } catch (e: Throwable) {
            AppLog.w(TAG, "拉起安装器失败：${e.message ?: e.javaClass.simpleName}")
            InstallStart.Unavailable
        }
    }

    /** 系统是否允许本应用安装其他应用（Android 8.0+ 的应用内更新安装需要） */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 打开系统「安装未知应用」授权页（本应用） */
    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            AppLog.w(TAG, "打开「安装未知应用」设置失败：${it.message ?: it.javaClass.simpleName}")
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 人类可读的字节数（如 `2.18 MB`）；0 或负数返回空串 */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        return when {
            bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
            bytes >= 1024L * 1024 -> "%.2f MB".format(bytes / 1024.0 / 1024)
            bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 下载目标文件（文件名里带 versionCode，便于重启后识别） */
    private fun downloadedFile(context: Context, versionCode: Int): File? {
        if (versionCode <= 0) return null
        val dir = File(context.filesDir, DIR_NAME)
        return File(dir, "kagenda-$versionCode.apk")
    }

    /** 清掉除 [keepVersionCode] 之外的旧下载，避免长期占用空间 */
    private fun cleanDownloaded(context: Context, keepVersionCode: Int) {
        runCatching {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.isDirectory) return
            dir.listFiles()?.forEach { f ->
                val v = f.name.removePrefix("kagenda-").substringBefore('.').toIntOrNull()
                if (v == null || v != keepVersionCode) f.delete()
            }
        }
    }

    private fun fetchManifest(): UpdateInfo {
        val conn = open(MANIFEST_URL)
        val text = try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            runCatching { conn.disconnect() }
        }
        val o = JSONObject(text)
        val apkUrl = o.optString("apkUrl").trim()
        require(apkUrl.startsWith("https://")) { "latest.json 的 apkUrl 必须是 https 地址" }
        val code = o.optInt("versionCode", 0)
        require(code > 0) { "latest.json 缺少合法的 versionCode" }
        return UpdateInfo(
            versionCode = code,
            versionName = o.optString("versionName").trim().ifBlank { "code $code" },
            apkUrl = apkUrl,
            sizeBytes = o.optLong("sizeBytes", 0L),
            sha256 = o.optString("sha256").trim(),
            notes = o.optString("notes").trim(),
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, application/octet-stream, */*")
            // 便于站点排查匿名请求量（不含任何个人信息）
            setRequestProperty("User-Agent", "KAgenda/${BuildConfig.VERSION_NAME} (Android)")
        }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
