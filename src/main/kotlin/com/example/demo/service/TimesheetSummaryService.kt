package com.example.demo.service

import com.example.demo.mapper.TimesheetEntryMapper
import org.springframework.stereotype.Service
import java.time.YearMonth

@Service
class TimesheetSummaryService(
    private val timesheetEntryMapper: TimesheetEntryMapper
) {
    data class Summary(
        val userName: String,
        val yearMonth: String,
        val totalWorkingMinutes: Int,
        val totalBreakMinutes: Int,
        val averageWorkingMinutes: Double,
        val daysCount: Int
    )

    fun summarize(userName: String, ym: YearMonth): Summary {
        val from = ym.atDay(1)
        val to = ym.atEndOfMonth()
        val list = timesheetEntryMapper.selectByUserAndRange(userName, from, to)
        var totalWorking = 0
        var totalBreak = 0
        var countedDays = 0
        list.forEach { e ->
            val w = e.workingMinutes
            val b = e.breakMinutes
            if (w != null) totalWorking += w
            if (b != null) totalBreak += b
            if (e.startTime != null || e.endTime != null) countedDays++
        }
        val avg = if (countedDays > 0) totalWorking.toDouble() / countedDays else 0.0
        return Summary(userName, ym.toString(), totalWorking, totalBreak, avg, countedDays)
    }
}

