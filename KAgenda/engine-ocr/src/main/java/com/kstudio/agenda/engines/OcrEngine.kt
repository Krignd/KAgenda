package com.kstudio.agenda.engines

import com.kstudio.agenda.data.BoundedInput
import com.kstudio.agenda.data.OcrTimetableLayout
import com.kstudio.agenda.data.engines.OcrEngineContract

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.kstudio.agenda.i18n.ImportText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine : OcrEngineContract {
    override suspend fun extract(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        com.google.mlkit.common.MlKit.initialize(context.applicationContext)
        val bytes = context.contentResolver.openInputStream(uri)?.use { BoundedInput.read(it, 16 * 1024 * 1024) }
            ?: error(ImportText.failed)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { ImportText.noText }
        val opts = BitmapFactory.Options().apply {
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 4096)
                inSampleSize = inSampleSize.coerceAtLeast(1) * 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: error(ImportText.noText)
        val rotation = runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val degrees = when (rotation) { 3 -> 180; 6 -> 90; 8 -> 270; else -> 0 }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, degrees)).addOnCompleteListener { task ->
                bitmap.recycle()
                recognizer.close()
                if (!continuation.isActive) return@addOnCompleteListener
                if (!task.isSuccessful) continuation.resumeWithException(task.exception ?: IllegalStateException(ImportText.failed))
                else {
                    val lines = task.result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        line.boundingBox?.let { OcrTimetableLayout.Line(line.text, it.left, it.top, it.right, it.bottom) }
                    }
                    val text = OcrTimetableLayout.extract(lines)
                    if (text.isBlank()) continuation.resumeWithException(IllegalStateException(ImportText.noText))
                    else continuation.resume(text)
                }
            }
        }
    }
}
