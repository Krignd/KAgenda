package com.kstudio.agenda.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 在分配整块内存之前限制输入；也用于压缩文档的展开大小。 */
object BoundedInput {
    class TooLarge : IllegalArgumentException("Input exceeds size limit")

    fun read(input: InputStream, maxBytes: Int): ByteArray {
        require(maxBytes >= 0)
        val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes.toLong() - output.size() + 1).toInt())
            if (count < 0) return output.toByteArray()
            if (count == 0) continue
            if (count > maxBytes - output.size()) throw TooLarge()
            output.write(buffer, 0, count)
        }
    }
}
