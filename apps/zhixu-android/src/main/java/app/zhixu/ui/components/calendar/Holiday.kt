package app.zhixu.ui.components.calendar

import java.time.LocalDate
import java.time.MonthDay

/**
 * 节假日类型
 */
enum class HolidayType {
    LEGAL_HOLIDAY,  // 法定节假日
    TRADITIONAL,    // 传统节日
    WORK_DAY,       // 调休工作日
}

/**
 * 工作日状态
 */
enum class WorkDayStatus {
    NONE,       // 普通工作日/周末
    REST,       // 休息（法定节假日或调休）
    WORK,       // 工作（调休的工作日）
}

/**
 * 节假日数据
 */
data class Holiday(
    val name: String,
    val type: HolidayType,
    val monthDay: MonthDay,
    val workDayStatus: WorkDayStatus = WorkDayStatus.NONE,
) {
    fun matches(date: LocalDate): Boolean {
        return monthDay.month == date.month && monthDay.dayOfMonth == date.dayOfMonth
    }
}

/**
 * 节假日数据提供者
 */
object HolidayProvider {

    // 固定日期的节假日（不包括农历节日）
    private val FIXED_HOLIDAYS = listOf(
        // 公历节假日
        Holiday("元旦", HolidayType.LEGAL_HOLIDAY, MonthDay.of(1, 1)),
        Holiday("情人节", HolidayType.TRADITIONAL, MonthDay.of(2, 14)),
        Holiday("妇女节", HolidayType.TRADITIONAL, MonthDay.of(3, 8)),
        Holiday("植树节", HolidayType.TRADITIONAL, MonthDay.of(3, 12)),
        Holiday("愚人节", HolidayType.TRADITIONAL, MonthDay.of(4, 1)),
        Holiday("劳动节", HolidayType.LEGAL_HOLIDAY, MonthDay.of(5, 1)),
        Holiday("青年节", HolidayType.TRADITIONAL, MonthDay.of(5, 4)),
        Holiday("儿童节", HolidayType.TRADITIONAL, MonthDay.of(6, 1)),
        Holiday("建党节", HolidayType.TRADITIONAL, MonthDay.of(7, 1)),
        Holiday("建军节", HolidayType.TRADITIONAL, MonthDay.of(8, 1)),
        Holiday("教师节", HolidayType.TRADITIONAL, MonthDay.of(9, 10)),
        Holiday("国庆节", HolidayType.LEGAL_HOLIDAY, MonthDay.of(10, 1)),
        Holiday("万圣节", HolidayType.TRADITIONAL, MonthDay.of(10, 31)),
        Holiday("平安夜", HolidayType.TRADITIONAL, MonthDay.of(12, 24)),
        Holiday("圣诞节", HolidayType.TRADITIONAL, MonthDay.of(12, 25)),
    )

    /**
     * 农历节日（需要根据农历日期判断）
     */
    fun getLunarHoliday(lunarDate: LunarDate): String? {
        return when {
            lunarDate.month == 1 && lunarDate.day == 1 -> "春节"
            lunarDate.month == 1 && lunarDate.day == 15 -> "元宵"
            lunarDate.month == 5 && lunarDate.day == 5 -> "端午"
            lunarDate.month == 7 && lunarDate.day == 7 -> "七夕"
            lunarDate.month == 8 && lunarDate.day == 15 -> "中秋"
            lunarDate.month == 9 && lunarDate.day == 9 -> "重阳"
            lunarDate.month == 12 && lunarDate.day == 8 -> "腊八"
            lunarDate.month == 12 && lunarDate.day == 23 -> "北小年"
            lunarDate.month == 12 && lunarDate.day == 24 -> "南小年"
            // 除夕需要特殊处理（农历年最后一天）
            else -> null
        }
    }

    /**
     * 获取指定日期的节假日信息
     */
    fun getHoliday(date: LocalDate): Holiday? {
        return FIXED_HOLIDAYS.firstOrNull { it.matches(date) }
    }

    /**
     * 获取工作日状态
     * 注意：实际应用中应该从服务器或本地配置加载每年的调休安排
     */
    fun getWorkDayStatus(date: LocalDate, year: Int): WorkDayStatus {
        // 示例：2026年的调休安排（实际应用中应该动态配置）
        if (year == 2026) {
            return when {
                // 元旦：1月1-3日休息
                date.monthValue == 1 && date.dayOfMonth in 1..3 -> WorkDayStatus.REST

                // 春节：2月15日-23日休息，2月14日(六)、2月28日(六)上班
                date.monthValue == 2 && date.dayOfMonth == 14 -> WorkDayStatus.WORK
                date.monthValue == 2 && date.dayOfMonth in 15..23 -> WorkDayStatus.REST
                date.monthValue == 2 && date.dayOfMonth == 28 -> WorkDayStatus.WORK

                // 清明节：4月4-6日休息
                date.monthValue == 4 && date.dayOfMonth in 4..6 -> WorkDayStatus.REST

                // 劳动节：5月1-5日休息，4月26日(日)、5月9日(六)上班
                date.monthValue == 4 && date.dayOfMonth == 26 -> WorkDayStatus.WORK
                date.monthValue == 5 && date.dayOfMonth in 1..5 -> WorkDayStatus.REST
                date.monthValue == 5 && date.dayOfMonth == 9 -> WorkDayStatus.WORK

                // 端午节：5月30日-6月1日休息
                date.monthValue == 5 && date.dayOfMonth in 30..31 -> WorkDayStatus.REST
                date.monthValue == 6 && date.dayOfMonth == 1 -> WorkDayStatus.REST

                // 中秋节+国庆节：10月1-8日休息，9月27日(日)、10月10日(六)上班
                date.monthValue == 9 && date.dayOfMonth == 27 -> WorkDayStatus.WORK
                date.monthValue == 10 && date.dayOfMonth in 1..8 -> WorkDayStatus.REST
                date.monthValue == 10 && date.dayOfMonth == 10 -> WorkDayStatus.WORK

                else -> WorkDayStatus.NONE
            }
        }

        // 其他年份返回默认值
        return WorkDayStatus.NONE
    }

    /**
     * 获取所有节假日
     */
    fun getAllHolidays(): List<Holiday> = FIXED_HOLIDAYS
}

/**
 * 日期单元格数据模型
 */
data class DayModel(
    val date: LocalDate,
    val lunarDate: LunarDate,
    val solarTerm: SolarTerm? = null,
    val holiday: Holiday? = null,
    val lunarHoliday: String? = null,
    val workDayStatus: WorkDayStatus = WorkDayStatus.NONE,
    val isCurrentMonth: Boolean = true,
    val isToday: Boolean = false,
) {
    /**
     * 获取显示在日期下方的文本（优先级：节气 > 公历节日 > 农历节日 > 农历日期）
     */
    val displayText: String
        get() = when {
            solarTerm != null -> solarTerm.displayName
            holiday != null -> holiday.name
            lunarHoliday != null -> lunarHoliday
            lunarDate.day == 1 -> lunarDate.getMonthText()
            else -> ""
        }

    /**
     * 获取农历显示文本（始终显示）
     * 优先级：节气 > 公历节日 > 农历节日 > 农历日期
     */
    fun getLunarDisplayText(): String {
        return when {
            solarTerm != null -> solarTerm.displayName
            holiday != null -> holiday.name
            lunarHoliday != null -> lunarHoliday
            else -> lunarDate.getDayText().ifBlank { lunarDate.getMonthText() }
        }
    }

    /**
     * 文本颜色（节日使用特殊颜色）
     */
    val textColorType: DayTextColorType
        get() = when {
            solarTerm != null -> DayTextColorType.SOLAR_TERM
            holiday != null && holiday.type == HolidayType.LEGAL_HOLIDAY -> DayTextColorType.LEGAL_HOLIDAY
            lunarHoliday != null -> DayTextColorType.LUNAR_HOLIDAY
            else -> DayTextColorType.NORMAL
        }
}

/**
 * 日期文本颜色类型
 */
enum class DayTextColorType {
    NORMAL,          // 普通
    SOLAR_TERM,      // 节气
    LEGAL_HOLIDAY,   // 法定节假日
    LUNAR_HOLIDAY,   // 农历节日
}
