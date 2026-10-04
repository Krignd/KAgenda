package com.kstudio.agenda.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.kstudio.agenda.util.AppLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份的「默认位置」：公开的 `下载/KAgenda/` 目录（注意：注释里不要写连续的 / 星号，会被当成嵌套注释）。
 *
 * 为什么不用 App 私有目录：私有目录会随卸载一起删掉，而备份的主要用途恰恰是
 * 「卸载重装后恢复」，必须放在卸载不会清的公共目录里；同时用户能自己用文件管理器看到。
 *
 * - Android 10+（API 29+）：走 MediaStore（不需要任何权限）；
 * - Android 8/9（API 26–28）：直接写公共下载目录，需要 WRITE_EXTERNAL_STORAGE
 *   （由界面层在点导出时才申请，遵循本 App「用到才申请」的约定）。
 *
 * 除默认位置外，界面仍提供「选择其他位置…」走系统文件选择器（SAF）。
 */
object BackupStore {

    private const val TAG = "BackupStore"

    /** 公共下载目录下的子目录名 */
    private const val DIR_NAME = "KAgenda"

    /** 默认文件名前缀（与界面上的备份名一致，便于辨认） */
    private const val PREFIX = "KAgenda_backup_"

    data class Entry(
        /** 读取用的 Uri（content:// 或 file://，[BackupManager.importFrom] 两种都支持） */
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val modifiedAtMillis: Long,
        /** 给用户看的路径描述 */
        val location: String,
    )

    /** 默认文件名：KAgenda_backup_2026-10-04.json（同一天重复导出时追加时间，避免互相覆盖） */
    fun defaultFileName(now: Long = System.currentTimeMillis()): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val time = SimpleDateFormat("HHmmss", Locale.US).format(Date(now))
        return "$PREFIX$day" + "_" + time + ".json"
    }

    /** 默认位置的展示文案（设置页提示用） */
    fun defaultLocationLabel(): String =
        Environment.DIRECTORY_DOWNLOADS + "/" + DIR_NAME

    /** 是否需要存储权限（只有 API 26–28 写公共目录才需要） */
    fun needsPermission(): Boolean = Build.VERSION.SDK_INT < 29

    /** 把备份文本写入默认位置；返回写入的文件信息，失败返回 null */
    fun writeToDefault(context: Context, text: String): Entry? {
        val name = defaultFileName()
        val bytes = text.toByteArray(Charsets.UTF_8)
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + DIR_NAME,
                    )
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore 插入失败")
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("输出流为空")
                Entry(uri, name, bytes.size.toLong(), System.currentTimeMillis(), defaultLocationLabel())
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    DIR_NAME,
                )
                if (!dir.exists() && !dir.mkdirs()) error("无法创建目录 ${dir.path}")
                val file = File(dir, name)
                file.writeBytes(bytes)
                Entry(Uri.fromFile(file), name, bytes.size.toLong(), file.lastModified(), defaultLocationLabel())
            }
        }.onFailure { AppLog.e(TAG, "写入默认备份目录失败", it) }.getOrNull()
    }

    /**
     * 列出默认位置里已有的备份（按时间倒序，最新的在前）。
     * 没有任何备份（或没有权限）时返回空列表 —— 界面据此决定是「直接弹文件选择器」
     * 还是「先问用户要不要用默认位置的这份」。
     */
    fun listDefault(context: Context): List<Entry> = runCatching {
        if (Build.VERSION.SDK_INT >= 29) listViaMediaStore(context) else listViaFile()
    }.onFailure { AppLog.e(TAG, "读取默认备份目录失败", it) }.getOrDefault(emptyList())

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listViaMediaStore(context: Context): List<Entry> {
        val out = mutableListOf<Entry>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        // 只查本 App 自己插入的媒体（不需要任何权限）
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("%$DIR_NAME%"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val timeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                if (!name.startsWith(PREFIX) || !name.endsWith(".json")) continue
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    c.getLong(idCol),
                )
                out.add(
                    Entry(
                        uri = uri,
                        name = name,
                        sizeBytes = c.getLong(sizeCol),
                        modifiedAtMillis = c.getLong(timeCol) * 1000L,
                        location = defaultLocationLabel(),
                    )
                )
            }
        }
        return out
    }

    private fun listViaFile(): List<Entry> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DIR_NAME,
        )
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(".json") }
            .sortedByDescending { it.lastModified() }
            .map {
                Entry(
                    uri = Uri.fromFile(it),
                    name = it.name,
                    sizeBytes = it.length(),
                    modifiedAtMillis = it.lastModified(),
                    location = defaultLocationLabel(),
                )
            }
    }
}
