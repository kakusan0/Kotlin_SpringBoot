package com.example.demo.service;

import com.example.demo.mapper.TimesheetEntryMapper;
import com.example.demo.model.TimesheetEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimesheetSummaryService {

    private final TimesheetEntryMapper timesheetEntryMapper;
    public Summary summarize(String userName, YearMonth ym) {
        var from = ym.atDay(1);
        var to = ym.atEndOfMonth();
        List<TimesheetEntry> list = timesheetEntryMapper.selectByUserAndRange(userName, from, to);
        int totalWorking = 0;
        int totalBreak = 0;
        int countedDays = 0;
        for (TimesheetEntry e : list) {
            Integer w = e.getWorkingMinutes();
            Integer b = e.getBreakMinutes();
            if (w != null) totalWorking += w;
            if (b != null) totalBreak += b;
            if (e.getStartTime() != null || e.getEndTime() != null) countedDays++;
        }
        double avg = countedDays > 0 ? (double) totalWorking / countedDays : 0.0;
        return new Summary(userName, ym.toString(), totalWorking, totalBreak, avg, countedDays);
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
