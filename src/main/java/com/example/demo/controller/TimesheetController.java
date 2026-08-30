package com.example.demo.controller;

import com.example.demo.dto.TimesheetAddNoteRequest;
import com.example.demo.dto.TimesheetBatchEntryRequest;
import com.example.demo.dto.TimesheetBatchSaveRequest;
import com.example.demo.dto.TimesheetSaveEntryRequest;
import com.example.demo.dto.TimesheetUpdateBreakRequest;
import com.example.demo.dto.TimesheetUpdateNoteRequest;
import com.example.demo.model.TimesheetEntry;
import com.example.demo.model.TimesheetSaveCommand;
import com.example.demo.service.TimesheetService;
import com.example.demo.service.TimesheetSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/timesheet/api")
@Validated
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService timesheetService;
    private final TimesheetSummaryService summaryService;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });


    private static LocalTime parseLocalTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value).withSecond(0).withNano(0);
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @PostMapping("/clock-in")
    public TimesheetEntry clockIn(Authentication auth) {
        TimesheetEntry entry = timesheetService.clockIn(auth.getName(), LocalTime.now());
        broadcast("clock-in", entry, auth.getName());
        return entry;
    }

    @PostMapping("/clock-out")
    public TimesheetEntry clockOut(Authentication auth) {
        TimesheetEntry entry = timesheetService.clockOut(auth.getName(), LocalTime.now());
        broadcast("clock-out", entry, auth.getName());
        return entry;
    }

    @GetMapping("/today")
    public TimesheetEntry today(Authentication auth) {
        return timesheetService.getToday(auth.getName());
    }

    @GetMapping
    public List<TimesheetEntry> list(
            Authentication auth,
            @RequestParam @NotBlank String from,
            @RequestParam(required = false) String to
    ) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to != null ? to : from);
        return timesheetService.list(auth.getName(), fromDate, toDate);
    }

    @PostMapping("/note")
    public TimesheetEntry updateNote(Authentication auth, @Valid @RequestBody TimesheetUpdateNoteRequest body) {
        String note = body.note() != null ? body.note() : "";
        TimesheetEntry entry = timesheetService.updateNote(auth.getName(), note);
        broadcast("note", entry, auth.getName());
        return entry;
    }

    @PostMapping("/note/saveBeacon")
    public TimesheetEntry saveBeacon(Authentication auth, @RequestParam(required = false) String note) {
        String safe = note != null ? note : "";
        TimesheetEntry entry = timesheetService.updateNote(auth.getName(), safe);
        broadcast("note", entry, auth.getName());
        return entry;
    }

    @PostMapping("/break")
    public TimesheetEntry updateBreak(Authentication auth, @Valid @RequestBody TimesheetUpdateBreakRequest body) {
        Integer minutes = body.minutes();
        LocalDate today = LocalDate.now();
        TimesheetEntry existing = timesheetService.getToday(auth.getName());
        TimesheetEntry updated = timesheetService.saveOrUpdate(
                auth.getName(),
                today,
                existing != null ? existing.getStartTime() : null,
                existing != null ? existing.getEndTime() : null,
                minutes,
                false,
                existing != null && Boolean.TRUE.equals(existing.getHolidayWork()),
                existing != null ? existing.getWorkLocation() : null
        );
        broadcast("break", updated, auth.getName());
        return updated;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(auth.getName(), _ -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));

        ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (Exception e) {
                emitter.complete();
            }
        }, 30, 30, TimeUnit.SECONDS);

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        return emitter;
    }

    private void broadcast(String event, Object data, String userName) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userName);
        if (list == null) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name(event).data(data));
            } catch (Exception ignored) {
                dead.add(em);
            }
        }
        list.removeAll(dead);
    }

    @PostMapping("/batch")
    public Map<String, Object> batchSave(Authentication auth, @Valid @RequestBody TimesheetBatchSaveRequest body) {
        List<TimesheetBatchEntryRequest> entries = body.entries() != null ? body.entries() : List.of();
        int saved = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        for (TimesheetBatchEntryRequest entry : entries) {
            String workDateStr = entry.workDate();
            String startTimeStr = entry.startTime();
            String endTimeStr = entry.endTime();
            try {
                LocalDate workDate = LocalDate.parse(workDateStr);
                LocalTime startTime = parseLocalTime(startTimeStr);
                LocalTime endTime = parseLocalTime(endTimeStr);
                Integer breakMinutes = entry.breakMinutes();
                boolean holidayWork = Boolean.TRUE.equals(entry.holidayWork());

                boolean startProvided = entry.startTime() != null;
                boolean endProvided = entry.endTime() != null;
                boolean breakProvided = entry.breakMinutes() != null;

                TimesheetSaveCommand cmd = TimesheetSaveCommand.builder()
                        .userName(auth.getName())
                        .workDate(workDate)
                        .startProvided(startProvided)
                        .startTime(startTime)
                        .endProvided(endProvided)
                        .endTime(endTime)
                        .breakProvided(breakProvided)
                        .breakMinutes(breakMinutes)
                        .force(false)
                        .holidayWork(holidayWork)
                        .build();

                TimesheetEntry savedEntry = timesheetService.saveOrUpdateWithFlags(cmd);
                broadcast("timesheet-updated", savedEntry, auth.getName());
                saved++;
            } catch (Exception ex) {
                Map<String, Object> failure = new HashMap<>();
                failure.put("workDate", workDateStr);
                failure.put("error", ex.getMessage() != null ? ex.getMessage() : "parse/save error");
                failure.put("startTime", startTimeStr);
                failure.put("endTime", endTimeStr);
                failure.put("breakMinutes", entry.breakMinutes());
                failures.add(failure);
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("saved", saved);
        resp.put("total", entries.size());
        resp.put("failed", failures.size());
        resp.put("failures", failures);
        return resp;
    }

    @GetMapping("/summary")
    public TimesheetSummaryService.Summary summary(Authentication auth, @RequestParam @NotBlank String month) {
        YearMonth ym = YearMonth.parse(month);
        return summaryService.summarize(auth.getName(), ym);
    }

    @PostMapping("/entry")
    public Map<String, Object> saveEntry(Authentication auth, @Valid @RequestBody TimesheetSaveEntryRequest body) {
        String workDateStr = body.workDate();
        LocalDate workDate = LocalDate.parse(workDateStr);
        LocalTime startTime = parseLocalTime(body.startTime());
        LocalTime endTime = parseLocalTime(body.endTime());
        Integer breakMinutes = body.breakMinutes();
        boolean force = Boolean.TRUE.equals(body.force());
        boolean holidayWork = Boolean.TRUE.equals(body.holidayWork());

        String note = trimToNull(body.note());
        boolean noteProvided = body.note() != null;
        String workLocation = trimToNull(body.workLocation());
        String irregularWorkType = trimToNull(body.irregularWorkType());
        String irregularWorkDesc = trimToNull(body.irregularWorkDesc());
        String irregularWorkData = trimToNull(body.irregularWorkData());
        String lateTime = trimToNull(body.lateTime());
        String lateDesc = trimToNull(body.lateDesc());
        String earlyTime = trimToNull(body.earlyTime());
        String earlyDesc = trimToNull(body.earlyDesc());
        String freeNote = trimToNull(body.freeNote());
        String paidLeave = trimToNull(body.paidLeave());

        boolean clearIrregular = body.irregularWorkType() != null && isBlank(body.irregularWorkType());
        boolean clearLate = body.lateTime() != null && isBlank(body.lateTime());
        boolean clearEarly = body.earlyTime() != null && isBlank(body.earlyTime());
        boolean clearFreeNote = body.freeNote() != null && isBlank(body.freeNote());
        boolean clearPaidLeave = body.paidLeave() != null && isBlank(body.paidLeave());
        boolean clearWorkLocation = body.workLocation() != null && isBlank(body.workLocation());

        boolean startProvided = body.startTime() != null;
        boolean endProvided = body.endTime() != null;
        boolean breakProvided = body.breakMinutes() != null;

        TimesheetSaveCommand cmd = TimesheetSaveCommand.builder()
                .userName(auth.getName())
                .workDate(workDate)
                .startProvided(startProvided)
                .startTime(startTime)
                .endProvided(endProvided)
                .endTime(endTime)
                .breakProvided(breakProvided)
                .breakMinutes(breakMinutes)
                .force(force)
                .holidayWork(holidayWork)
                .noteProvided(noteProvided)
                .note(note)
                .workLocation(workLocation)
                .irregularWorkType(irregularWorkType)
                .irregularWorkDesc(irregularWorkDesc)
                .irregularWorkData(irregularWorkData)
                .lateTime(lateTime)
                .lateDesc(lateDesc)
                .earlyTime(earlyTime)
                .earlyDesc(earlyDesc)
                .freeNote(freeNote)
                .paidLeave(paidLeave)
                .clearIrregular(clearIrregular)
                .clearLate(clearLate)
                .clearEarly(clearEarly)
                .clearFreeNote(clearFreeNote)
                .clearPaidLeave(clearPaidLeave)
                .clearWorkLocation(clearWorkLocation)
                .build();

        TimesheetEntry saved = timesheetService.saveOrUpdateWithFlags(cmd);
        broadcast("timesheet-updated", saved, auth.getName());
        return Map.of("success", true, "entry", saved);
    }

    @PostMapping("/add-note")
    public TimesheetEntry addNote(Authentication auth, @Valid @RequestBody TimesheetAddNoteRequest body) {
        TimesheetEntry entry = timesheetService.addNoteToEntry(auth.getName(), body.note());
        broadcast("add-note", entry, auth.getName());
        return entry;
    }
}
