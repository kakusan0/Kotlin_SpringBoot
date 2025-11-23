package com.example.demo.service

import com.example.demo.mapper.TimesheetEntryMapper
import com.example.demo.model.TimesheetEntry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

class TimesheetConflictException(message: String) : RuntimeException(message)
class TimesheetNotFoundException(message: String) : RuntimeException(message)

@Service
class TimesheetService(
    private val timesheetEntryMapper: TimesheetEntryMapper
) {
    // 稼働/実働再計算共通
    private fun recalc(entry: TimesheetEntry): TimesheetEntry {
        val st = entry.startTime
        val et = entry.endTime
        val breakMinRaw = entry.breakMinutes ?: 0
        var duration: Int? = null
        if (st != null && et != null) {
            val startTotal = st.hour * 60 + st.minute
            val endTotal = et.hour * 60 + et.minute
            duration = if (endTotal >= startTotal) {
                endTotal - startTotal
            } else {
                // 終了時刻が開始より前の場合は翌日跨ぎとみなし 24h 加算 (最大24h内勤務想定)
                val wrapped = endTotal + 1440 - startTotal
                // 異常 (24時間超) は null として扱う
                if (wrapped in 1..1440) wrapped else null
            }
        }
        // 休憩が負数なら 0 に矯正、稼働未確定時はそのまま
        val breakMin = if (breakMinRaw < 0) 0 else breakMinRaw
        val working = duration?.let { (it - breakMin).coerceAtLeast(0) }
        return entry.copy(durationMinutes = duration, workingMinutes = working, breakMinutes = breakMin)
    }

    @Transactional
    fun clockIn(userName: String, now: LocalTime = LocalTime.now()): TimesheetEntry {
        val today = LocalDate.now()
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, today)
        if (existing != null) {
            if (existing.startTime == null) {
                // 補完: startTime 未設定ならセット
                val updated = recalc(existing.copy(startTime = now))
                timesheetEntryMapper.updateTimes(updated)
                return updated
            }
            if (existing.endTime == null) {
                throw TimesheetConflictException("既に勤務中です: clock-out が必要")
            }
            // 当日完了済 -> そのまま返却
            return existing
        }
        val entry = recalc(TimesheetEntry(workDate = today, userName = userName, startTime = now))
        timesheetEntryMapper.insert(entry)
        return entry
    }

    @Transactional
    fun clockOut(userName: String, now: LocalTime = LocalTime.now()): TimesheetEntry {
        val today = LocalDate.now()
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, today)
            ?: throw TimesheetNotFoundException("本日のタイムシートがありません")
        if (existing.endTime != null) {
            return existing // すでに終了
        }
        if (existing.startTime == null) {
            throw TimesheetConflictException("clock-in が未実施です")
        }
        val updated = recalc(existing.copy(endTime = now))
        timesheetEntryMapper.updateTimes(updated)
        return updated
    }

    fun getToday(userName: String): TimesheetEntry? {
        return timesheetEntryMapper.selectByUserAndDate(userName, LocalDate.now())
    }

    fun list(userName: String, from: LocalDate, to: LocalDate): List<TimesheetEntry> {
        require(!from.isAfter(to)) { "from は to より後ろにできません" }
        return timesheetEntryMapper.selectByUserAndRange(userName, from, to)
    }

    @Transactional
    fun updateNote(userName: String, note: String): TimesheetEntry {
        val today = LocalDate.now()
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, today)
            ?: throw TimesheetNotFoundException("本日のタイムシートがありません")
        timesheetEntryMapper.updateNote(existing.id!!, note)
        return existing.copy(note = note)
    }

    @Transactional
    fun saveOrUpdate(
        userName: String,
        workDate: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        breakMinutes: Int? = null
    ): TimesheetEntry {
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, workDate)
        return if (existing != null) {
            val merged = existing.copy(
                startTime = startTime ?: existing.startTime,
                endTime = endTime ?: existing.endTime,
                breakMinutes = breakMinutes ?: existing.breakMinutes
            )
            val recalced = recalc(merged)
            timesheetEntryMapper.updateTimes(recalced)
            recalced
        } else {
            val createdBase = TimesheetEntry(
                workDate = workDate,
                userName = userName,
                startTime = startTime,
                endTime = endTime,
                breakMinutes = breakMinutes
            )
            val created = recalc(createdBase)
            timesheetEntryMapper.insert(created)
            created
        }
    }
}
