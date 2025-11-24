package com.example.demo.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TimesheetValidatorTest {
    @Test
    fun validNormalShift() {
        val r = TimesheetEvaluator.evaluate(LocalTime.of(9, 0), LocalTime.of(18, 0), 60)
        assertTrue(r.isValid)
        assertEquals(9 * 60, r.durationMinutes)
        assertEquals(8 * 60, r.workingMinutes)
    }

    @Test
    fun breakTooLarge() {
        val r = TimesheetEvaluator.evaluate(LocalTime.of(9, 0), LocalTime.of(18, 0), 500)
        assertFalse(r.isValid)
        assertTrue(r.errors.any { it.contains("休憩が勤務の50%") })
        // duration は計算される
        assertEquals(9 * 60, r.durationMinutes)
    }

    @Test
    fun negativeBreak() {
        val r = TimesheetEvaluator.evaluate(LocalTime.of(10, 0), LocalTime.of(11, 0), -5)
        assertFalse(r.isValid)
        assertEquals(60, r.durationMinutes)
    }

    @Test
    fun crossDayWrap() {
        val r = TimesheetEvaluator.evaluate(LocalTime.of(22, 0), LocalTime.of(1, 0), 60)
        // 22:00 -> 01:00 = 3h = 180
        assertTrue(r.isValid)
        assertEquals(180, r.durationMinutes)
        assertEquals(120, r.workingMinutes)
    }

    @Test
    fun startEndMissingWithBreak() {
        val r = TimesheetEvaluator.evaluate(null, null, 30)
        assertFalse(r.isValid)
        assertNull(r.durationMinutes)
        assertNull(r.workingMinutes)
    }

    @Test
    fun over12HoursInvalid() {
        val r = TimesheetEvaluator.evaluate(LocalTime.of(6, 0), LocalTime.of(23, 0), 60) // 17h -> invalid
        assertFalse(r.isValid)
        assertTrue(r.errors.any { it.contains("最大勤務(12h)超過") })
    }

    @Test
    fun breakExceedsDuration() {
        val r = TimesheetEvaluator.evaluate(LocalTime.of(9, 0), LocalTime.of(10, 0), 120)
        assertFalse(r.isValid)
        assertTrue(r.errors.any { it.contains("休憩が勤務時間を超過") })
    }
}
