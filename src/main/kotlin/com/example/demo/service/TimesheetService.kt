package com.example.demo.service

import com.example.demo.mapper.TimesheetEntryMapper
import com.example.demo.model.TimesheetEntry
import org.springframework.dao.DuplicateKeyException
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
    @Transactional
    fun clockIn(userName: String, now: LocalTime = LocalTime.now()): TimesheetEntry {
        val today = LocalDate.now()
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, today)
        if (existing != null) {
            if (existing.startTime == null) {
                // 補完: startTime 未設定ならセット
                val updated = existing.copy(startTime = now)
                timesheetEntryMapper.updateTimes(updated)
                return updated
            }
            if (existing.endTime == null) {
                throw TimesheetConflictException("既に勤務中です: clock-out が必要")
            }
            // 当日完了済 -> そのまま返却
            return existing
        }
        val entry = TimesheetEntry(
            workDate = today,
            userName = userName,
            startTime = now
        )
        return try {
            timesheetEntryMapper.insert(entry)
            entry
        } catch (e: DuplicateKeyException) {
            // 競合（同時INSERT）時は再取得して整合状態に合わせる
            val latest = timesheetEntryMapper.selectByUserAndDate(userName, today)
                ?: throw e
            if (latest.endTime == null && latest.startTime != null) {
                latest
            } else if (latest.startTime == null) {
                val updated = latest.copy(startTime = now)
                timesheetEntryMapper.updateTimes(updated)
                updated
            } else {
                latest
            }
        }
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
        val updated = existing.copy(endTime = now)
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
        endTime: LocalTime?
    ): TimesheetEntry {
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, workDate)

        if (existing != null) {
            // 既存レコードを更新
            val updated = existing.copy(
                startTime = startTime ?: existing.startTime,
                endTime = endTime ?: existing.endTime
            )
            timesheetEntryMapper.updateTimes(updated)
            return updated
        } else {
            // 新規作成
            val entry = TimesheetEntry(
                workDate = workDate,
                userName = userName,
                startTime = startTime,
                endTime = endTime
            )
            timesheetEntryMapper.insert(entry)
            return entry
        }
    }
}
