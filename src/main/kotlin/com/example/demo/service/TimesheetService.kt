package com.example.demo.service

import com.example.demo.mapper.TimesheetEntryMapper
import com.example.demo.model.TimesheetEntry
import com.example.demo.util.dbCall
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
        val existing =
            dbCall("selectByUserAndDate", userName, today) { timesheetEntryMapper.selectByUserAndDate(userName, today) }
        if (existing != null) {
            if (existing.startTime == null) {
                // 補完: startTime 未設定ならセット
                val updated = applyCalc(existing.copy(startTime = now))
                val updatedCount =
                    dbCall("updateTimes", userName, today, existing.id) { timesheetEntryMapper.updateTimes(updated) }
                if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
                return updated
            }
            if (existing.endTime == null) {
                throw TimesheetConflictException("既に勤務中です: clock-out が必要")
            }
            // 当日完了済 -> そのまま返却
            return applyCalc(existing)
        }
        val entry = applyCalc(TimesheetEntry(workDate = today, userName = userName, startTime = now))
        try {
            dbCall("insert", userName, today) { timesheetEntryMapper.insert(entry) }
        } catch (ex: Exception) {
            // 競合で同一 user+date が挿入されていた場合、既存レコードを取得して再試行
            val nowExisting = dbCall(
                "selectByUserAndDate (insert-catch)",
                userName,
                today
            ) { timesheetEntryMapper.selectByUserAndDate(userName, today) }
                ?: throw ex
            if (nowExisting.startTime == null) {
                val updated = applyCalc(nowExisting.copy(startTime = now))
                val updatedCount = dbCall(
                    "updateTimes (insert-catch)",
                    userName,
                    today,
                    nowExisting.id
                ) { timesheetEntryMapper.updateTimes(updated) }
                if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
                return updated
            }
            if (nowExisting.endTime == null) {
                throw TimesheetConflictException("既に勤務中です: clock-out が必要")
            }
            return applyCalc(nowExisting)
        }
        eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, today))
        return entry
    }

    @Transactional
    fun clockOut(userName: String, now: LocalTime = LocalTime.now()): TimesheetEntry {
        val today = LocalDate.now()
        val existing = dbCall("selectByUserAndDate", userName, today) {
            timesheetEntryMapper.selectByUserAndDate(userName, today)
        } ?: throw TimesheetNotFoundException("本日のタイムシートがありません")

        if (existing.endTime != null) {
            // Already clocked out
            return applyCalc(existing)
        }
        if (existing.startTime == null) {
            throw TimesheetConflictException("clock-in が未実施です")
        }

        val updated = applyCalc(existing.copy(endTime = now))
        val updatedCount =
            dbCall("updateTimes", userName, today, existing.id) { timesheetEntryMapper.updateTimes(updated) }
        if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
        eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, today))
        return updated
    }

    fun getToday(userName: String): TimesheetEntry? {
        val entry = dbCall(
            "selectByUserAndDate",
            userName,
            LocalDate.now()
        ) { timesheetEntryMapper.selectByUserAndDate(userName, LocalDate.now()) }
        return entry?.let { applyCalc(it) }
    }

    fun list(userName: String, from: LocalDate, to: LocalDate): List<TimesheetEntry> {
        require(!from.isAfter(to)) { "from は to より後ろにできません" }
        val entries = dbCall("selectByUserAndRange", userName, from, to) {
            timesheetEntryMapper.selectByUserAndRange(
                userName,
                from,
                to
            )
        }
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
        val existing =
            dbCall("selectByUserAndDate", userName, today) { timesheetEntryMapper.selectByUserAndDate(userName, today) }
                ?: throw TimesheetNotFoundException("本日のタイムシートがありません")
        dbCall("updateNote", existing.id, userName, today) { timesheetEntryMapper.updateNote(existing.id!!, note) }
        eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, today))
        return applyCalc(existing.copy(note = note))
    }

    @Transactional
    fun addNoteToEntry(userName: String, note: String): TimesheetEntry {
        val today = LocalDate.now()
        val existing = dbCall("selectByUserAndDate", userName, today) {
            timesheetEntryMapper.selectByUserAndDate(userName, today)
        } ?: throw TimesheetNotFoundException("No timesheet entry found for today")

        dbCall("updateNote", existing.id, userName, today) {
            timesheetEntryMapper.updateNote(existing.id!!, note)
        }
        eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, today))
        return applyCalc(existing.copy(note = note))
    }

    @Transactional
    fun saveOrUpdate(
        userName: String,
        workDate: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        breakMinutes: Int? = null,
        force: Boolean = false,
        holidayWork: Boolean = false,
        workLocation: String? = null
    ): TimesheetEntry {
        val existing = dbCall("selectByUserAndDate", userName, workDate) {
            timesheetEntryMapper.selectByUserAndDate(
                userName,
                workDate
            )
        }
        return if (existing != null) {
            val merged = existing.copy(
                startTime = startTime ?: existing.startTime,
                endTime = endTime ?: existing.endTime,
                breakMinutes = breakMinutes ?: existing.breakMinutes,
                holidayWork = holidayWork,
                workLocation = workLocation ?: existing.workLocation
            )
            val recalced = applyCalc(merged)
            val updatedCount = dbCall("updateTimes/updateTimesForce", recalced.id, userName, workDate) {
                if (force) timesheetEntryMapper.updateTimesForce(recalced) else timesheetEntryMapper.updateTimes(
                    recalced
                )
            }
            if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
            recalced
        } else {
            val createdBase = TimesheetEntry(
                workDate = workDate,
                userName = userName,
                startTime = startTime,
                endTime = endTime,
                breakMinutes = breakMinutes,
                holidayWork = holidayWork,
                workLocation = workLocation
            )
            val created = applyCalc(createdBase)
            try {
                dbCall("insert", userName, workDate) { timesheetEntryMapper.insert(created) }
            } catch (ex: Exception) {
                // 競合で既にレコードが挿入されていた場合は、再取得して update を試みる
                val nowExisting = dbCall(
                    "selectByUserAndDate (insert-catch)",
                    userName,
                    workDate
                ) { timesheetEntryMapper.selectByUserAndDate(userName, workDate) }
                    ?: throw ex
                val merged = nowExisting.copy(
                    startTime = startTime ?: nowExisting.startTime,
                    endTime = endTime ?: nowExisting.endTime,
                    breakMinutes = breakMinutes ?: nowExisting.breakMinutes,
                    holidayWork = holidayWork,
                    workLocation = workLocation ?: nowExisting.workLocation
                )
                val recalced = applyCalc(merged)
                val updatedCount =
                    dbCall("updateTimes/updateTimesForce (insert-catch)", recalced.id, userName, workDate) {
                        if (force) timesheetEntryMapper.updateTimesForce(recalced) else timesheetEntryMapper.updateTimes(
                            recalced
                        )
                    }
                if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
                eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
                return recalced
            }
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
            created
        }
    }

    /**
     * Variant of saveOrUpdate that accepts explicit "provided" flags for start/end/break.
     * When a provided flag is true, the corresponding value (which may be null) will be used
     * (allowing callers to explicitly clear a field by providing null). When the provided flag
     * is false, the existing DB value is preserved.
     */
    @Transactional
    fun saveOrUpdateWithFlags(
        userName: String,
        workDate: LocalDate,
        startProvided: Boolean,
        startTime: LocalTime?,
        endProvided: Boolean,
        endTime: LocalTime?,
        breakProvided: Boolean,
        breakMinutes: Int?,
        force: Boolean = false,
        holidayWork: Boolean = false,
        workLocation: String? = null
    ): TimesheetEntry {
        val existing = dbCall("selectByUserAndDate", userName, workDate) {
            timesheetEntryMapper.selectByUserAndDate(
                userName,
                workDate
            )
        }
        return if (existing != null) {
            val newStart = if (startProvided) startTime else existing.startTime
            val newEnd = if (endProvided) endTime else existing.endTime
            val newBreak = if (breakProvided) breakMinutes else existing.breakMinutes
            val newHoliday = holidayWork
            val newWorkLocation = workLocation ?: existing.workLocation

            // If nothing changed, avoid DB write and just return evaluated existing
            val nothingChanged =
                (existing.startTime == newStart) && (existing.endTime == newEnd) && (existing.breakMinutes == newBreak) && (existing.holidayWork == newHoliday) && (existing.workLocation == newWorkLocation)
            if (nothingChanged && !force) {
                return applyCalc(existing)
            }

            val merged = existing.copy(
                startTime = newStart,
                endTime = newEnd,
                breakMinutes = newBreak,
                holidayWork = newHoliday,
                workLocation = newWorkLocation
            )
            val recalced = applyCalc(merged)
            val updatedCount = dbCall("updateTimes/updateTimesForce", recalced.id, userName, workDate) {
                if (force) timesheetEntryMapper.updateTimesForce(recalced) else timesheetEntryMapper.updateTimes(
                    recalced
                )
            }
            if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
            recalced
        } else {
            val createdBase = TimesheetEntry(
                workDate = workDate,
                userName = userName,
                startTime = if (startProvided) startTime else null,
                endTime = if (endProvided) endTime else null,
                breakMinutes = if (breakProvided) breakMinutes else null,
                holidayWork = holidayWork,
                workLocation = workLocation
            )
            val created = applyCalc(createdBase)
            try {
                dbCall("insert", userName, workDate) { timesheetEntryMapper.insert(created) }
            } catch (ex: Exception) {
                val nowExisting = dbCall(
                    "selectByUserAndDate (insert-catch)",
                    userName,
                    workDate
                ) { timesheetEntryMapper.selectByUserAndDate(userName, workDate) }
                    ?: throw ex
                val merged = nowExisting.copy(
                    startTime = if (startProvided) startTime else nowExisting.startTime,
                    endTime = if (endProvided) endTime else nowExisting.endTime,
                    breakMinutes = if (breakProvided) breakMinutes else nowExisting.breakMinutes,
                    holidayWork = holidayWork,
                    workLocation = workLocation ?: nowExisting.workLocation
                )
                val recalced = applyCalc(merged)
                val updatedCount =
                    dbCall("updateTimes/updateTimesForce (insert-catch)", recalced.id, userName, workDate) {
                        if (force) timesheetEntryMapper.updateTimesForce(recalced) else timesheetEntryMapper.updateTimes(
                            recalced
                        )
                    }
                if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
                eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
                return recalced
            }
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
            created
        }
    }

    /**
     * Variant of saveOrUpdate that accepts explicit "provided" flags for start/end/break.
     * When a provided flag is true, the corresponding value (which may be null) will be used
     * (allowing callers to explicitly clear a field by providing null). When the provided flag
     * is false, the existing DB value is preserved.
     *
     * For extended fields (irregularWorkType, lateTime, etc.):
     * - Pass the actual value to update
     * - Pass empty string "" to clear the field
     * - Pass null to keep existing value
     */
    @Transactional
    fun saveOrUpdateWithFlags(
        userName: String,
        workDate: LocalDate,
        startProvided: Boolean,
        startTime: LocalTime?,
        endProvided: Boolean,
        endTime: LocalTime?,
        breakProvided: Boolean,
        breakMinutes: Int?,
        force: Boolean = false,
        holidayWork: Boolean = false,
        noteProvided: Boolean,
        note: String?,
        workLocation: String? = null,
        irregularWorkType: String? = null,
        irregularWorkDesc: String? = null,
        irregularWorkData: String? = null,
        lateTime: String? = null,
        lateDesc: String? = null,
        earlyTime: String? = null,
        earlyDesc: String? = null,
        paidLeave: String? = null,
        // フラグ: 明示的にクリアするかどうか
        clearIrregular: Boolean = false,
        clearLate: Boolean = false,
        clearEarly: Boolean = false
    ): TimesheetEntry {
        val existing = dbCall("selectByUserAndDate", userName, workDate) {
            timesheetEntryMapper.selectByUserAndDate(userName, workDate)
        }
        return if (existing != null) {
            val merged = existing.copy(
                startTime = if (startProvided) startTime else existing.startTime,
                endTime = if (endProvided) endTime else existing.endTime,
                breakMinutes = if (breakProvided) breakMinutes else existing.breakMinutes,
                holidayWork = holidayWork,
                note = if (noteProvided) note else existing.note,
                workLocation = workLocation ?: existing.workLocation,
                // 拡張フィールド: clearフラグがtrueならnull、そうでなければ値があれば更新、なければ既存値
                irregularWorkType = if (clearIrregular) null else (irregularWorkType ?: existing.irregularWorkType),
                irregularWorkDesc = if (clearIrregular) null else (irregularWorkDesc ?: existing.irregularWorkDesc),
                irregularWorkData = if (clearIrregular) null else (irregularWorkData ?: existing.irregularWorkData),
                lateTime = if (clearLate) null else (lateTime ?: existing.lateTime),
                lateDesc = if (clearLate) null else (lateDesc ?: existing.lateDesc),
                earlyTime = if (clearEarly) null else (earlyTime ?: existing.earlyTime),
                earlyDesc = if (clearEarly) null else (earlyDesc ?: existing.earlyDesc),
                paidLeave = paidLeave ?: existing.paidLeave
            )
            val recalced = applyCalc(merged)
            val updatedCount = dbCall("updateTimes/updateTimesForce", recalced.id, userName, workDate) {
                if (force) timesheetEntryMapper.updateTimesForce(recalced) else timesheetEntryMapper.updateTimes(
                    recalced
                )
            }
            if (updatedCount == 0) throw TimesheetConflictException("同時更新により保存できませんでした")
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
            recalced
        } else {
            val createdBase = TimesheetEntry(
                workDate = workDate,
                userName = userName,
                startTime = startTime,
                endTime = endTime,
                breakMinutes = breakMinutes,
                holidayWork = holidayWork,
                note = note,
                workLocation = workLocation,
                irregularWorkType = irregularWorkType,
                irregularWorkDesc = irregularWorkDesc,
                irregularWorkData = irregularWorkData,
                lateTime = lateTime,
                lateDesc = lateDesc,
                earlyTime = earlyTime,
                earlyDesc = earlyDesc,
                paidLeave = paidLeave
            )
            val created = applyCalc(createdBase)
            dbCall("insert", userName, workDate) { timesheetEntryMapper.insert(created) }
            eventPublisher.publishEvent(TimesheetUpdatedEvent(userName, workDate))
            created
        }
    }

}
