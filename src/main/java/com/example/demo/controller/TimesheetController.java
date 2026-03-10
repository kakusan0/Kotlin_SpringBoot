package com.example.demo.controller;

import com.example.demo.model.TimesheetEntry;
import com.example.demo.model.TimesheetSaveCommand;
import com.example.demo.service.TimesheetService;
import com.example.demo.service.TimesheetSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
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


    private static String toString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

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
            @RequestParam String from,
            @RequestParam(required = false) String to
    ) {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to != null ? to : from);
        return timesheetService.list(auth.getName(), fromDate, toDate);
    }

    @PostMapping("/note")
    public TimesheetEntry updateNote(Authentication auth, @RequestBody Map<String, String> body) {
        String note = body.getOrDefault("note", "");
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
    public TimesheetEntry updateBreak(Authentication auth, @RequestBody Map<String, Integer> body) {
        Integer minutes = body.getOrDefault("minutes", 0);
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
    public Map<String, Object> batchSave(Authentication auth, @RequestBody Map<String, Object> body) {
        Object entriesObj = body.get("entries");
        List<?> entries = entriesObj instanceof List<?> ? (List<?>) entriesObj : List.of();
        int saved = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        for (Object entryObj : entries) {
            if (!(entryObj instanceof Map<?, ?> entry)) {
                continue;
            }
            String workDateStr = toString(entry.get("workDate"));
            if (workDateStr == null) {
                continue;
            }
            String startTimeStr = toString(entry.get("startTime"));
            String endTimeStr = toString(entry.get("endTime"));
            String breakStr = toString(entry.get("breakMinutes"));
            String holidayStr = toString(entry.get("holidayWork"));
            try {
                LocalDate workDate = LocalDate.parse(workDateStr);
                LocalTime startTime = parseLocalTime(startTimeStr);
                LocalTime endTime = parseLocalTime(endTimeStr);
                Integer breakMinutes = parseInt(breakStr);
                boolean holidayWork = holidayStr != null && holidayStr.equalsIgnoreCase("true");

                boolean startProvided = entry.containsKey("startTime");
                boolean endProvided = entry.containsKey("endTime");
                boolean breakProvided = entry.containsKey("breakMinutes");

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
                failure.put("breakMinutes", breakStr);
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
    public TimesheetSummaryService.Summary summary(Authentication auth, @RequestParam String month) {
        YearMonth ym = YearMonth.parse(month);
        return summaryService.summarize(auth.getName(), ym);
    }

    @PostMapping("/entry")
    public Map<String, Object> saveEntry(Authentication auth, @RequestBody Map<String, Object> body) {
        String workDateStr = toString(body.get("workDate"));
        if (workDateStr == null) {
            return Map.of("success", false, "message", "workDate required");
        }
        try {
            LocalDate workDate = LocalDate.parse(workDateStr);
            LocalTime startTime = parseLocalTime(toString(body.get("startTime")));
            LocalTime endTime = parseLocalTime(toString(body.get("endTime")));
            Integer breakMinutes = parseInt(toString(body.get("breakMinutes")));
            boolean force = "true".equalsIgnoreCase(toString(body.get("force")));
            boolean holidayWork = "true".equalsIgnoreCase(toString(body.get("holidayWork")));

            String note = trimToNull(toString(body.get("note")));
            boolean noteProvided = body.containsKey("note");
            String workLocation = trimToNull(toString(body.get("workLocation")));
            String irregularWorkType = trimToNull(toString(body.get("irregularWorkType")));
            String irregularWorkDesc = trimToNull(toString(body.get("irregularWorkDesc")));
            String irregularWorkData = trimToNull(toString(body.get("irregularWorkData")));
            String lateTime = trimToNull(toString(body.get("lateTime")));
            String lateDesc = trimToNull(toString(body.get("lateDesc")));
            String earlyTime = trimToNull(toString(body.get("earlyTime")));
            String earlyDesc = trimToNull(toString(body.get("earlyDesc")));
            String freeNote = trimToNull(toString(body.get("freeNote")));
            String paidLeave = trimToNull(toString(body.get("paidLeave")));

            boolean clearIrregular = body.containsKey("irregularWorkType") && isBlank(toString(body.get("irregularWorkType")));
            boolean clearLate = body.containsKey("lateTime") && isBlank(toString(body.get("lateTime")));
            boolean clearEarly = body.containsKey("earlyTime") && isBlank(toString(body.get("earlyTime")));
            boolean clearFreeNote = body.containsKey("freeNote") && isBlank(toString(body.get("freeNote")));
            boolean clearPaidLeave = body.containsKey("paidLeave") && isBlank(toString(body.get("paidLeave")));
            boolean clearWorkLocation = body.containsKey("workLocation") && isBlank(toString(body.get("workLocation")));

            boolean startProvided = body.containsKey("startTime");
            boolean endProvided = body.containsKey("endTime");
            boolean breakProvided = body.containsKey("breakMinutes");

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
        } catch (Exception ex) {
            return Map.of("success", false, "message", ex.getMessage() != null ? ex.getMessage() : "save error");
        }
    }

    @PostMapping("/add-note")
    public TimesheetEntry addNote(Authentication auth, @RequestBody Map<String, String> body) {
        String note = body.get("note");
        if (note == null) {
            throw new IllegalArgumentException("Note is required");
        }
        TimesheetEntry entry = timesheetService.addNoteToEntry(auth.getName(), note);
        broadcast("add-note", entry, auth.getName());
        return entry;
    }
}
