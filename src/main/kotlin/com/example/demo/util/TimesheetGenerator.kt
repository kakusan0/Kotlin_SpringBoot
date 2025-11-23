package com.example.demo.util

import java.time.LocalDate
import java.time.YearMonth
import java.util.*

object TimesheetGenerator {
    fun generateDates(yearMonth: YearMonth): List<LocalDate> =
        (1..yearMonth.lengthOfMonth()).map { day -> yearMonth.atDay(day) }

    fun formatYearMonth(yearMonth: YearMonth, locale: Locale = Locale.JAPAN): String =
        "%d年%d月".format(locale, yearMonth.year, yearMonth.monthValue)
}

