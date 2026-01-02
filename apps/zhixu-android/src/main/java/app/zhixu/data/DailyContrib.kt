package com.zhixu.android.data

import java.time.LocalDate

data class DailyContrib(
    val day: LocalDate,
    val docsEdited: Int,
    val tasksDone: Int,
) {
    val total: Int get() = docsEdited + tasksDone
}

