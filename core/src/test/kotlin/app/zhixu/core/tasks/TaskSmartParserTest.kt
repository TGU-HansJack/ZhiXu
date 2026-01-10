package app.zhixu.core.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime

class TaskSmartParserTest {
    @Test
    fun `parses relative day and time`() {
        val now = LocalDateTime.of(2025, 3, 5, 10, 0)
        val r = TaskSmartParser.parse("今天下午3点 开会", now)
        assertEquals("开会", r.cleanedTitle)
        assertEquals(now.toLocalDate(), r.dueDate)
        assertEquals(LocalTime.of(15, 0), r.dueTime)
    }

    @Test
    fun `parses weekday to nearest occurrence`() {
        val now = LocalDateTime.of(2025, 3, 5, 10, 0) // Wed
        val r = TaskSmartParser.parse("周一 例会", now)
        assertEquals("例会", r.cleanedTitle)
        assertNotNull(r.dueDate)
        // nearest Monday after Wed is 2025-03-10
        assertEquals(10, r.dueDate!!.dayOfMonth)
    }

    @Test
    fun `parses time only to next valid occurrence`() {
        val now = LocalDateTime.of(2025, 3, 6, 10, 0)
        val r = TaskSmartParser.parse("9点 打卡", now)
        assertEquals("打卡", r.cleanedTitle)
        assertEquals(now.toLocalDate().plusDays(1), r.dueDate)
        assertEquals(LocalTime.of(9, 0), r.dueTime)
    }

    @Test
    fun `parses remind before due`() {
        val now = LocalDateTime.of(2025, 3, 6, 10, 0)
        val r = TaskSmartParser.parse("今天下午3点 提前5分钟提醒我 开会", now)
        assertEquals("开会", r.cleanedTitle)
        assertEquals(TaskSmartParseResult.ReminderSpec.OffsetBefore(TimeDelta(minutes = 5)), r.remind)
    }

    @Test
    fun `parses repeat and anchors due date`() {
        val now = LocalDateTime.of(2025, 3, 6, 10, 0)
        val r = TaskSmartParser.parse("每天 喝水", now)
        assertEquals("喝水", r.cleanedTitle)
        assertEquals("每天", r.repeat)
        assertEquals(now.toLocalDate(), r.dueDate)
    }
}

