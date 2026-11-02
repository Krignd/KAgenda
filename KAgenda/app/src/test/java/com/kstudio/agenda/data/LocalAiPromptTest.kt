package com.kstudio.agenda.data

import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class LocalAiPromptTest {
    @Before fun language() { AppText.state.value = AppLang.EN }
    private val existing = listOf("""{"id":"event-1","t":"体检","d":"2026-10-19"}""")

    @Test fun `only simple unambiguous input bypasses the model`() {
        assertTrue(LocalAiPrompt.simpleRuleInput("明天下午3点开会"))
        for (text in listOf("把体检改到明天下午", "取消明天的会议", "明天3点到5点开会", "明天3点一班开会", "每周三下午3点开会", "明天3点提醒我开会"))
            assertFalse(text, LocalAiPrompt.simpleRuleInput(text))
    }

    @Test fun `mutations cannot become rule-based additions`() {
        for (text in listOf("删除体检", "把会议改为周五", "不要提醒我开会", "Cancel the meeting", "Déplacer le rendez-vous"))
            assertTrue(text, LocalAiPrompt.isMutation(text))
    }

    @Test fun `valid batch preserves reminders and recurrence`() {
        val reply = """{"ops":[{"op":"add","tg":"plan","t":"锻炼","d":"2026-10-19","s":"20:00","rp":"weekly:1,3,5","alarm":true,"lead":0}]}"""
        val op = LocalAiPrompt.parseOps(reply, emptyList()).single() as AiSkills.AiOp.Add
        assertTrue(op.item.isPlan)
        assertEquals("weekly:1,3,5", op.item.repeat)
        assertEquals(true, op.item.alarm)
    }

    @Test fun `update requires an id actually provided to the model`() {
        val reply = """{"ops":[{"op":"update","match":{"id":"event-1","t":"体检"},"set":{"s":"15:30"}}]}"""
        assertEquals("event-1", (LocalAiPrompt.parseOps(reply, existing).single() as AiSkills.AiOp.Update).matchId)
        rejects(reply, emptyList())
        rejects(reply.replace("event-1", "invented"), existing)
    }

    @Test fun `unknown operations invalid dates and partial batches fail closed`() {
        for (reply in listOf(
            """{"ops":[{"op":"unknown","t":"oops"}]}""",
            """{"ops":[{"op":"add","t":"考试","d":"2026-02-30"}]}""",
            """{"ops":[{"op":"add","t":"会议","s":"25:00"}]}""",
            """{"ops":[{"op":"add","t":"会议","rp":"weekly:9"}]}""",
            """{"ops":[{"op":"add","t":"会议"},{"op":"add","t":""}]}""",
            """{"ops":[{"op":"add","t":"会议"}""",
            """{"ops":[{"op":"delete","match":{"t":"体检"}}]}""",
            """{"ops":[{"op":"add","t":"会议","alarm":"false"}]}""",
            """{"ops":[{"op":"add","t":null}]}""",
            """{"ops":[{"op":"add","t":"会议","lead":-1}]}""",
            """{"ops":[{"op":"add","t":"假期","d":"2026-10-20","lg":true,"ed":"2026-10-19"}]}""",
        )) rejects(reply, existing)
    }

    @Test fun `empty operations are an explicit no-op`() {
        assertTrue(LocalAiPrompt.parseOps("""{"ops":[]}""", emptyList()).isEmpty())
    }

    @Test fun `null optional fields mean omitted fields`() {
        val reply = """{"ops":[{"op":"add","t":"会议","d":"2026-10-19","s":"09:00","e":null,"ed":null,"rp":null,"lg":null,"alarm":null,"lead":null}]}"""
        assertEquals(1, LocalAiPrompt.parseOps(reply, emptyList()).size)
    }

    @Test fun `explicit destination overrides model date arithmetic without using the old date`() {
        val date = LocalDate.of(2026, 10, 18)
        val update = AiSkills.AiOp.Update("体检", date, AiSkills.AiSet(null, date, "10:00", null, null, null), "event-1")
        val normalized = LocalAiPrompt.normalizeExplicitDestination(listOf(update), "把明天的体检改到后天下午三点", date).single() as AiSkills.AiOp.Update
        assertEquals(date.plusDays(2), normalized.set.date)
        assertEquals("15:00", normalized.set.startTime)
        assertEquals("event-1", normalized.matchId)
        assertNull(normalized.set.alarm)
    }

    @Test fun `long inputs are rejected rather than silently truncated`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalAiPrompt.user("字".repeat(1601), LocalDate.of(2026, 10, 18), emptyList(), "")
        }
    }

    @Test fun `backup accepts local settings without including model weights`() {
        assertTrue(BackupSettings.validate("ai_provider", "local"))
        assertTrue(BackupSettings.validate("local_ai_model", LocalAiModels.defaultId))
        assertFalse(BackupSettings.validate("local_ai_weights", "weights"))
        assertEquals(2, LocalAiModels.all.size)
        for (model in LocalAiModels.all) {
            assertTrue(model.url.startsWith("https://huggingface.co/unsloth/"))
            assertTrue(model.url.contains(model.revision))
            assertTrue(model.sha256.matches(Regex("[a-f0-9]{64}")))
        }
    }

    private fun rejects(reply: String, context: List<String>) {
        assertThrows("Must reject: $reply", RuntimeException::class.java) { LocalAiPrompt.parseOps(reply, context) }
    }
}
