package com.example.demo.service;

import com.example.demo.mapper.TimesheetEntryMapper;
import com.example.demo.model.TimesheetEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimesheetSummaryServiceTest {

    @Mock
    private TimesheetEntryMapper timesheetEntryMapper;

    @InjectMocks
    private TimesheetSummaryService service;

    @Test
    void summarizeAggregatesWorkingAndBreakMinutes() {
        when(timesheetEntryMapper.selectByUserAndRange("alice",
                YearMonth.of(2026, 8).atDay(1), YearMonth.of(2026, 8).atEndOfMonth()))
                .thenReturn(List.of(
                        TimesheetEntry.builder().workingMinutes(480).breakMinutes(60)
                                .startTime(java.time.LocalTime.of(9, 0)).build(),
                        TimesheetEntry.builder().workingMinutes(420).breakMinutes(45)
                                .startTime(java.time.LocalTime.of(9, 0)).build()));

        TimesheetSummaryService.Summary summary = service.summarize("alice", YearMonth.of(2026, 8));

        assertEquals(900, summary.totalWorkingMinutes());
        assertEquals(105, summary.totalBreakMinutes());
        assertEquals(2, summary.daysCount());
        assertEquals(450.0, summary.averageWorkingMinutes());
    }
}
