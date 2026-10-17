package com.kstudio.agenda.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

object SchoolIconStore {
    val revision = MutableStateFlow(0)
    private fun file(context: Context, id: String) = File(context.filesDir, "school_icons/" + id.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".png")
    fun load(context: Context, id: String): Bitmap? = runCatching { BitmapFactory.decodeFile(file(context, id).absolutePath) }.getOrNull()
    @Synchronized fun save(context: Context, id: String, bitmap: Bitmap) {
        val f = file(context, id); f.parentFile?.mkdirs()
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        revision.value++
    }
}
