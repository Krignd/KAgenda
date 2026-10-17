package com.kstudio.agenda.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 完整写入临时文件后替换，避免进程退出/磁盘写满时留下半截 JSON。 */
internal object AtomicFileStore {
    fun write(file: File, text: String) {
        val parent = file.absoluteFile.parentFile!!
        check(parent.isDirectory || parent.mkdirs())
        val temp = File.createTempFile(file.name, ".tmp", parent)
        try {
            temp.outputStream().use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            replace(temp, file)
        } finally { temp.delete() }
    }

    fun replace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
