package com.example.demo.service;

import com.example.demo.mapper.TimesheetEntryMapper;
import com.example.demo.model.TimesheetEntry;
import com.example.demo.model.TimesheetSaveCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {

    @Mock
    private TimesheetEntryMapper timesheetEntryMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TimesheetService service;

    @Test
    void saveOrUpdateWithFlagsClearsIrregularFieldsAndPersistsEmptyNote() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        TimesheetEntry existing = TimesheetEntry.builder()
                .id(10L)
                .userName("alice")
                .workDate(date)
                .note("午前休")
                .irregularWorkType("在宅")
                .irregularWorkDesc("旧値")
                .irregularWorkData("旧データ")
                .build();
        when(timesheetEntryMapper.selectByUserAndDate("alice", date)).thenReturn(existing);
        when(timesheetEntryMapper.updateTimes(any())).thenReturn(1);

        TimesheetSaveCommand command = TimesheetSaveCommand.builder()
                .userName("alice")
                .workDate(date)
                .noteProvided(true)
                .note("")
                .clearIrregular(true)
                .build();

        TimesheetEntry result = service.saveOrUpdateWithFlags(command);

        assertEquals("", result.getNote());
        assertEquals(null, result.getIrregularWorkType());
        assertEquals(null, result.getIrregularWorkDesc());
        assertEquals(null, result.getIrregularWorkData());
        verify(timesheetEntryMapper).updateTimes(any(TimesheetEntry.class));
        verify(eventPublisher).publishEvent(any(TimesheetUpdatedEvent.class));
    }
}
