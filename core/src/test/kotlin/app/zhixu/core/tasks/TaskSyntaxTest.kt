package app.zhixu.core.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TaskSyntaxTest {
    @Test
    fun `normalize inserts missing ids`() {
        val input = """
            # T
            - [ ] A
            - [x] B @id(01JTESTTESTTESTTESTTESTTEST)
            - [ ] C @tag(work)
        """.trimIndent()

        val result = TaskSyntax.normalizeMarkdown(input)
        assertTrue(result.changed)
        assertEquals(2, result.insertedIds)
        assertTrue(result.markdown.contains("@id("))
    }

    @Test
    fun `toggle adds done and id`() {
        val now = LocalDateTime.of(2025, 12, 25, 10, 30)
        val input = "- [ ] A"
        val out = TaskSyntax.toggleTaskAtLine(input, lineIndex = 0, now = now)
        assertTrue(out.startsWith("- [x] A"))
        assertTrue(out.contains("@id("))
        assertTrue(out.contains("@done(2025-12-25 10:30)"))
    }

    @Test
    fun `toggle removes done when unchecking`() {
        val now = LocalDateTime.of(2025, 12, 25, 10, 30)
        val input = "- [x] A @done(2025-12-25 10:30) @id(01JTESTTESTTESTTESTTESTTEST)"
        val out = TaskSyntax.toggleTaskAtLine(input, lineIndex = 0, now = now)
        assertTrue(out.startsWith("- [ ] A"))
        assertTrue(!out.contains("@done("))
        assertTrue(out.contains("@id("))
    }
}

