package app.zhixu.ui.components.calendar

import java.time.LocalDate

/**
 * 农历日期数据类
 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean = false,
) {
    /**
     * 获取农历日期显示文本
     */
    fun getDayText(): String {
        return when (day) {
            1 -> "初一"
            2 -> "初二"
            3 -> "初三"
            4 -> "初四"
            5 -> "初五"
            6 -> "初六"
            7 -> "初七"
            8 -> "初八"
            9 -> "初九"
            10 -> "初十"
            11 -> "十一"
            12 -> "十二"
            13 -> "十三"
            14 -> "十四"
            15 -> "十五"
            16 -> "十六"
            17 -> "十七"
            18 -> "十八"
            19 -> "十九"
            20 -> "二十"
            21 -> "廿一"
            22 -> "廿二"
            23 -> "廿三"
            24 -> "廿四"
            25 -> "廿五"
            26 -> "廿六"
            27 -> "廿七"
            28 -> "廿八"
            29 -> "廿九"
            30 -> "三十"
            else -> ""
        }
    }

    /**
     * 获取农历月份显示文本
     */
    fun getMonthText(): String {
        val monthName = when (month) {
            1 -> "正月"
            2 -> "二月"
            3 -> "三月"
            4 -> "四月"
            5 -> "五月"
            6 -> "六月"
            7 -> "七月"
            8 -> "八月"
            9 -> "九月"
            10 -> "十月"
            11 -> "冬月"
            12 -> "腊月"
            else -> ""
        }
        return if (isLeapMonth) "闰$monthName" else monthName
    }
}

/**
 * 节气枚举
 */
enum class SolarTerm(val displayName: String) {
    LICHUN("立春"),
    YUSHUI("雨水"),
    JINGZHE("惊蛰"),
    CHUNFEN("春分"),
    QINGMING("清明"),
    GUYU("谷雨"),
    LIXIA("立夏"),
    XIAOMAN("小满"),
    MANGZHONG("芒种"),
    XIAZHI("夏至"),
    XIAOSHU("小暑"),
    DASHU("大暑"),
    LIQIU("立秋"),
    CHUSHU("处暑"),
    BAILU("白露"),
    QIUFEN("秋分"),
    HANLU("寒露"),
    SHUANGJIANG("霜降"),
    LIDONG("立冬"),
    XIAOXUE("小雪"),
    DAXUE("大雪"),
    DONGZHI("冬至"),
    XIAOHAN("小寒"),
    DAHAN("大寒");
}

/**
 * 农历计算器
 * 基于查表法实现，支持 1900-2100 年
 */
object LunarCalendarCalculator {

    // 农历年份信息表：每个元素表示一年的农历信息
    // 低12位：12个月的大小月（1=大月30天，0=小月29天）
    // 高4位：闰月的月份（0=无闰月，1-12=闰几月）
    // 如果有闰月，第13-16位表示闰月大小（1=30天，0=29天）
    private val LUNAR_INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520 // 2100
    )

    // 1900年1月31日是农历1900年正月初一
    private val BASE_DATE = LocalDate.of(1900, 1, 31)
    private const val BASE_YEAR = 1900

    /**
     * 获取农历年份的天数
     */
    private fun getLunarYearDays(lunarYear: Int): Int {
        var sum = 348 // 12个月 * 29天
        for (i in 0x8000 downTo 0x8) {
            sum += if (LUNAR_INFO[lunarYear - BASE_YEAR] and i.toLong() != 0L) 1 else 0
        }
        return sum + getLeapMonthDays(lunarYear)
    }

    /**
     * 获取农历年份的闰月天数
     */
    private fun getLeapMonthDays(lunarYear: Int): Int {
        if (getLeapMonth(lunarYear) == 0) return 0
        return if (LUNAR_INFO[lunarYear - BASE_YEAR] and 0x10000 != 0L) 30 else 29
    }

    /**
     * 获取农历年份的闰月月份（0表示无闰月）
     */
    private fun getLeapMonth(lunarYear: Int): Int {
        return ((LUNAR_INFO[lunarYear - BASE_YEAR] and 0xf00000) shr 20).toInt()
    }

    /**
     * 获取农历月份的天数
     */
    private fun getLunarMonthDays(lunarYear: Int, lunarMonth: Int): Int {
        return if (LUNAR_INFO[lunarYear - BASE_YEAR] and (0x10000 shr lunarMonth).toLong() == 0L) 29 else 30
    }

    /**
     * 将公历日期转换为农历日期
     */
    fun solarToLunar(date: LocalDate): LunarDate {
        if (date.year < 1900 || date.year > 2100) {
            // 超出范围，返回默认值
            return LunarDate(date.year, 1, 1)
        }

        var offset = java.time.temporal.ChronoUnit.DAYS.between(BASE_DATE, date).toInt()
        var lunarYear = BASE_YEAR
        var daysInYear: Int

        // 计算农历年份
        while (lunarYear < 2100) {
            daysInYear = getLunarYearDays(lunarYear)
            if (offset < daysInYear) break
            offset -= daysInYear
            lunarYear++
        }

        // 计算闰月
        val leapMonth = getLeapMonth(lunarYear)
        var isLeap = false
        var lunarMonth = 1
        var daysInMonth: Int

        // 计算农历月份
        while (lunarMonth <= 12) {
            if (leapMonth > 0 && lunarMonth == leapMonth + 1 && !isLeap) {
                // 当前月是闰月
                lunarMonth--
                isLeap = true
                daysInMonth = getLeapMonthDays(lunarYear)
            } else {
                daysInMonth = getLunarMonthDays(lunarYear, lunarMonth)
            }

            if (offset < daysInMonth) break

            offset -= daysInMonth

            if (isLeap) {
                isLeap = false
            }
            lunarMonth++
        }

        val lunarDay = offset + 1

        return LunarDate(lunarYear, lunarMonth, lunarDay, isLeap)
    }

    /**
     * 获取节气（单日命中）
     */
    fun getSolarTerm(date: LocalDate): SolarTerm? {
        val year = date.year
        val rules =
            listOf(
                TermRule(SolarTerm.XIAOHAN, 1, 6.11, 5.4055),
                TermRule(SolarTerm.DAHAN, 1, 20.84, 20.12),
                TermRule(SolarTerm.LICHUN, 2, 4.6295, 3.87),
                TermRule(SolarTerm.YUSHUI, 2, 19.4599, 18.73),
                TermRule(SolarTerm.JINGZHE, 3, 6.3826, 5.63),
                TermRule(SolarTerm.CHUNFEN, 3, 21.4155, 20.646),
                TermRule(SolarTerm.QINGMING, 4, 5.59, 4.81),
                TermRule(SolarTerm.GUYU, 4, 20.888, 20.1),
                TermRule(SolarTerm.LIXIA, 5, 6.318, 5.52),
                TermRule(SolarTerm.XIAOMAN, 5, 21.86, 21.04),
                TermRule(SolarTerm.MANGZHONG, 6, 6.5, 5.678),
                TermRule(SolarTerm.XIAZHI, 6, 22.20, 21.37),
                TermRule(SolarTerm.XIAOSHU, 7, 7.928, 7.108),
                TermRule(SolarTerm.DASHU, 7, 23.65, 22.83),
                TermRule(SolarTerm.LIQIU, 8, 8.35, 7.5),
                TermRule(SolarTerm.CHUSHU, 8, 23.95, 23.13),
                TermRule(SolarTerm.BAILU, 9, 8.44, 7.646),
                TermRule(SolarTerm.QIUFEN, 9, 23.822, 23.042),
                TermRule(SolarTerm.HANLU, 10, 9.098, 8.318),
                TermRule(SolarTerm.SHUANGJIANG, 10, 24.218, 23.438),
                TermRule(SolarTerm.LIDONG, 11, 8.218, 7.438),
                TermRule(SolarTerm.XIAOXUE, 11, 23.08, 22.36),
                TermRule(SolarTerm.DAXUE, 12, 7.9, 7.18),
                TermRule(SolarTerm.DONGZHI, 12, 22.60, 21.94),
            )

        for (r in rules) {
            if (r.month != date.monthValue) continue
            if (date.dayOfMonth == r.dayOfMonth(year)) return r.term
        }
        return null
    }

    private data class TermRule(
        val term: SolarTerm,
        val month: Int,
        val c20: Double,
        val c21: Double,
    ) {
        fun dayOfMonth(year: Int): Int {
            val y = year % 100
            val c = if (year <= 2000) c20 else c21
            return (y * 0.2422 + c).toInt() - ((y - 1) / 4)
        }
    }
}
