package com.example.demo.service;

import com.example.demo.mapper.TimesheetEntryMapper;
import com.example.demo.model.TimesheetEntry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TimesheetSummaryService {

    private final TimesheetEntryMapper timesheetEntryMapper;
    private final Cache<String, Summary> cache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(1_000)
            .build();

    private String key(String user, YearMonth ym) {
        return user + ":" + ym;
    }

    public Summary summarize(String userName, YearMonth ym) {
        return cache.get(key(userName, ym), k -> {
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
            return new Summary(userName, ym.toString(), totalWorking, totalBreak, avg, countedDays);
        });
    }

    public void invalidate(String userName, LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        cache.invalidate(key(userName, ym));
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
            int daysCount) {
    }

}
