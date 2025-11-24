package com.example.demo.service

import com.example.demo.mapper.TimesheetEntryMapper
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

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

    private data class Cached(val summary: Summary, val cachedAtMillis: Long)

    // TTL (ms)
    private val ttlMillis = 60_000L // 60秒キャッシュ
    private val cache = ConcurrentHashMap<String, Cached>()

    private fun key(user: String, ym: YearMonth) = "$user:$ym"
    fun summarize(userName: String, ym: YearMonth): Summary {
        val k = key(userName, ym)
        val now = System.currentTimeMillis()
        val cached = cache[k]
        if (cached != null && now - cached.cachedAtMillis < ttlMillis) {
            return cached.summary
        }
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
        val summary = Summary(userName, ym.toString(), totalWorking, totalBreak, avg, countedDays)
        cache[k] = Cached(summary, now)
        return summary
    }

    fun invalidate(userName: String, date: LocalDate) {
        val ym = YearMonth.from(date)
        cache.remove(key(userName, ym))
    }

    @EventListener
    fun onTimesheetUpdated(ev: TimesheetUpdatedEvent) {
        invalidate(ev.userName, ev.date)
    }
}
