package com.example.demo.service;

import com.example.demo.mapper.TimesheetEntryMapper;
import com.example.demo.model.TimesheetEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimesheetSummaryServiceTest {

    @Mock
    private TimesheetEntryMapper timesheetEntryMapper;

    @InjectMocks
    private TimesheetSummaryService service;

    @Test
    void summarizeAggregatesTotalsAndAverage() {
        TimesheetEntry entry1 = TimesheetEntry.builder()
                .workDate(LocalDate.of(2026, 2, 1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .workingMinutes(480)
                .breakMinutes(60)
                .build();

        TimesheetEntry entry2 = TimesheetEntry.builder()
                .workDate(LocalDate.of(2026, 2, 2))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .workingMinutes(120)
                .breakMinutes(15)
                .build();

        when(timesheetEntryMapper.selectByUserAndRange(eq("user"), any(), any()))
                .thenReturn(List.of(entry1, entry2));

        TimesheetSummaryService.Summary summary = service.summarize("user", YearMonth.of(2026, 2));

        assertEquals(600, summary.totalWorkingMinutes());
        assertEquals(75, summary.totalBreakMinutes());
        assertEquals(2, summary.daysCount());
        assertEquals(300.0, summary.averageWorkingMinutes());
    }
}

