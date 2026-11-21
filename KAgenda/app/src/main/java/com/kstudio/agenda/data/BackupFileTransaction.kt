package com.kstudio.agenda.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 先暂存全部文件；文件或设置写入失败时恢复原件，避免仅恢复了一半。 */
internal object BackupFileTransaction {
    private data class Entry(val target: File, val staged: File, val previous: File?)

    suspend fun restore(
        root: File,
        files: Map<String, String>,
        photos: Map<String, File> = emptyMap(),
        saveSettings: suspend () -> Unit,
    ) {
        val entries = mutableListOf<Entry>()
        val applied = mutableListOf<Entry>()
        val preserve = mutableSetOf<File>()
        try {
            for (name in photos.keys + files.keys) {
                val target = File(root, name).canonicalFile
                require(target.toPath().startsWith(root.canonicalFile.toPath()) && target != root.canonicalFile)
                val parent = target.parentFile!!
                check(parent.isDirectory || parent.mkdirs())
                check(!target.exists() || target.isFile)
                val staged = File.createTempFile("restore-", ".new", parent)
                var previous: File? = null
                try {
                    val photo = photos[name]
                    if (photo != null) Files.copy(photo.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    else AtomicFileStore.write(staged, files.getValue(name))
                    if (target.exists()) {
                        previous = File.createTempFile("restore-", ".old", parent)
                        Files.copy(target.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    entries += Entry(target, staged, previous)
                } catch (e: Throwable) {
                    staged.delete(); previous?.delete()
                    throw e
                }
            }
            for (entry in entries) {
                AtomicFileStore.replace(entry.staged, entry.target)
                applied += entry
            }
            saveSettings()
        } catch (e: Throwable) {
            for (entry in applied.asReversed()) {
                try {
                    if (entry.previous != null) AtomicFileStore.replace(entry.previous, entry.target)
                    else check(!entry.target.exists() || entry.target.delete())
                } catch (rollback: Throwable) {
                    entry.previous?.let { preserve += it }
                    e.addSuppressed(rollback)
                }
            }
            throw e
        } finally {
            entries.forEach { it.staged.delete(); if (it.previous !in preserve) it.previous?.delete() }
        }
    }
}
