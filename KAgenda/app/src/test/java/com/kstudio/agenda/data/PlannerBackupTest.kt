package com.kstudio.agenda.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PlannerBackupTest {
    @get:Rule val temporary = TemporaryFolder()

    private val noteId = "c57a26b9-50eb-40c1-aa49-c83af1d5be4e"
    private val photoId = "c6fbf331-294b-4099-81d0-b563b262bd5b"
    private val billId = "7bcde302-2f37-4141-9b3e-2cf3a030894f"
    private val jpeg = java.util.Base64.getDecoder().decode("/9j/2Q==").let { it.copyOfRange(0, 3) + byteArrayOf(1, 2, 3, -1, -39) }
    private val lesson = LessonRef("buaa", "series/m101", 20778, "Calculus")
    private val planner = PlannerData(
        tabs = listOf("Countdown", "Schedule", "Bills", "Settings"), countdownPeriod = "month",
        widgetActions = mapOf("next" to "camera"), courseTags = mapOf(lesson.id to listOf("lab")),
        notes = listOf(LessonNote(noteId, lesson, "复习第三章", listOf("作业", "自定义"), photoId, "2026-11-30T18:00", true)),
        links = listOf(CountdownLink("agenda/1", "Review", "2026-12-01T10:00", agendaId = "1")),
        bills = listOf(BillRule(billId, "Renewal", "2026-11-01", "year", 30, "subscription", null, "icloud", 2)),
        courseTagDefinitions = listOf(CourseTagDefinition("lab", "实践")),
        countdownFormat = "days_clock", countdownsExpanded = false, deadlinesExpanded = true,
    )

    @Test fun roundTripsPlannerMetadataAndOriginalPhotoBytes() {
        val inputPhoto = temporary.newFile("original.jpg").apply { writeBytes(jpeg) }
        val metadata = JSONObject().put("planner", JSONObject(PlannerStore.encode(planner))).toString()
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, metadata, mapOf(photoId to inputPhoto))
        val contents = BackupArchive.read(output.toByteArray().inputStream(), temporary.newFolder())
        assertEquals(planner, plannerBackupData(JSONObject(contents.metadata).getJSONObject("planner"), contents.photos.keys))
        assertArrayEquals(jpeg, contents.photos.getValue(photoId).readBytes())
    }

    @Test fun readsLegacyJsonWithoutRestoringAnyPhotoEntries() {
        val json = """{"format":"kagenda-backup","version":1,"settings":[]}"""
        val contents = BackupArchive.read(json.byteInputStream(), temporary.newFolder())
        assertEquals(json, contents.metadata)
        assertFalse(contents.zipped)
        assertTrue(contents.photos.isEmpty())
    }

    @Test fun legacyPlannerPreservesDefaultLabelsAndMonthlyInterval() {
        val old = PlannerStore.decode("""{"courseTags":{"lesson":["lab"]},"bills":[{"id":"$billId","title":"Rent","first":"2026-11-01","repeat":"month","days":30}]}""")
        assertEquals(listOf("exam", "lab", "quiz"), old.courseTagDefinitions.map { it.id })
        assertEquals(listOf("lab"), old.courseTags.getValue("lesson"))
        assertEquals(1, old.bills.single().interval ?: 1)
        assertEquals("auto", old.countdownFormat)
    }

    @Test fun rejectsMissingPhotosAndTraversalNames() {
        val metadata = JSONObject(PlannerStore.encode(planner))
        assertThrows(IllegalArgumentException::class.java) { plannerBackupData(metadata, emptySet()) }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lesson-photos/../planner.json"))
            zip.write(jpeg)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.read(output.toByteArray().inputStream(), temporary.newFolder())
        }
    }

    @Test fun failedRestoreRollsBackPhotoAndPlannerTogether() = runBlocking {
        val root = temporary.newFolder()
        val before = root.resolve("planner.json").apply { writeText("old planner") }
        val folder = root.resolve("lesson-photos").apply { mkdir() }
        val activePhoto = folder.resolve("$photoId.jpg").apply { writeBytes(byteArrayOf(8, 9)) }
        val newPhoto = temporary.newFile("new.jpg").apply { writeBytes(jpeg) }
        val exception = runCatching {
            BackupFileTransaction.restore(root, mapOf("planner.json" to "new planner"),
                mapOf("lesson-photos/$photoId.jpg" to newPhoto)) { error("settings failure") }
        }.exceptionOrNull()
        assertEquals("settings failure", exception?.message)
        assertEquals("old planner", before.readText())
        assertArrayEquals(byteArrayOf(8, 9), activePhoto.readBytes())
    }
}
