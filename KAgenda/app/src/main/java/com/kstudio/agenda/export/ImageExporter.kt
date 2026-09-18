package com.kstudio.agenda.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException

/**
 * 将 Bitmap 以 PNG 形式保存进系统相册（MediaStore，Android 10+ 无需存储权限）。
 * 保存位置：相册 / Pictures/K日程
 */
object ImageExporter {

    private const val ALBUM = "K日程"

    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
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
}
