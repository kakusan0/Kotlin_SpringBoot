package com.example.demo.service

import com.example.demo.mapper.TimesheetEntryMapper
import com.example.demo.model.TimesheetEntry
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

class TimesheetConflictException(message: String) : RuntimeException(message)
class TimesheetNotFoundException(message: String) : RuntimeException(message)

@Service
class TimesheetService(
    private val timesheetEntryMapper: TimesheetEntryMapper,
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TimesheetService::class.java)
    }

    private fun applyCalc(entry: TimesheetEntry): TimesheetEntry {
        val eval = TimesheetEvaluator.evaluate(entry.startTime, entry.endTime, entry.breakMinutes)
        if (!eval.isValid) {
            logger.warn("Timesheet validation warnings for entry {}: {}", entry.id, eval.errors.joinToString(";"))
        }
        return entry.copy(durationMinutes = eval.durationMinutes, workingMinutes = eval.workingMinutes)
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
            return applyCalc(existing)
        }
        val entry = applyCalc(TimesheetEntry(workDate = today, userName = userName, startTime = now))
        timesheetEntryMapper.insert(entry)
        eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, today))
        return entry
    }

    @Transactional
    fun clockOut(userName: String, now: LocalTime = LocalTime.now()): TimesheetEntry {
        val today = LocalDate.now()
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, today)
            ?: throw TimesheetNotFoundException("本日のタイムシートがありません")
        if (existing.endTime != null) {
            return applyCalc(existing) // すでに終了
        }
        if (existing.startTime == null) {
            throw TimesheetConflictException("clock-in が未実施です")
        }
        val updated = applyCalc(existing.copy(endTime = now))
        timesheetEntryMapper.updateTimes(updated)
        eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, today))
        return updated
    }

    fun getToday(userName: String): TimesheetEntry? {
        val entry = timesheetEntryMapper.selectByUserAndDate(userName, LocalDate.now())
        return entry?.let { applyCalc(it) }
    }

    fun list(userName: String, from: LocalDate, to: LocalDate): List<TimesheetEntry> {
        require(!from.isAfter(to)) { "from は to より後ろにできません" }
        val entries = timesheetEntryMapper.selectByUserAndRange(userName, from, to)
        val calculatedEntries = ArrayList<TimesheetEntry>(entries.size)
        var invalidCount = 0
        for (e in entries) {
            val eval = TimesheetEvaluator.evaluate(e.startTime, e.endTime, e.breakMinutes)
            if (!eval.isValid) {
                invalidCount++
                logger.debug("Entry {} validation issues: {}", e.id, eval.errors.joinToString(";"))
            }
            calculatedEntries += e.copy(durationMinutes = eval.durationMinutes, workingMinutes = eval.workingMinutes)
        }
        logger.info(
            "Timesheet entries user={} range={}..{} total={} invalid={}",
            userName,
            from,
            to,
            entries.size,
            invalidCount
        )
        return calculatedEntries
    }

    @Transactional
    fun updateNote(userName: String, note: String): TimesheetEntry {
        val today = LocalDate.now()
        val existing = timesheetEntryMapper.selectByUserAndDate(userName, today)
            ?: throw TimesheetNotFoundException("本日のタイムシートがありません")
        timesheetEntryMapper.updateNote(existing.id!!, note)
        eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, today))
        return applyCalc(existing.copy(note = note))
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
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
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
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
            created
        }
    }
}
