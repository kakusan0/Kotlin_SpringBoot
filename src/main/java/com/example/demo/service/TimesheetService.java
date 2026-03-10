package com.example.demo.service;

import com.example.demo.mapper.TimesheetEntryMapper;
import com.example.demo.model.TimesheetEntry;
import com.example.demo.model.TimesheetSaveCommand;
import com.example.demo.util.DbUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final TimesheetEntryMapper timesheetEntryMapper;
    private final ApplicationEventPublisher eventPublisher;


    private TimesheetEntry applyCalc(TimesheetEntry entry) {
        TimesheetEval eval = TimesheetEvaluator.evaluate(
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getBreakMinutes()
        );
        if (!eval.isValid()) {
            log.warn("Timesheet validation warnings for entry {}: {}",
                    entry.getId(),
                    String.join(";", eval.errors()));
        }
        TimesheetEntry updated = copyEntry(entry);
        updated.setDurationMinutes(eval.durationMinutes());
        updated.setWorkingMinutes(eval.workingMinutes());
        return updated;
    }

    @Transactional
    public TimesheetEntry clockIn(String userName, LocalTime now) {
        LocalDate today = LocalDate.now();
        TimesheetEntry existing = DbUtils.dbCall(
                "selectByUserAndDate",
                () -> timesheetEntryMapper.selectByUserAndDate(userName, today),
                userName, today
        );
        if (existing != null) {
            if (existing.getStartTime() == null) {
                TimesheetEntry updated = copyEntry(existing);
                updated.setStartTime(now);
                TimesheetEntry finalUpdated = applyCalc(updated);
                int updatedCount = DbUtils.dbCall(
                        "updateTimes",
                        () -> timesheetEntryMapper.updateTimes(finalUpdated),
                        userName, today, existing.getId()
                );
                if (updatedCount == 0) {
                    throw new TimesheetConflictException("同時更新により保存できませんでした");
                }
                return finalUpdated;
            }
            if (existing.getEndTime() == null) {
                throw new TimesheetConflictException("既に勤務中です: clock-out が必要");
            }
            return applyCalc(existing);
        }
        TimesheetEntry entry = new TimesheetEntry();
        entry.setWorkDate(today);
        entry.setUserName(userName);
        entry.setStartTime(now);
        TimesheetEntry finalEntry = applyCalc(entry);
        try {
            DbUtils.dbCall("insert", () -> timesheetEntryMapper.insert(finalEntry), userName, today);
        } catch (Exception ex) {
            TimesheetEntry nowExisting = DbUtils.dbCall(
                    "selectByUserAndDate (insert-catch)",
                    () -> timesheetEntryMapper.selectByUserAndDate(userName, today),
                    userName, today
            );
            if (nowExisting == null) {
                throw ex;
            }
            if (nowExisting.getStartTime() == null) {
                TimesheetEntry updated2 = copyEntry(nowExisting);
                updated2.setStartTime(now);
                TimesheetEntry finalUpdated2 = applyCalc(updated2);
                int updatedCount = DbUtils.dbCall(
                        "updateTimes (insert-catch)",
                        () -> timesheetEntryMapper.updateTimes(finalUpdated2),
                        userName, today, nowExisting.getId()
                );
                if (updatedCount == 0) {
                    throw new TimesheetConflictException("同時更新により保存できませんでした");
                }
                return finalUpdated2;
            }
            if (nowExisting.getEndTime() == null) {
                throw new TimesheetConflictException("既に勤務中です: clock-out が必要");
            }
            return applyCalc(nowExisting);
        }
        eventPublisher.publishEvent(new TimesheetUpdatedEvent(userName, today));
        return finalEntry;
    }

    @Transactional
    public TimesheetEntry clockOut(String userName, LocalTime now) {
        LocalDate today = LocalDate.now();
        TimesheetEntry existing = DbUtils.dbCall(
                "selectByUserAndDate",
                () -> timesheetEntryMapper.selectByUserAndDate(userName, today),
                userName, today
        );
        if (existing == null) {
            throw new TimesheetNotFoundException("本日のタイムシートがありません");
        }

        if (existing.getEndTime() != null) {
            return applyCalc(existing);
        }
        if (existing.getStartTime() == null) {
            throw new TimesheetConflictException("clock-in が未実施です");
        }

        TimesheetEntry updated = copyEntry(existing);
        updated.setEndTime(now);
        TimesheetEntry finalUpdated = applyCalc(updated);
        int updatedCount = DbUtils.dbCall(
                "updateTimes",
                () -> timesheetEntryMapper.updateTimes(finalUpdated),
                userName, today, existing.getId()
        );
        if (updatedCount == 0) {
            throw new TimesheetConflictException("同時更新により保存できませんでした");
        }
        eventPublisher.publishEvent(new TimesheetUpdatedEvent(userName, today));
        return finalUpdated;
    }

    public TimesheetEntry getToday(String userName) {
        LocalDate today = LocalDate.now();
        TimesheetEntry entry = DbUtils.dbCall(
                "selectByUserAndDate",
                () -> timesheetEntryMapper.selectByUserAndDate(userName, today),
                userName, today
        );
        return entry != null ? applyCalc(entry) : null;
    }

    public List<TimesheetEntry> list(String userName, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from は to より後ろにできません");
        }
        List<TimesheetEntry> entries = DbUtils.dbCall(
                "selectByUserAndRange",
                () -> timesheetEntryMapper.selectByUserAndRange(userName, from, to),
                userName, from, to
        );
        List<TimesheetEntry> calculatedEntries = new ArrayList<>(entries.size());
        int invalidCount = 0;
        for (TimesheetEntry e : entries) {
            TimesheetEval eval = TimesheetEvaluator.evaluate(e.getStartTime(), e.getEndTime(), e.getBreakMinutes());
            if (!eval.isValid()) {
                invalidCount++;
                log.debug("Entry {} validation issues: {}", e.getId(), String.join(";", eval.errors()));
            }
            TimesheetEntry updated = copyEntry(e);
            updated.setDurationMinutes(eval.durationMinutes());
            updated.setWorkingMinutes(eval.workingMinutes());
            calculatedEntries.add(updated);
        }
        log.info(
                "Timesheet entries user={} range={}..{} total={} invalid={}",
                userName, from, to, entries.size(), invalidCount
        );
        return calculatedEntries;
    }

    @Transactional
    public TimesheetEntry updateNote(String userName, String note) {
        LocalDate today = LocalDate.now();
        TimesheetEntry existing = DbUtils.dbCall(
                "selectByUserAndDate",
                () -> timesheetEntryMapper.selectByUserAndDate(userName, today),
                userName, today
        );
        if (existing == null) {
            throw new TimesheetNotFoundException("本日のタイムシートがありません");
        }
        DbUtils.dbCall(
                "updateNote",
                () -> timesheetEntryMapper.updateNote(existing.getId(), note),
                existing.getId(), userName, today
        );
        eventPublisher.publishEvent(new TimesheetUpdatedEvent(userName, today));
        TimesheetEntry updated = copyEntry(existing);
        updated.setNote(note);
        return applyCalc(updated);
    }

    @Transactional
    public TimesheetEntry addNoteToEntry(String userName, String note) {
        LocalDate today = LocalDate.now();
        TimesheetEntry existing = DbUtils.dbCall(
                "selectByUserAndDate",
                () -> timesheetEntryMapper.selectByUserAndDate(userName, today),
                userName, today
        );
        if (existing == null) {
            throw new TimesheetNotFoundException("No timesheet entry found for today");
        }
        DbUtils.dbCall(
                "updateNote",
                () -> timesheetEntryMapper.updateNote(existing.getId(), note),
                existing.getId(), userName, today
        );
        eventPublisher.publishEvent(new TimesheetUpdatedEvent(userName, today));
        TimesheetEntry updated = copyEntry(existing);
        updated.setNote(note);
        return applyCalc(updated);
    }

    @Transactional
    public TimesheetEntry saveOrUpdate(
            String userName,
            LocalDate workDate,
            LocalTime startTime,
            LocalTime endTime,
            Integer breakMinutes,
            boolean force,
            boolean holidayWork,
            String workLocation
    ) {
        TimesheetEntry existing = DbUtils.dbCall(
                "selectByUserAndDate",
                () -> timesheetEntryMapper.selectByUserAndDate(userName, workDate),
                userName, workDate
        );
        if (existing != null) {
            TimesheetEntry merged = copyEntry(existing);
            merged.setStartTime(startTime != null ? startTime : existing.getStartTime());
            merged.setEndTime(endTime != null ? endTime : existing.getEndTime());
            merged.setBreakMinutes(breakMinutes != null ? breakMinutes : existing.getBreakMinutes());
            merged.setHolidayWork(holidayWork);
            merged.setWorkLocation(workLocation != null ? workLocation : existing.getWorkLocation());

            TimesheetEntry recalced = applyCalc(merged);
            int updatedCount = DbUtils.dbCall(
                    "updateTimes/updateTimesForce",
                    () -> force ? timesheetEntryMapper.updateTimesForce(recalced)
                            : timesheetEntryMapper.updateTimes(recalced),
                    recalced.getId(), userName, workDate
            );
            if (updatedCount == 0) {
                throw new TimesheetConflictException("同時更新により保存できませんでした");
            }
            eventPublisher.publishEvent(new TimesheetUpdatedEvent(userName, workDate));
            return recalced;
        }

        TimesheetEntry createdBase = new TimesheetEntry();
        createdBase.setWorkDate(workDate);
        createdBase.setUserName(userName);
        createdBase.setStartTime(startTime);
        createdBase.setEndTime(endTime);
        createdBase.setBreakMinutes(breakMinutes);
        createdBase.setHolidayWork(holidayWork);
        createdBase.setWorkLocation(workLocation);
        TimesheetEntry created = applyCalc(createdBase);

        try {
            DbUtils.dbCall("insert", () -> timesheetEntryMapper.insert(created), userName, workDate);
        } catch (Exception ex) {
            TimesheetEntry nowExisting = DbUtils.dbCall(
                    "selectByUserAndDate (insert-catch)",
                    () -> timesheetEntryMapper.selectByUserAndDate(userName, workDate),
                    userName, workDate
            );
            if (nowExisting == null) {
                throw ex;
            }
            TimesheetEntry merged = copyEntry(nowExisting);
            merged.setStartTime(startTime != null ? startTime : nowExisting.getStartTime());
            merged.setEndTime(endTime != null ? endTime : nowExisting.getEndTime());
            merged.setBreakMinutes(breakMinutes != null ? breakMinutes : nowExisting.getBreakMinutes());
            merged.setHolidayWork(holidayWork);
            merged.setWorkLocation(workLocation != null ? workLocation : nowExisting.getWorkLocation());

            TimesheetEntry recalced = applyCalc(merged);
            int updatedCount = DbUtils.dbCall(
                    "updateTimes/updateTimesForce (insert-catch)",
                    () -> force ? timesheetEntryMapper.updateTimesForce(recalced)
                            : timesheetEntryMapper.updateTimes(recalced),
                    recalced.getId(), userName, workDate
            );
            if (updatedCount == 0) {
                throw new TimesheetConflictException("同時更新により保存できませんでした");
            }
            eventPublisher.publishEvent(new TimesheetUpdatedEvent(userName, workDate));
            return recalced;
        }

        eventPublisher.publishEvent(new TimesheetUpdatedEvent(userName, workDate));
        return created;
    }

    @Transactional
    public TimesheetEntry saveOrUpdateWithFlags(TimesheetSaveCommand cmd) {
        TimesheetEntry existing = DbUtils.dbCall(
                "selectByUserAndDate",
                () -> timesheetEntryMapper.selectByUserAndDate(cmd.userName(), cmd.workDate()),
                cmd.userName(), cmd.workDate()
        );
        if (existing != null) {
            TimesheetEntry merged = copyEntry(existing);
            merged.setStartTime(cmd.startProvided() ? cmd.startTime() : existing.getStartTime());
            merged.setEndTime(cmd.endProvided() ? cmd.endTime() : existing.getEndTime());
            merged.setBreakMinutes(cmd.breakProvided() ? cmd.breakMinutes() : existing.getBreakMinutes());
            merged.setHolidayWork(cmd.holidayWork());
            merged.setNote(cmd.noteProvided() ? cmd.note() : existing.getNote());
            merged.setWorkLocation(cmd.clearWorkLocation() ? null : (cmd.workLocation() != null ? cmd.workLocation() : existing.getWorkLocation()));

            merged.setIrregularWorkType(cmd.clearIrregular() ? null : (cmd.irregularWorkType() != null ? cmd.irregularWorkType() : existing.getIrregularWorkType()));
            merged.setIrregularWorkDesc(cmd.clearIrregular() ? null : (cmd.irregularWorkDesc() != null ? cmd.irregularWorkDesc() : existing.getIrregularWorkDesc()));
            merged.setIrregularWorkData(cmd.clearIrregular() ? null : (cmd.irregularWorkData() != null ? cmd.irregularWorkData() : existing.getIrregularWorkData()));
            merged.setLateTime(cmd.clearLate() ? null : (cmd.lateTime() != null ? cmd.lateTime() : existing.getLateTime()));
            merged.setLateDesc(cmd.clearLate() ? null : (cmd.lateDesc() != null ? cmd.lateDesc() : existing.getLateDesc()));
            merged.setEarlyTime(cmd.clearEarly() ? null : (cmd.earlyTime() != null ? cmd.earlyTime() : existing.getEarlyTime()));
            merged.setEarlyDesc(cmd.clearEarly() ? null : (cmd.earlyDesc() != null ? cmd.earlyDesc() : existing.getEarlyDesc()));
            merged.setFreeNote(cmd.clearFreeNote() ? null : (cmd.freeNote() != null ? cmd.freeNote() : existing.getFreeNote()));
            merged.setPaidLeave(cmd.clearPaidLeave() ? null : (cmd.paidLeave() != null ? cmd.paidLeave() : existing.getPaidLeave()));

            TimesheetEntry recalced = applyCalc(merged);
            int updatedCount = DbUtils.dbCall(
                    "updateTimes/updateTimesForce",
                    () -> cmd.force() ? timesheetEntryMapper.updateTimesForce(recalced)
                            : timesheetEntryMapper.updateTimes(recalced),
                    recalced.getId(), cmd.userName(), cmd.workDate()
            );
            if (updatedCount == 0) {
                throw new TimesheetConflictException("同時更新により保存できませんでした");
            }
            eventPublisher.publishEvent(new TimesheetUpdatedEvent(cmd.userName(), cmd.workDate()));
            return recalced;
        }

        TimesheetEntry createdBase = new TimesheetEntry();
        createdBase.setWorkDate(cmd.workDate());
        createdBase.setUserName(cmd.userName());
        createdBase.setStartTime(cmd.startTime());
        createdBase.setEndTime(cmd.endTime());
        createdBase.setBreakMinutes(cmd.breakMinutes());
        createdBase.setHolidayWork(cmd.holidayWork());
        createdBase.setNote(cmd.note());
        createdBase.setWorkLocation(cmd.workLocation());
        createdBase.setIrregularWorkType(cmd.irregularWorkType());
        createdBase.setIrregularWorkDesc(cmd.irregularWorkDesc());
        createdBase.setIrregularWorkData(cmd.irregularWorkData());
        createdBase.setLateTime(cmd.lateTime());
        createdBase.setLateDesc(cmd.lateDesc());
        createdBase.setEarlyTime(cmd.earlyTime());
        createdBase.setEarlyDesc(cmd.earlyDesc());
        createdBase.setFreeNote(cmd.freeNote());
        createdBase.setPaidLeave(cmd.paidLeave());

        TimesheetEntry created = applyCalc(createdBase);
        DbUtils.dbCall("insert", () -> timesheetEntryMapper.insert(created), cmd.userName(), cmd.workDate());
        eventPublisher.publishEvent(new TimesheetUpdatedEvent(cmd.userName(), cmd.workDate()));
        return created;
    }

    private TimesheetEntry copyEntry(TimesheetEntry source) {
        return source.toBuilder().build();
    }
}
