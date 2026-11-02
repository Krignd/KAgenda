package com.kstudio.agenda.data

import org.junit.Assert.*
import org.junit.Test

class ModelDownloadRangeTest {
    @Test fun `resume only accepts the exact pinned range`() {
        assertTrue(ModelDownloadWorker.validRange("bytes 100-999/1000", 100, 1000))
        for (header in listOf(null, "bytes 0-999/1000", "bytes 100-999/2000", "bytes 100-500/1000", "bytes */1000", "bytes 999999999999999999999-999/1000"))
            assertFalse(header, ModelDownloadWorker.validRange(header, 100, 1000))
    }
}
