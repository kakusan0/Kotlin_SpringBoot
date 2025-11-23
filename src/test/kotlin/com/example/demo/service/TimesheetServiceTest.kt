package com.example.demo.service

import com.example.demo.mapper.TimesheetEntryMapper
import com.example.demo.model.TimesheetEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate
import java.time.LocalTime

class TimesheetServiceTest {
    private val mapper = Mockito.mock(TimesheetEntryMapper::class.java)
    private val service = TimesheetService(mapper)

    @Test
    fun calcNormalDay() {
        val entry = TimesheetEntry(
            workDate = LocalDate.now(),
            userName = "u",
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(18, 0),
            breakMinutes = 60
        )
        val recalc = service.saveOrUpdate("u", entry.workDate, entry.startTime, entry.endTime, entry.breakMinutes)
        assertEquals(540, recalc.durationMinutes) // 9h
        assertEquals(480, recalc.workingMinutes) // 8h
    }

    @Test
    fun calcOverMidnight() {
        val today = LocalDate.now()
        val start = LocalTime.of(22, 30)
        val end = LocalTime.of(1, 15) // 翌日
        val recalc = service.saveOrUpdate("u", today, start, end, 30)
        assertEquals(165, recalc.durationMinutes) // 2h45m = 165
        assertEquals(135, recalc.workingMinutes)
    }

    @Test
    fun calcNegativeBreakClamp() {
        val today = LocalDate.now()
        val start = LocalTime.of(10, 0)
        val end = LocalTime.of(11, 0)
        val recalc = service.saveOrUpdate("u", today, start, end, -20)
        assertEquals(60, recalc.durationMinutes)
        assertEquals(60, recalc.workingMinutes) // break 0 に矯正
    }

    @Test
    fun calcTooLongSpanIgnored() {
        val today = LocalDate.now()
        val start = LocalTime.of(8, 0)
        val end = LocalTime.of(7, 30) // 24h を超えるケース(23h30 は許容、ここは -30 wrap=1430 正常) -> 正常
        val recalc = service.saveOrUpdate("u", today, start, end, 0)
        assertEquals(1430, recalc.durationMinutes)
    }
}

