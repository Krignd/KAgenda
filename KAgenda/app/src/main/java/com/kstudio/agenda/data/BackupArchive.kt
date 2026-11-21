package com.kstudio.agenda.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object BackupArchive {
    private const val METADATA = "backup.json"
    private const val MAX_METADATA_BYTES = 20 * 1024 * 1024
    private const val MAX_PHOTO_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_PHOTO_BYTES = 512L * 1024 * 1024
    private val photoEntry = Regex("lesson-photos/([0-9a-f-]{36})\\.jpg")

    data class Contents(val metadata: String, val photos: Map<String, File>, val zipped: Boolean)

    fun write(output: OutputStream, metadata: String, photos: Map<String, File>) {
        val bytes = metadata.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_METADATA_BYTES) { "Backup metadata is too large" }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(METADATA))
            zip.write(bytes)
            zip.closeEntry()
            zip.setLevel(Deflater.NO_COMPRESSION)
            var remaining = MAX_TOTAL_PHOTO_BYTES
            for ((id, photo) in photos) {
                require(isPlannerPhotoId(id) && photo.isFile) { "A lesson photo is missing" }
                requireJpeg(photo)
                zip.putNextEntry(ZipEntry("lesson-photos/$id.jpg"))
                remaining -= photo.inputStream().use { copyBounded(it, zip, minOf(remaining, MAX_PHOTO_BYTES)) }
                zip.closeEntry()
            }
        }
    }

    fun read(source: InputStream, scratch: File): Contents {
        val input = source.buffered()
        input.mark(4)
        val zipped = input.read() == 0x50 && input.read() == 0x4B
        input.reset()
        if (!zipped) return Contents(BoundedInput.read(input, MAX_METADATA_BYTES).toString(Charsets.UTF_8), emptyMap(), false)
        return ZipInputStream(input).use { zip ->
            require(zip.nextEntry?.name == METADATA) { "Invalid backup archive" }
            val metadata = BoundedInput.read(zip, MAX_METADATA_BYTES).toString(Charsets.UTF_8)
            val photos = linkedMapOf<String, File>()
            var remaining = MAX_TOTAL_PHOTO_BYTES
            while (true) {
                val entry = zip.nextEntry ?: break
                val id = photoEntry.matchEntire(entry.name)?.groupValues?.get(1)
                require(!entry.isDirectory && id != null && isPlannerPhotoId(id) && id !in photos) { "Invalid photo entry" }
                val target = File.createTempFile("photo-", ".jpg", scratch)
                remaining -= target.outputStream().use { copyBounded(zip, it, minOf(remaining, MAX_PHOTO_BYTES)) }
                requireJpeg(target)
                photos[id] = target
            }
            Contents(metadata, photos, true)
        }
    }

    private fun requireJpeg(file: File) {
        file.inputStream().buffered().use {
            require(it.read() == 0xFF && it.read() == 0xD8 && it.read() == 0xFF) { "Invalid lesson photo" }
        }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            written += size
            require(written <= limit) { "Backup photos exceed the size limit" }
            output.write(buffer, 0, size)
        }
        return written
    }
}
