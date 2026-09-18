package com.kstudio.agenda.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 将 Bitmap 以 PNG 形式保存进系统相册。
 * - Android 10+：MediaStore（无需存储权限）
 * - Android 8.0–9：写入公共 Pictures 目录 + 媒体扫描登记（需 WRITE_EXTERNAL_STORAGE）
 * 保存位置：相册 / Pictures/K日程
 */
object ImageExporter {

    private const val ALBUM = "K日程"

    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? =
        if (Build.VERSION.SDK_INT >= 29) {
            saveViaMediaStore(context, bitmap, fileName)
        } else {
            saveLegacy(context, bitmap, fileName)
        }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IOException("compress failed")
                }
            } ?: throw IOException("no output stream")

            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** Android 8.0–9 回退路径：直接写入公共相册目录并通知媒体库扫描（无权限或失败时返回 null） */
    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap, fileName: String): Uri? = try {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!dir.exists() && !dir.mkdirs()) throw IOException("mkdir failed")
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("compress failed")
            }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/png"),
            null,
        )
        Uri.fromFile(file)
    } catch (_: Throwable) {
        null
    }
}
