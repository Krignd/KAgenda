package com.kstudio.agenda.i18n

import org.junit.Assert.*
import org.junit.Test

class HelpSearchTest {
    @Test fun `blank query keeps all questions in their original order`() {
        assertEquals(HelpContent.zh, searchHelpQuestions(HelpContent.zh, " \t\n "))
    }

    @Test fun `search finds Chinese answer text as well as question titles`() {
        assertEquals(listOf("sync"), searchHelpQuestions(HelpContent.zh, "江苏大学").map { it.id })
        assertEquals(listOf("lock_screen"), searchHelpQuestions(HelpContent.zh, "锁屏").map { it.id })
    }

    @Test fun `search ignores case and accents and requires every keyword`() {
        assertEquals(listOf("lock_screen"), searchHelpQuestions(HelpContent.en, "LOCK  silent").map { it.id })
        assertEquals(listOf("lock_screen"), searchHelpQuestions(HelpContent.fr, "ECRAN verrouille").map { it.id })
        assertTrue(searchHelpQuestions(HelpContent.en, "lock nonexistentkeyword").isEmpty())
    }

    @Test fun `all languages expose the same distinct topics with nonempty content`() {
        for (questions in listOf(HelpContent.zh, HelpContent.en, HelpContent.fr)) {
            assertEquals(HelpContent.zh.map { it.id }, questions.map { it.id })
            assertEquals(questions.size, questions.map { it.id }.toSet().size)
            assertTrue(questions.all { it.title.isNotBlank() && it.answer.isNotBlank() })
        }
    }
}
