package com.kstudio.agenda.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SchoolFileScopeTest {
    @get:Rule val temp = TemporaryFolder()
    private val name = "schedule_cache.json"
    @Test fun `switching and saving another school preserves the first school's original cache`() {
        val root = temp.root
        SchoolFileScope.save(root, name, "school_a", JSONObject().put("value", "original"))
        assertNull(SchoolFileScope.load(root, name, "school_b"))
        SchoolFileScope.save(root, name, "school_b", JSONObject().put("value", "new"))
        assertEquals("original", SchoolFileScope.load(root, name, "school_a")!!.getString("value"))
        assertEquals("new", SchoolFileScope.load(root, name, "school_b")!!.getString("value"))
    }
    @Test fun `legacy unowned courses migrate once and cannot follow school switches`() {
        File(temp.root, "extra_courses.json").writeText("""{"courses":[{"title":"manual"}]}""")
        val first = SchoolFileScope.load(temp.root, "extra_courses.json", "school_a")!!
        assertEquals("school_a", first.getString("school"))
        assertNull(SchoolFileScope.load(temp.root, "extra_courses.json", "school_b"))
        assertTrue(SchoolFileScope.file(temp.root, "extra_courses.json", "school_a").exists())
    }
    @Test fun `clearing one school never deletes the other school's most recent cache`() {
        SchoolFileScope.save(temp.root, name, "school_a", JSONObject().put("v", 1))
        SchoolFileScope.save(temp.root, name, "school_b", JSONObject().put("v", 2))
        SchoolFileScope.clear(temp.root, name, "school_a")
        assertNull(SchoolFileScope.load(temp.root, name, "school_a"))
        assertEquals(2, SchoolFileScope.load(temp.root, name, "school_b")!!.getInt("v"))
    }
    @Test fun `a restored legacy backup takes precedence over the older scoped mirror`() {
        SchoolFileScope.save(temp.root, name, "school_a", JSONObject().put("v", 1))
        File(temp.root, name).writeText("""{"school":"school_a","v":99}""")
        assertEquals(99, SchoolFileScope.load(temp.root, name, "school_a")!!.getInt("v"))
    }
    @Test(expected = IllegalArgumentException::class) fun `school identifiers cannot escape their directory`() {
        SchoolFileScope.file(temp.root, name, "../outside")
    }
    @Test fun `legacy migration waits for a known school`() {
        File(temp.root, name).writeText("""{"v":1}""")
        assertNull(SchoolFileScope.load(temp.root, name, "school_a", allowUnowned = false))
        assertFalse(JSONObject(File(temp.root, name).readText()).has("school"))
    }
    @Test fun `a damaged recent mirror does not hide the intact scoped school cache`() {
        SchoolFileScope.save(temp.root, name, "school_a", JSONObject().put("v", 1))
        File(temp.root, name).writeText("broken json")
        assertEquals(1, SchoolFileScope.load(temp.root, name, "school_a")!!.getInt("v"))
        SchoolFileScope.save(temp.root, name, "school_b", JSONObject().put("v", 2))
        assertEquals(2, SchoolFileScope.load(temp.root, name, "school_b")!!.getInt("v"))
    }
}
