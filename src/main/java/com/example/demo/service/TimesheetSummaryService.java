package com.example.demo.service;

import com.example.demo.mapper.TimesheetEntryMapper;
import com.example.demo.model.TimesheetEntry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TimesheetSummaryService {

    private final TimesheetEntryMapper timesheetEntryMapper;
    private final long ttlMillis = 60_000L;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public TimesheetSummaryService(TimesheetEntryMapper timesheetEntryMapper) {
        this.timesheetEntryMapper = timesheetEntryMapper;
    }

    private String key(String user, YearMonth ym) {
        return user + ":" + ym;
    }

    public Summary summarize(String userName, YearMonth ym) {
        String k = key(userName, ym);
        long now = System.currentTimeMillis();
        Cached cached = cache.get(k);
        if (cached != null && now - cached.cachedAtMillis() < ttlMillis) {
            return cached.summary();
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<TimesheetEntry> list = timesheetEntryMapper.selectByUserAndRange(userName, from, to);
        int totalWorking = 0;
        int totalBreak = 0;
        int countedDays = 0;
        for (TimesheetEntry e : list) {
            Integer w = e.getWorkingMinutes();
            Integer b = e.getBreakMinutes();
            if (w != null) {
                totalWorking += w;
            }
            if (b != null) {
                totalBreak += b;
            }
            if (e.getStartTime() != null || e.getEndTime() != null) {
                countedDays++;
            }
        }
        double avg = countedDays > 0 ? (double) totalWorking / countedDays : 0.0;
        Summary summary = new Summary(userName, ym.toString(), totalWorking, totalBreak, avg, countedDays);
        cache.put(k, new Cached(summary, now));
        return summary;
    }

    public void invalidate(String userName, LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        cache.remove(key(userName, ym));
    }

    @EventListener
    public void onTimesheetUpdated(TimesheetUpdatedEvent ev) {
        invalidate(ev.userName(), ev.date());
    }

    public record Summary(
            String userName,
            String yearMonth,
            int totalWorkingMinutes,
            int totalBreakMinutes,
            double averageWorkingMinutes,
            int daysCount
    ) {
    }

    private record Cached(Summary summary, long cachedAtMillis) {
    }
}
