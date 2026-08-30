package com.example.demo.service;

import com.example.demo.mapper.CalendarHolidayMapper;
import com.example.demo.model.CalendarHoliday;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarHolidayServiceTest {

    @Mock
    private CalendarHolidayMapper calendarHolidayMapper;

    @InjectMocks
    private CalendarHolidayService service;

    @Test
    void getHolidaysByRangeRejectsReversedRange() {
        assertThrows(IllegalArgumentException.class, () -> service.getHolidaysByRange(
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)));
    }

    @Test
    void addHolidayBuildsAndInsertsEntity() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        CalendarHoliday result = service.addHoliday(date, "臨時休業");

        assertEquals(date, result.getHolidayDate());
        assertEquals(2026, result.getYear());
        assertEquals("臨時休業", result.getName());
        verify(calendarHolidayMapper).insert(result);
    }

    @Test
    void getHolidaysMapByYearMapsDateToName() {
        CalendarHoliday holiday = new CalendarHoliday();
        holiday.setHolidayDate(LocalDate.of(2026, 1, 1));
        holiday.setName("元日");
        when(calendarHolidayMapper.selectByYear(2026)).thenReturn(List.of(holiday));

        assertEquals("元日", service.getHolidaysMapByYear(2026).get("2026-01-01"));
    }
}
