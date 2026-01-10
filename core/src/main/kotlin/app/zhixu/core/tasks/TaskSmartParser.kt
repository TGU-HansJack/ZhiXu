package app.zhixu.core.tasks

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

data class TaskSmartParseResult(
    val cleanedTitle: String,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val remind: ReminderSpec?,
    val remindPersistent: Boolean,
    val tags: List<String>,
    val priority: Int?,
    val repeat: String?,
    val allDay: Boolean,
) {
    sealed interface ReminderSpec {
        data object AtDue : ReminderSpec

        /** Base is the due datetime. */
        data class OffsetBefore(val delta: TimeDelta) : ReminderSpec

        /** Base is the due datetime. */
        data class OffsetAfter(val delta: TimeDelta) : ReminderSpec

        /** Base is `now`. */
        data class FromNow(val delta: TimeDelta) : ReminderSpec
    }
}

data class TimeDelta(
    val years: Int = 0,
    val months: Int = 0,
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
) {
    fun isZero(): Boolean = years == 0 && months == 0 && days == 0 && hours == 0 && minutes == 0

    fun addTo(dt: LocalDateTime): LocalDateTime =
        dt.plusYears(years.toLong())
            .plusMonths(months.toLong())
            .plusDays(days.toLong())
            .plusHours(hours.toLong())
            .plusMinutes(minutes.toLong())

    fun subtractFrom(dt: LocalDateTime): LocalDateTime =
        dt.minusYears(years.toLong())
            .minusMonths(months.toLong())
            .minusDays(days.toLong())
            .minusHours(hours.toLong())
            .minusMinutes(minutes.toLong())
}

enum class SmartTokenKind {
    Date,
    Time,
    Repeat,
    Remind,
    Tag,
    Priority,
}

data class SmartToken(
    val start: Int,
    val endExclusive: Int,
    val kind: SmartTokenKind,
)

object TaskSmartParser {
    private val tagRegex = Regex("""(^|\s)[#@]([\p{L}\p{N}_-]{1,20})""")
    private val priorityRegex = Regex("""\b[pP]([1-4])\b""")
    private val weekdayRegex = Regex("""(下?)(周|星期)([一二三四五六日天])""")
    private val dateYmdRegex = Regex("""\b(20\d{2})[-/\.](\d{1,2})[-/\.](\d{1,2})\b""")
    private val dateMdCnRegex = Regex("""(?<!\d)(\d{1,2})月(\d{1,2})(日|号)?""")
    private val dateMdSepRegex = Regex("""\b(\d{1,2})[-/](\d{1,2})\b""")
    private val monthOnlyRegex = Regex("""(?<!\d)(\d{1,2})月(?!\d)""")
    private val timeHmRegex = Regex("""\b(\d{1,2})[:：](\d{1,2})\b""")
    private val timeHalfRegex = Regex("""(?<!\d)(\d{1,2})点半""")
    private val timeHmsRegex = Regex("""(?<!\d)(\d{1,2})点(\d{1,2})分?""")
    private val timeHourRegex = Regex("""(?<!\d)(\d{1,2})点(?!\d)""")

    private val repeatEveryDaysRegex = Regex("""每\s*(\d+)\s*天""")
    private val repeatEveryWeeksRegex = Regex("""每\s*(\d+)\s*周""")
    private val repeatEveryMonthsRegex = Regex("""每\s*(\d+)\s*月""")
    private val repeatEveryYearsMonthDayRegex = Regex("""每年\s*(\d{1,2})月(\d{1,2})(日|号)?""")
    private val repeatEveryYearsMonthRegex = Regex("""每年\s*(\d{1,2})月(?!\d)""")
    private val repeatMonthlyDayRegex = Regex("""每月\s*第\s*(\d{1,2})\s*天""")
    private val repeatMonthlyLastRegex = Regex("""每月\s*(最后|最)\s*(\d{1,2})\s*天""")

    private val remindAtDueRegex = Regex("""(准时|按时)提醒""")
    private val remindPersistentRegex = Regex("""(持续提醒|一直提醒|不断提醒)""")
    private val remindBeforeRegex = Regex("""提前\s*([0-9]+)\s*(分钟|分|小时|天|周|月|年)""")
    private val remindAfterRegex = Regex("""(延后|推迟)\s*([0-9]+)\s*(分钟|分|小时|天|周|月|年)""")
    private val remindFromNowHmRegex = Regex("""(\d+)\s*小时\s*(\d+)?\s*(分钟|分)?\s*(后|之后|以后)(提醒)?""")
    private val remindFromNowMinutesRegex = Regex("""(\d+)\s*(分钟|分)\s*(后|之后|以后)(提醒)?""")
    private val remindFromNowUnitRegex = Regex("""(\d+)\s*(天|周|月|年)\s*(后|之后|以后)(提醒)?""")
    private val relativeDayRegex = Regex("""今天|今日|明天|后天""")
    private val dayPeriodTokenRegex = Regex("""凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|今晚|夜里|深夜""")
    private val repeatSimpleRegex = Regex("""每天|每日|每周末|每个工作日|每工作日|每周|每月|每年""")

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now()): TaskSmartParseResult {
        var text =
            raw.trim()
                .replace('：', ':')
                .replace(Regex("""\s{2,}"""), " ")
        if (text.isBlank()) {
            return TaskSmartParseResult(
                cleanedTitle = "",
                dueDate = null,
                dueTime = null,
                remind = null,
                remindPersistent = false,
                tags = emptyList(),
                priority = null,
                repeat = null,
                allDay = false,
            )
        }

        val tags = ArrayList<String>()
        tagRegex.findAll(text).forEach { m ->
            val t = m.groupValues[2]
            if (t.isNotBlank()) tags += t
        }
        text =
            text.replace(Regex("""[#@][\p{L}\p{N}_-]{1,20}"""), " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()

        var priority: Int? = null
        priorityRegex.find(text)?.let { m ->
            priority = m.groupValues[1].toIntOrNull()
            text = stripToken(text, m.value)
        }

        val fallbackTitle = text

        val dayPeriod = detectDayPeriod(text)

        var repeat: String? = null
        var repeatAnchorDate: LocalDate? = null
        run {
            fun setRepeat(value: String, matched: String) {
                if (repeat != null) return
                repeat = value
                text = stripToken(text, matched)
            }

            if (text.contains("每天") || text.contains("每日")) {
                setRepeat("每天", if (text.contains("每天")) "每天" else "每日")
                repeatAnchorDate = now.toLocalDate()
            }
            if (repeat == null && text.contains("每周") && !text.contains("每周末")) {
                setRepeat("每周", "每周")
                repeatAnchorDate = nextOrSameWeekday(now.toLocalDate(), DayOfWeek.MONDAY, forceNextWeek = false)
            }
            if (repeat == null && text.contains("每月") && !text.contains("每月第") && !text.contains("每月最") && !text.contains("每月最后")) {
                setRepeat("每月", "每月")
                repeatAnchorDate = now.toLocalDate()
            }
            if (
                repeat == null &&
                text.contains("每年") &&
                !repeatEveryYearsMonthRegex.containsMatchIn(text) &&
                !repeatEveryYearsMonthDayRegex.containsMatchIn(text)
            ) {
                setRepeat("每年", "每年")
                repeatAnchorDate = now.toLocalDate()
            }
            repeatEveryDaysRegex.find(text)?.let { m ->
                val n = m.groupValues[1].toIntOrNull()?.coerceIn(1, 3650) ?: return@let
                setRepeat("每${n}天", m.value)
                repeatAnchorDate = now.toLocalDate()
            }
            repeatEveryWeeksRegex.find(text)?.let { m ->
                val n = m.groupValues[1].toIntOrNull()?.coerceIn(1, 520) ?: return@let
                setRepeat("每${n}周", m.value)
                repeatAnchorDate = now.toLocalDate()
            }
            repeatEveryMonthsRegex.find(text)?.let { m ->
                val n = m.groupValues[1].toIntOrNull()?.coerceIn(1, 1200) ?: return@let
                setRepeat("每${n}月", m.value)
                repeatAnchorDate = now.toLocalDate()
            }
            if (text.contains("每周末")) {
                setRepeat("每周末", "每周末")
                repeatAnchorDate = nextOrSameWeekend(now.toLocalDate())
            }
            if (text.contains("每个工作日") || text.contains("每工作日")) {
                setRepeat("每个工作日", if (text.contains("每个工作日")) "每个工作日" else "每工作日")
                repeatAnchorDate = nextOrSameWorkday(now.toLocalDate())
            }
            repeatMonthlyDayRegex.find(text)?.let { m ->
                val day = m.groupValues[1].toIntOrNull()?.coerceIn(1, 31) ?: return@let
                setRepeat("每月第${day}天", m.value)
                repeatAnchorDate = nextMonthlyDay(now.toLocalDate(), day)
            }
            repeatMonthlyLastRegex.find(text)?.let { m ->
                val n = m.groupValues[2].toIntOrNull()?.coerceIn(1, 31) ?: return@let
                setRepeat("每月最后${n}天", m.value)
                repeatAnchorDate = nextMonthlyLastNDay(now.toLocalDate(), n)
            }
            repeatEveryYearsMonthDayRegex.find(text)?.let { m ->
                val mo = m.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: return@let
                val d = m.groupValues[2].toIntOrNull()?.coerceIn(1, 31) ?: return@let
                setRepeat("每年${mo}月${d}日", m.value)
                repeatAnchorDate = nextYearlyMonthDay(now.toLocalDate(), mo, d)
            }
            repeatEveryYearsMonthRegex.find(text)?.let { m ->
                val mo = m.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: return@let
                setRepeat("每年${mo}月", m.value)
                repeatAnchorDate = nextYearlyMonthDay(now.toLocalDate(), mo, 1)
            }
        }

        var allDay = false
        if (text.contains("全天")) {
            allDay = true
            text = stripToken(text, "全天")
        }

        var dueDate: LocalDate? = null
        var dueTime: LocalTime? = null

        // relative day
        val today = now.toLocalDate()
        when {
            text.contains("后天") -> {
                dueDate = today.plusDays(2)
                text = stripToken(text, "后天")
            }
            text.contains("明天") -> {
                dueDate = today.plusDays(1)
                text = stripToken(text, "明天")
            }
            text.contains("今天") || text.contains("今日") -> {
                dueDate = today
                text = stripToken(text, "今天")
                text = stripToken(text, "今日")
            }
        }

        // weekday
        weekdayRegex.find(text)?.let { m ->
            val forceNextWeek = m.groupValues[1] == "下"
            val wd = parseWeekdayToken(m.groupValues[3])
            if (wd != null) {
                val next = nextOrSameWeekday(today, wd, forceNextWeek = forceNextWeek)
                dueDate = dueDate ?: next
                text = stripToken(text, m.value)
                if (repeat == "每周" || repeat == "每1周") {
                    repeat = "每" + formatWeekdayShort(wd)
                }
            }
        }

        // explicit dates
        dateYmdRegex.find(text)?.let { m ->
            val y = m.groupValues[1].toIntOrNull()
            val mo = m.groupValues[2].toIntOrNull()
            val d = m.groupValues[3].toIntOrNull()
            if (y != null && mo != null && d != null) {
                runCatching { LocalDate.of(y, mo, d) }.onSuccess { date ->
                    dueDate = date
                    text = stripToken(text, m.value)
                }
            }
        }

        dateMdCnRegex.find(text)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull()
            val d = m.groupValues[2].toIntOrNull()
            if (mo != null && d != null) {
                val next = nextYearlyMonthDay(today, mo, d)
                dueDate = dueDate ?: next
                text = stripToken(text, m.value)
            }
        }

        dateMdSepRegex.find(text)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull()
            val d = m.groupValues[2].toIntOrNull()
            if (mo != null && d != null) {
                val next = nextYearlyMonthDay(today, mo, d)
                dueDate = dueDate ?: next
                text = stripToken(text, m.value)
            }
        }

        // month-only: next valid month first day.
        monthOnlyRegex.find(text)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull()
            if (mo != null) {
                val next = nextYearlyMonthDay(today, mo, 1)
                dueDate = dueDate ?: next
                text = stripToken(text, m.value)
            }
        }

        // explicit time
        var hadExplicitTime = false
        timeHmRegex.find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val mi = m.groupValues[2].toIntOrNull()
            if (h != null && mi != null) {
                val time = normalizeHourWithPeriod(h, mi, dayPeriod)
                if (time != null) {
                    dueTime = time
                    hadExplicitTime = true
                    text = stripToken(text, m.value)
                }
            }
        }

        if (!hadExplicitTime) {
            timeHalfRegex.find(text)?.let { m ->
                val h = m.groupValues[1].toIntOrNull()
                val time = h?.let { normalizeHourWithPeriod(it, 30, dayPeriod) }
                if (time != null) {
                    dueTime = time
                    hadExplicitTime = true
                    text = stripToken(text, m.value)
                }
            }
        }

        if (!hadExplicitTime) {
            timeHmsRegex.find(text)?.let { m ->
                val h = m.groupValues[1].toIntOrNull()
                val mi = m.groupValues[2].toIntOrNull()
                if (h != null && mi != null) {
                    val time = normalizeHourWithPeriod(h, mi, dayPeriod)
                    if (time != null) {
                        dueTime = time
                        hadExplicitTime = true
                        text = stripToken(text, m.value)
                    }
                }
            }
        }

        if (!hadExplicitTime) {
            timeHourRegex.find(text)?.let { m ->
                val h = m.groupValues[1].toIntOrNull()
                val time = h?.let { normalizeHourWithPeriod(it, 0, dayPeriod) }
                if (time != null) {
                    dueTime = time
                    hadExplicitTime = true
                    text = stripToken(text, m.value)
                }
            }
        }

        if (hadExplicitTime && dayPeriod != null) {
            text = stripToken(text, dayPeriod.matchedToken)
        }

        // period-only default time
        if (!hadExplicitTime && !allDay) {
            dayPeriod?.defaultTime?.let { defaultTime ->
                dueTime = dueTime ?: defaultTime
                text = stripToken(text, dayPeriod.matchedToken)
            }
        }

        // If only time was provided, pick the next valid occurrence.
        if (dueDate == null && dueTime != null) {
            val candidate = today.atTime(dueTime)
            dueDate = if (candidate.isAfter(now)) today else today.plusDays(1)
        }

        if (repeat != null && dueDate == null) {
            dueDate = repeatAnchorDate
        }

        var remindPersistent = false
        if (remindPersistentRegex.containsMatchIn(text)) {
            remindPersistent = true
            text = stripToken(text, remindPersistentRegex.find(text)!!.value)
        }

        var remind: TaskSmartParseResult.ReminderSpec? = null

        if (remindAtDueRegex.containsMatchIn(text)) {
            remind = TaskSmartParseResult.ReminderSpec.AtDue
            text = stripToken(text, remindAtDueRegex.find(text)!!.value)
        }

        if (remind == null) {
            remindBeforeRegex.find(text)?.let { m ->
                val n = m.groupValues[1].toIntOrNull()
                val unit = m.groupValues[2]
                if (n != null) {
                    val delta = deltaFromUnit(n, unit)
                    if (delta != null && !delta.isZero()) {
                        remind = TaskSmartParseResult.ReminderSpec.OffsetBefore(delta)
                        text = stripToken(text, m.value)
                    }
                }
            }
        }

        if (remind == null) {
            remindAfterRegex.find(text)?.let { m ->
                val n = m.groupValues[2].toIntOrNull()
                val unit = m.groupValues[3]
                if (n != null) {
                    val delta = deltaFromUnit(n, unit)
                    if (delta != null && !delta.isZero()) {
                        remind = TaskSmartParseResult.ReminderSpec.OffsetAfter(delta)
                        text = stripToken(text, m.value)
                    }
                }
            }
        }

        if (remind == null) {
            // "1小时30分钟后"
            remindFromNowHmRegex.find(text)?.let { m ->
                val h = m.groupValues[1].toIntOrNull()?.coerceIn(1, 24 * 365) ?: return@let
                val mi = m.groupValues[2].toIntOrNull()?.coerceIn(0, 60 * 24 * 365) ?: 0
                val delta = TimeDelta(hours = h, minutes = mi)
                remind = TaskSmartParseResult.ReminderSpec.FromNow(delta)
                text = stripToken(text, m.value)
            }
        }

        if (remind == null) {
            // "5分钟后"
            remindFromNowMinutesRegex.find(text)?.let { m ->
                val n = m.groupValues[1].toIntOrNull()?.coerceIn(1, 60 * 24 * 365) ?: return@let
                val delta = TimeDelta(minutes = n)
                remind = TaskSmartParseResult.ReminderSpec.FromNow(delta)
                text = stripToken(text, m.value)
            }
        }

        if (remind == null) {
            // "3天后" / "2周后" / "1月后" / "1年后"
            remindFromNowUnitRegex.find(text)?.let { m ->
                val n = m.groupValues[1].toIntOrNull()?.coerceIn(1, 3650) ?: return@let
                val unit = m.groupValues[2]
                val delta =
                    when (unit) {
                        "天" -> TimeDelta(days = n)
                        "周" -> TimeDelta(days = n * 7)
                        "月" -> TimeDelta(months = n)
                        "年" -> TimeDelta(years = n)
                        else -> null
                    } ?: return@let
                remind = TaskSmartParseResult.ReminderSpec.FromNow(delta)
                text = stripToken(text, m.value)
            }
        }

        if (remind != null) {
            text = stripToken(text, "提醒我")
            text = stripToken(text, "提醒")
        }

        // If the user just wrote "提醒我" and we already have a due date/time, treat it as remind at due.
        if (remind == null && text.contains("提醒") && (dueDate != null || dueTime != null)) {
            remind = TaskSmartParseResult.ReminderSpec.AtDue
            text = stripToken(text, "提醒我")
            text = stripToken(text, "提醒")
        }

        val cleanedTitle =
            text.replace(Regex("""\s{2,}"""), " ")
                .trim()

        return TaskSmartParseResult(
            cleanedTitle = cleanedTitle.ifBlank { fallbackTitle.trim() },
            dueDate = dueDate,
            dueTime = if (allDay) null else dueTime,
            remind = remind,
            remindPersistent = remindPersistent,
            tags = tags,
            priority = priority,
            repeat = repeat?.trim()?.takeIf { it.isNotBlank() },
            allDay = allDay,
        )
    }

    fun findTokens(raw: String): List<SmartToken> {
        if (raw.isBlank()) return emptyList()

        val out = ArrayList<SmartToken>(12)
        fun add(range: IntRange, kind: SmartTokenKind) {
            val start = range.first
            val end = range.last + 1
            if (start < 0 || end <= start) return
            if (end > raw.length) return
            out += SmartToken(start = start, endExclusive = end, kind = kind)
        }

        // tags: highlight "#xxx" / "@xxx" only (exclude leading whitespace from the regex group)
        tagRegex.findAll(raw).forEach { m ->
            val tokenOffset = m.value.indexOfAny(charArrayOf('#', '@')).takeIf { it >= 0 } ?: return@forEach
            val start = m.range.first + tokenOffset
            val end = m.range.last + 1
            if (start in 0..end && end <= raw.length) {
                out += SmartToken(start = start, endExclusive = end, kind = SmartTokenKind.Tag)
            }
        }

        priorityRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Priority) }

        relativeDayRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Date) }
        weekdayRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Date) }
        dateYmdRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Date) }
        dateMdCnRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Date) }
        dateMdSepRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Date) }
        monthOnlyRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Date) }

        timeHmRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Time) }
        timeHalfRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Time) }
        timeHmsRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Time) }
        timeHourRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Time) }
        dayPeriodTokenRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Time) }

        repeatSimpleRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }
        repeatEveryDaysRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }
        repeatEveryWeeksRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }
        repeatEveryMonthsRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }
        repeatEveryYearsMonthDayRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }
        repeatEveryYearsMonthRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }
        repeatMonthlyDayRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }
        repeatMonthlyLastRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Repeat) }

        remindAtDueRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }
        remindPersistentRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }
        remindBeforeRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }
        remindAfterRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }
        remindFromNowHmRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }
        remindFromNowMinutesRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }
        remindFromNowUnitRegex.findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }
        Regex("""提醒我|提醒""").findAll(raw).forEach { m -> add(m.range, SmartTokenKind.Remind) }

        if (out.isEmpty()) return emptyList()
        val sorted = out.sortedWith(compareBy<SmartToken> { it.start }.thenBy { it.endExclusive })
        val merged = ArrayList<SmartToken>(sorted.size)
        var cur = sorted.first()
        for (i in 1 until sorted.size) {
            val n = sorted[i]
            if (n.start <= cur.endExclusive) {
                cur = cur.copy(endExclusive = maxOf(cur.endExclusive, n.endExclusive))
            } else {
                merged += cur
                cur = n
            }
        }
        merged += cur
        return merged
    }

    private data class DayPeriod(
        val matchedToken: String,
        val kind: Kind,
        val defaultTime: LocalTime?,
    ) {
        enum class Kind { Morning, Noon, Afternoon, Evening, Night, Dawn }
    }

    private fun detectDayPeriod(text: String): DayPeriod? {
        val candidates =
            listOf(
                "凌晨" to DayPeriod.Kind.Dawn,
                "早上" to DayPeriod.Kind.Morning,
                "早晨" to DayPeriod.Kind.Morning,
                "上午" to DayPeriod.Kind.Morning,
                "中午" to DayPeriod.Kind.Noon,
                "下午" to DayPeriod.Kind.Afternoon,
                "傍晚" to DayPeriod.Kind.Evening,
                "晚上" to DayPeriod.Kind.Night,
                "今晚" to DayPeriod.Kind.Night,
                "夜里" to DayPeriod.Kind.Night,
                "深夜" to DayPeriod.Kind.Night,
            )
        for ((token, kind) in candidates) {
            if (!text.contains(token)) continue
            val defaultTime =
                when (token) {
                    "早上", "早晨" -> LocalTime.of(7, 0)
                    "上午" -> LocalTime.of(9, 0)
                    "中午" -> LocalTime.of(12, 0)
                    "下午" -> LocalTime.of(13, 0)
                    "傍晚" -> LocalTime.of(17, 0)
                    "晚上", "今晚" -> LocalTime.of(20, 0)
                    "夜里", "深夜" -> LocalTime.of(22, 0)
                    "凌晨" -> LocalTime.of(1, 0)
                    else -> null
                }
            return DayPeriod(token, kind, defaultTime)
        }
        return null
    }

    private fun normalizeHourWithPeriod(hour: Int, minute: Int, period: DayPeriod?): LocalTime? {
        val h0 = hour.coerceIn(0, 23)
        val m0 = minute.coerceIn(0, 59)
        var h = h0
        when (period?.kind) {
            DayPeriod.Kind.Afternoon, DayPeriod.Kind.Evening, DayPeriod.Kind.Night -> {
                if (h in 1..11) h += 12
            }
            DayPeriod.Kind.Noon -> {
                // "中午1点" => 13:00
                if (h in 1..10) h += 12
            }
            else -> Unit
        }
        return runCatching { LocalTime.of(h, m0) }.getOrNull()
    }

    private fun parseWeekdayToken(token: String): DayOfWeek? =
        when (token) {
            "一" -> DayOfWeek.MONDAY
            "二" -> DayOfWeek.TUESDAY
            "三" -> DayOfWeek.WEDNESDAY
            "四" -> DayOfWeek.THURSDAY
            "五" -> DayOfWeek.FRIDAY
            "六" -> DayOfWeek.SATURDAY
            "日", "天" -> DayOfWeek.SUNDAY
            else -> null
        }

    private fun formatWeekdayShort(wd: DayOfWeek): String =
        when (wd) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }

    private fun nextOrSameWeekday(base: LocalDate, target: DayOfWeek, forceNextWeek: Boolean): LocalDate {
        val todayDow = base.dayOfWeek.value // 1..7
        val targetDow = target.value
        var delta = (targetDow - todayDow + 7) % 7
        if (forceNextWeek && delta == 0) delta = 7
        return base.plusDays(delta.toLong())
    }

    private fun nextOrSameWeekend(base: LocalDate): LocalDate {
        val dow = base.dayOfWeek
        return when (dow) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> base
            else -> nextOrSameWeekday(base, DayOfWeek.SATURDAY, forceNextWeek = false)
        }
    }

    private fun nextOrSameWorkday(base: LocalDate): LocalDate {
        val dow = base.dayOfWeek
        return when (dow) {
            DayOfWeek.SATURDAY -> base.plusDays(2)
            DayOfWeek.SUNDAY -> base.plusDays(1)
            else -> base
        }
    }

    private fun nextMonthlyDay(base: LocalDate, day: Int): LocalDate {
        val ym = YearMonth.from(base)
        val d = day.coerceIn(1, ym.lengthOfMonth())
        val candidate = ym.atDay(d)
        return if (!candidate.isBefore(base)) candidate else {
            val nextYm = ym.plusMonths(1)
            nextYm.atDay(day.coerceIn(1, nextYm.lengthOfMonth()))
        }
    }

    private fun nextMonthlyLastNDay(base: LocalDate, n: Int): LocalDate {
        val ym = YearMonth.from(base)
        fun lastDayOfMonth(y: YearMonth): LocalDate {
            val last = y.lengthOfMonth()
            val d = (last - (n - 1)).coerceAtLeast(1)
            return y.atDay(d)
        }
        val candidate = lastDayOfMonth(ym)
        return if (!candidate.isBefore(base)) candidate else lastDayOfMonth(ym.plusMonths(1))
    }

    private fun nextYearlyMonthDay(base: LocalDate, month: Int, day: Int): LocalDate {
        val mo = month.coerceIn(1, 12)
        fun mk(year: Int): LocalDate? {
            val ym = YearMonth.of(year, mo)
            val d = day.coerceIn(1, ym.lengthOfMonth())
            return runCatching { LocalDate.of(year, mo, d) }.getOrNull()
        }
        val y0 = base.year
        val candidate = mk(y0) ?: return base
        return if (!candidate.isBefore(base)) candidate else (mk(y0 + 1) ?: candidate)
    }

    private fun deltaFromUnit(n: Int, unit: String): TimeDelta? {
        val v = n.coerceIn(1, 3650)
        return when (unit) {
            "分钟", "分" -> TimeDelta(minutes = v)
            "小时" -> TimeDelta(hours = v)
            "天" -> TimeDelta(days = v)
            "周" -> TimeDelta(days = v * 7)
            "月" -> TimeDelta(months = v)
            "年" -> TimeDelta(years = v)
            else -> null
        }
    }

    private fun stripToken(text: String, token: String): String {
        if (token.isBlank()) return text
        return text.replace(token, " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }
}
