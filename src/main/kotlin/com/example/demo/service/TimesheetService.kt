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
    private fun applyCalc(entry: TimesheetEntry): TimesheetEntry {
        val result = TimesheetValidator.validate(entry.startTime, entry.endTime, entry.breakMinutes)
        if (!result.isValid) throw TimesheetValidationException(result.errors.joinToString(";"))
        val calc = TimesheetCalculation.compute(entry.startTime, entry.endTime, entry.breakMinutes)
        return entry.copy(durationMinutes = calc.durationMinutes, workingMinutes = calc.workingMinutes)
    }

    @Transactional
    fun clockIn(userName: String, now: LocalTime = LocalTime.now()): TimesheetEntry {
        val today = LocalDate.now()
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, today)
        if (existing != null) {
            if (existing.startTime == null) {
                // 補完: startTime 未設定ならセット
                val updated = applyCalc(existing.copy(startTime = now))
                timesheetEntryMapper.updateTimes(updated)
                return updated
            }
            if (existing.endTime == null) {
                throw TimesheetConflictException("既に勤務中です: clock-out が必要")
            }
            // 当日完了済 -> そのまま返却
            return existing
        }
        val entry = applyCalc(TimesheetEntry(workDate = today, userName = userName, startTime = now))
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
        val updated = applyCalc(existing.copy(endTime = now))
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
            val recalced = applyCalc(merged)
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
            val created = applyCalc(createdBase)
            timesheetEntryMapper.insert(created)
            created
        }
    }
}
