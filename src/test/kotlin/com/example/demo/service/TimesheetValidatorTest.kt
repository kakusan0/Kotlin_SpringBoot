package com.example.demo.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TimesheetValidatorTest {
    @Test
    fun validNormalShift() {
        val r = TimesheetValidator.validate(LocalTime.of(9, 0), LocalTime.of(18, 0), 60)
        assertTrue(r.isValid)
    }

    @Test
    fun breakTooLarge() {
        val r = TimesheetValidator.validate(LocalTime.of(9, 0), LocalTime.of(18, 0), 500)
        assertFalse(r.isValid)
        assertTrue(r.errors.any { it.contains("休憩が勤務の50%") })
    }

    @Test
    fun negativeBreak() {
        val r = TimesheetValidator.validate(LocalTime.of(10, 0), LocalTime.of(11, 0), -5)
        assertFalse(r.isValid)
    }

    @Test
    fun crossDayTooLong() {
        // 23:00 -> 23:30 (wrap 30分) 正常, 23:00 -> 22:59 (wrap 1439分) 正常, 23:00 -> 22:58 (wrap 1438) 正常
        // 23:00 -> 22:00 (wrap 1380) 正常, 過大例: 08:00 -> 07:59 wrap 1439 OK. 制限超過ケース作るには意図的に24h超の想定を作れないため省略。
        val r = TimesheetValidator.validate(LocalTime.of(22, 0), LocalTime.of(1, 0), 60)
        assertTrue(r.isValid)
    }

    @Test
    fun startEndMissingWithBreak() {
        val r = TimesheetValidator.validate(null, null, 30)
        assertFalse(r.isValid)
    }
}

