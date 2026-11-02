package com.kstudio.agenda.data

import android.content.Context
import android.os.Process
import java.io.File
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

data class LocalAiModel(
    val id: String,
    val name: String,
    val repository: String,
    val revision: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String,
) {
    val url: String get() = "https://huggingface.co/$repository/resolve/$revision/$fileName"
}

/** Only metadata is bundled; weights live in noBackupFilesDir after an explicit download. */
object LocalAiModels {
    val all = listOf(
        LocalAiModel("qwen3-0.6b-q4", "Qwen3 · 0.6B", "unsloth/Qwen3-0.6B-GGUF",
            "50968a4468ef4233ed78cd7c3de230dd1d61a56b", "Qwen3-0.6B-Q4_K_M.gguf", 396705472L,
            "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a"),
        LocalAiModel("qwen3-1.7b-q4", "Qwen3 · 1.7B", "unsloth/Qwen3-1.7B-GGUF",
            "d7f544eead698dbd1f15126ef60b45a1e1933222", "Qwen3-1.7B-Q4_K_M.gguf", 1107409472L,
            "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897"),
    )
    val defaultId = all.first().id
    fun find(id: String): LocalAiModel? = all.find { it.id == id }
    fun supported(): Boolean = Process.is64Bit()
    fun directory(context: Context) = File(context.noBackupFilesDir, "local-ai").apply { mkdirs() }
    fun file(context: Context, model: LocalAiModel) = File(directory(context), model.fileName)
    fun partial(context: Context, model: LocalAiModel) = File(directory(context), model.fileName + ".part")
    fun receipt(context: Context, model: LocalAiModel) = File(directory(context), model.fileName + ".verified")
    fun installed(context: Context, model: LocalAiModel): Boolean =
        file(context, model).length() == model.bytes &&
            runCatching { receipt(context, model).readText() == model.sha256 }.getOrDefault(false)
}

internal object ModelFileLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun forModel(id: String): Mutex = locks.getOrPut(id) { Mutex() }
}
