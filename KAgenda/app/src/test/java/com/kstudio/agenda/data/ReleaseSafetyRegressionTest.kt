package com.kstudio.agenda.data

import com.kstudio.agenda.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

class ReleaseSafetyRegressionTest {
    private val course = Course("课程", "C101", "", "1-16", "A101", 1, 2, 1)

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> entries.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
        return out.toByteArray()
    }

    @Test fun `bounded input accepts exact size and rejects oversized input before reading it all`() {
        assertArrayEquals(byteArrayOf(1, 2), BoundedInput.read(byteArrayOf(1, 2).inputStream(), 2))
        val input = ByteArray(100_000).inputStream()
        assertThrows(BoundedInput.TooLarge::class.java) { BoundedInput.read(input, 10) }
        assertEquals(99_989, input.available())
        assertEquals(0, BoundedInput.read(byteArrayOf().inputStream(), 0).size)
    }

    @Test fun `xlsx shared strings resolve even when their ZIP entry is last`() {
        val bytes = archive(
            "xl/worksheets/sheet1.xml" to "<worksheet><row><c r=\"A1\" t=\"s\"><v>0</v></c></row></worksheet>",
            "xl/sharedStrings.xml" to "<sst><si><t>课程 &amp;lt;A&amp;gt;</t></si></sst>",
        )
        assertEquals("课程 &lt;A&gt;", DocumentTextExtractor.extractBytes(bytes, "xlsx").getOrThrow())
    }

    @Test fun `escaped angle brackets in docx remain visible`() {
        val bytes = archive("word/document.xml" to "<w:document><w:p><w:t>保留&lt;课程&gt;</w:t></w:p></w:document>")
        assertEquals("保留<课程>", DocumentTextExtractor.extractBytes(bytes, "docx").getOrThrow())
    }

    @Test fun `compressed expansion is bounded even for ignored ZIP entries`() {
        val bytes = archive("unused.bin" to "x".repeat(8 * 1024 * 1024 + 1), "word/document.xml" to "<w:t>text</w:t>")
        assertTrue(DocumentTextExtractor.extractBytes(bytes, "docx").exceptionOrNull() is BoundedInput.TooLarge)
    }

    @Test fun `invalid worksheet column cannot allocate an enormous row`() {
        val bytes = archive("xl/worksheets/sheet1.xml" to "<row><c r=\"ZZZZZZZZ1\"><v>1</v></c></row>")
        assertTrue(DocumentTextExtractor.extractBytes(bytes, "xlsx").isFailure)
    }

    @Test fun `new course edit preserves an identical target from another school`() {
        val previous = Schools.currentId
        try {
            Schools.setCurrent("school_a")
            val other = CourseEdit(false, course, course, 0, kind = EDIT_KIND_ADD, schoolId = "school_b")
            val current = other.copy(schoolId = "school_a")
            val result = CourseEditStore.merge(listOf(other), current)
            assertEquals(listOf(other, current), result)
            assertEquals(listOf(other, current.copy(editedAtMillis = 2)), CourseEditStore.merge(result, current.copy(editedAtMillis = 2)))
        } finally { Schools.setCurrent(previous) }
    }

    @Test fun `series edit does not remove single slot edits from another school`() {
        val previous = Schools.currentId
        try {
            Schools.setCurrent("school_a")
            val other = CourseEdit(false, course, course, 0, schoolId = "school_b")
            val current = other.copy(applyAll = true, schoolId = "school_a")
            assertEquals(listOf(other, current), CourseEditStore.merge(listOf(other), current))
        } finally { Schools.setCurrent(previous) }
    }

    @Test fun `imported course appears in a week absent from academic cache and disappears on removal`() {
        val raw = SemesterSchedule("学期", 0, mapOf(1 to listOf(course.copy(weeksRaw = "1"))), 0)
        val extra = course.copy(code = "LOCAL", weeksRaw = "3")
        assertEquals(listOf(extra), ExtraCoursesStore.mergeInto(raw, listOf(extra), 0)!!.weeks[3])
        assertEquals(raw, ExtraCoursesStore.mergeInto(raw, emptyList(), 0))
        assertFalse(raw.weeks.containsKey(3))
    }

    @Test fun `backup rejects wrong settings types unknown keys and invalid dates before import`() {
        val root = JSONObject().put("settings", JSONArray().put(JSONObject().put("k", "reminder_enabled").put("t", "s").put("v", "true")))
        assertThrows(IllegalArgumentException::class.java) { BackupPayload.validate(root) }
        root.put("settings", JSONArray().put(JSONObject().put("k", "unknown").put("t", "b").put("v", true)))
        assertThrows(IllegalArgumentException::class.java) { BackupPayload.validate(root) }
        root.remove("settings")
        root.put("agenda", JSONArray().put(JSONObject().put("id", "id").put("title", "title").put("dateEpochDay", Long.MAX_VALUE)))
        assertThrows(java.time.DateTimeException::class.java) { BackupPayload.validate(root) }
    }

    @Test fun `backup rejects wrong block shapes and traversal school ids`() {
        assertThrows(IllegalArgumentException::class.java) { BackupPayload.validate(JSONObject().put("agenda", JSONObject())) }
        assertThrows(IllegalArgumentException::class.java) {
            BackupPayload.validate(JSONObject().put("schoolSchedules", JSONObject().put("../escape", JSONObject())))
        }
    }

    @Test fun `backup accepts current settings and school profiles without encrypted secrets`() {
        val root = JSONObject().put("settings", JSONArray()
            .put(JSONObject().put("k", "last_sync_at").put("t", "l").put("v", 0))
            .put(JSONObject().put("k", "school_profiles").put("t", "s").put("v", "{}")))
        BackupPayload.validate(root)
        assertFalse(BackupSettings.validate("password_enc", "secret"))
        assertFalse(BackupSettings.validate("ai_key_enc", "secret"))
    }

    @Test fun `cached package is rehashed even if length is unchanged`() {
        val root = Files.createTempDirectory("update-test").toFile()
        try {
            val file = File(root, "update.apk").apply { writeText("abc") }
            val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            assertTrue(UpdateIntegrity.matches(file, 3, hash))
            file.writeText("abd")
            assertFalse(UpdateIntegrity.matches(file, 3, hash))
            assertFalse(UpdateIntegrity.matches(file, 4, ""))
            file.writeText("")
            assertFalse(UpdateIntegrity.matches(file, 0, ""))
        } finally { root.deleteRecursively() }
    }

    @Test fun `update URL rejects cleartext downgrade credentials and missing host`() {
        UpdateIntegrity.requireHttps("https://example.com/update.apk")
        for (url in listOf("http://example.com/a.apk", "https://user:pass@example.com/a.apk", "https:///update.apk")) {
            assertThrows(IllegalArgumentException::class.java) { UpdateIntegrity.requireHttps(url) }
        }
    }

    @Test fun `atomic file replacement leaves no staging files`() {
        val root = Files.createTempDirectory("atomic-test").toFile()
        try {
            val file = File(root, "data.json")
            AtomicFileStore.write(file, "old")
            AtomicFileStore.write(file, "new")
            assertEquals("new", file.readText())
            assertEquals(listOf("data.json"), root.listFiles()!!.map { it.name })
        } finally { root.deleteRecursively() }
    }

    @Test fun `future recurring projection has a single occurrence end rather than repeat cutoff`() {
        val day = LocalDate.of(2026, 10, 17)
        val repeating = AgendaEvent("id", "日程", day.toEpochDay(), "09:00", "10:00", repeatRule = "daily:1", endDateEpochDay = day.plusDays(10).toEpochDay())
        val view = repeating.upcomingView(day.atTime(12, 0))
        assertEquals(day.plusDays(1), view.date)
        assertNull(view.endDateEpochDay)
        assertEquals("", view.repeatRule)
        assertEquals(day.plusDays(1).atTime(10, 0), view.endDateTime())
        assertFalse(view.isOngoing(day.plusDays(2).atTime(9, 30)))
        assertFalse(repeating.occursOn(day.plusDays(11)))
    }

    @Test fun `reminder lead from external data stays within supported bounds`() {
        val event = AgendaEvent("id", "日程", 0, followGlobalReminder = false, reminderLeadMinutes = Int.MAX_VALUE)
        assertEquals(10080, ReminderPolicy.forEvent(event, true, true, false, 30).leadMinutes)
        assertEquals(0, ReminderPolicy.forEvent(event.copy(reminderLeadMinutes = -1), true, true, false, 30).leadMinutes)
        assertEquals(120, ReminderPolicy.forEvent(event.copy(followGlobalReminder = true), true, true, false, Int.MAX_VALUE).leadMinutes)
    }

    @Test fun `backup settings failure restores every original and removes newly created files`() {
        val root = Files.createTempDirectory("restore-test").toFile()
        try {
            File(root, "agenda.json").writeText("original")
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    BackupFileTransaction.restore(root, linkedMapOf("agenda.json" to "imported", "extra.json" to "new")) {
                        error("settings failed")
                    }
                }
            }
            assertEquals("original", File(root, "agenda.json").readText())
            assertFalse(File(root, "extra.json").exists())
            assertEquals(listOf("agenda.json"), root.listFiles()!!.map { it.name })
        } finally { root.deleteRecursively() }
    }

    @Test fun `staging failure leaves all original files and settings untouched`() {
        val root = Files.createTempDirectory("restore-test").toFile()
        try {
            File(root, "agenda.json").writeText("original")
            File(root, "blocked.json").mkdir()
            var settingsWritten = false
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    BackupFileTransaction.restore(root, linkedMapOf("agenda.json" to "imported", "blocked.json" to "invalid")) {
                        settingsWritten = true
                    }
                }
            }
            assertFalse(settingsWritten)
            assertEquals("original", File(root, "agenda.json").readText())
            assertFalse(root.walkTopDown().any { it.name.startsWith("restore-") && it != root })
        } finally { root.deleteRecursively() }
    }

    @Test fun `successful backup transaction saves files then settings and removes temporary files`() = runBlocking {
        val root = Files.createTempDirectory("restore-test").toFile()
        try {
            File(root, "agenda.json").writeText("original")
            var settingsWritten = false
            BackupFileTransaction.restore(root, mapOf("agenda.json" to "imported")) {
                assertEquals("imported", File(root, "agenda.json").readText())
                settingsWritten = true
            }
            assertTrue(settingsWritten)
            assertEquals(listOf("agenda.json"), root.listFiles()!!.map { it.name })
        } finally { root.deleteRecursively() }
    }
}
