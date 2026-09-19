package com.kstudio.agenda.util

import android.content.Context
import android.os.Build
import com.kstudio.agenda.BuildConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 运行日志系统（2026.9 v2 全面优化）：
 * - 每次启动一个会话文件 session-yyyyMMdd-HHmmss.log（保留最近 6 个，单文件 2MB 上限）；
 * - 后台线程异步落盘：调用方不做文件 IO，高频日志不阻塞界面/同步线程；
 * - 敏感信息双重遮蔽：精确串（如当前密码）+ 正则兜底（ticket/CASTGC/session/token/cookie 等）；
 * - 会话头部自动记录版本与设备信息；崩溃时由全局异常处理器同步落盘；
 * - 内存保留最近 800 行供界面展示；设置页可查看（按级别筛选）/ 分享 / 清空。
 */
object AppLog {

    private const val MAX_LINE_LEN = 4000
    private const val MAX_BUFFER_LINES = 800
    private const val KEEP_FILES = 6
    private const val MAX_FILE_BYTES = 2 * 1024 * 1024L

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val fileNameFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    private val lock = Any()
    private val buffer = ArrayDeque<String>()      // 内存缓冲（界面展示用）
    private val pending = LinkedBlockingQueue<String>()  // 待落盘队列（后台线程阻塞式消费）

    @Volatile
    private var currentFile: File? = null

    @Volatile
    private var sizeCapped = false

    private var writer: Thread? = null

    /**
     * 需要遮蔽的敏感字符串（如密码）。
     * 注册后，所有写入日志的内容中出现的敏感串都会被替换为 ******，
     * 防止页面控制台/异常信息等意外把密码带进日志文件（日志可在设置页分享）。
     */
    private val secrets = java.util.concurrent.CopyOnWriteArrayList<String>()

    @Volatile
    private var logDir: File? = null

    /** 注册敏感字符串（如密码），仅内存驻留，不落盘 */
    fun registerSecret(secret: String) {
        if (secret.length < 3) return
        if (!secrets.contains(secret)) secrets.add(secret)
    }

    /** 票据 / 会话 / 令牌 / API Key 类字段的兜底遮蔽（正则） */
    private val SECRET_PATTERNS = listOf(
        Regex(
            "(?i)(ticket|castgc|jsessionid|sessionid|session|token|password|passwd|pwd|authorization|cookie)" +
                "\\s*[=:]\\s*([^&;\\s\"'}]+)",
        ),
        // DeepSeek 等 API Key（sk- 开头）：防止 Key 意外进入可分享的日志
        Regex("sk-[A-Za-z0-9_\\-]{8,}"),
    )

    private fun redact(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        // 1) 精确遮蔽（如当前登录密码的原文）
        for (s in secrets) {
            if (s.isNotEmpty() && result.contains(s)) {
                result = result.replace(s, "******")
            }
        }
        // 快速短路：绝大多数日志行不含敏感特征（无 = / : / sk-），直接返回，跳过后续正则替换
        if (result.indexOf("sk-") < 0 && result.indexOf('=') < 0 && result.indexOf(':') < 0) {
            return result
        }
        // 2) 常规敏感字段兜底（ticket=、CASTGC=、token:、sk- API Key 等）
        for (p in SECRET_PATTERNS) {
            result = p.replace(result) { m ->
                val groups = m.groupValues
                if (groups.size > 1 && groups[1].isNotEmpty()) groups[1] + "=******" else "******"
            }
        }
        return result
    }

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            // 仅保留最近 KEEP_FILES 个会话文件
            dir.listFiles()
                ?.sortedByDescending { it.name }
                ?.drop(KEEP_FILES - 1)
                ?.forEach { runCatching { it.delete() } }
            currentFile = File(dir, "session-${LocalDateTime.now().format(fileNameFmt)}.log")
            sizeCapped = false
            logDir = dir
        }
        startWriter()
        i("App", "================ 会话开始 ================")
        i(
            "App",
            "版本=${BuildConfig.VERSION_NAME}(vc=${BuildConfig.VERSION_CODE})；" +
                "设备=${Build.MANUFACTURER} ${Build.MODEL}；Android ${Build.VERSION.RELEASE}(SDK ${Build.VERSION.SDK_INT})",
        )
    }

    /** 启动后台写线程：调用方不再阻塞在文件 IO 上（高频日志不卡界面/同步线程） */
    private fun startWriter() {
        if (writer?.isAlive == true) return
        writer = thread(name = "app-log-writer", isDaemon = true) {
            while (true) {
                // 阻塞等待新日志（1 秒超时兜底），替代原来的 250ms 空轮询常驻唤醒
                val first = try {
                    pending.poll(1, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    null
                } ?: continue
                val rest = ArrayList<String>()
                pending.drainTo(rest)
                val batch = StringBuilder(first)
                rest.forEach { batch.append(it) }
                synchronized(lock) {
                    currentFile?.let { appendToFileLocked(it, batch.toString()) }
                }
            }
        }
    }

    /** 立即同步落盘（崩溃兕底等必须确保日志不丢的场景） */
    fun flushNow() {
        drainQueueToFile()
    }

    /** 把待写队列全部落盘（跨线程可调用；写文件在 lock 内进行） */
    private fun drainQueueToFile() {
        val rest = ArrayList<String>()
        pending.drainTo(rest)
        if (rest.isEmpty()) return
        synchronized(lock) {
            currentFile?.let { appendToFileLocked(it, rest.joinToString("")) }
        }
    }

    /** 追加文本到日志文件（须在 lock 内调用）；达到大小上限后仅写一条提示并停止写入 */
    private fun appendToFileLocked(file: File, text: String) {
        if (sizeCapped || text.isEmpty()) return
        runCatching {
            if (file.length() > MAX_FILE_BYTES) {
                sizeCapped = true
                file.appendText(
                    "${LocalDateTime.now().format(timeFmt)} W/App: （日志已达大小上限，后续内容不再写入本文件）\n",
                    Charsets.UTF_8,
                )
            } else {
                file.appendText(text, Charsets.UTF_8)
            }
        }
    }

    fun d(tag: String, message: String) = write("D", tag, message)

    fun i(tag: String, message: String) = write("I", tag, message)

    fun w(tag: String, message: String) = write("W", tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write("E", tag, message + (throwable?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))

    private fun write(level: String, tag: String, message: String) {
        // 先遮蔽敏感串，再替换换行截断，避免密码意外落入日志文件
        val line = "${LocalDateTime.now().format(timeFmt)} $level/$tag: " +
            redact(message).replace('\n', ' ').take(MAX_LINE_LEN)
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_BUFFER_LINES) buffer.removeFirst()
            if (currentFile == null) return
            pending.add(line + "\n")
        }
    }

    /** 读取全部日志（按日期升序拼接；读取前先落盘未刷新的队列，保证完整） */
    fun readAll(): String = synchronized(lock) {
        try {
            drainQueueToFile()
            val dir = logDir ?: return ""
            val files = dir.listFiles()?.sortedBy { it.name } ?: return ""
            files.joinToString("\n") { file ->
                runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
            }
        } catch (t: Throwable) {
            "读取日志失败: ${t.message}"
        }
    }

    /** 最近日志（内存缓冲） */
    fun recent(): String = synchronized(lock) { buffer.joinToString("\n") }

    fun clear() {
        synchronized(lock) {
            logDir?.listFiles()?.forEach { runCatching { it.delete() } }
            buffer.clear()
            pending.clear()
            sizeCapped = false
            currentFile = logDir?.let { File(it, "session-${LocalDateTime.now().format(fileNameFmt)}.log") }
        }
        i("App", "日志已清空（新会话文件）")
    }
}
