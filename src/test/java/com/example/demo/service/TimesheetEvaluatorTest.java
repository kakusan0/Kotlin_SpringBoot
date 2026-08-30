package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TimesheetEvaluatorTest {

    @Test
    void evaluatesCrossMidnightWork() {
        TimesheetEval result = TimesheetEvaluator.evaluate(
                LocalTime.of(22, 0), LocalTime.of(6, 0), 60);

        assertEquals(480, result.durationMinutes());
        assertEquals(420, result.workingMinutes());
        assertEquals(0, result.errors().size());
    }

    @Test
    void rejectsBreakWithoutWorkTimes() {
        TimesheetEval result = TimesheetEvaluator.evaluate(null, null, 30);

        assertFalse(result.isValid());
        assertEquals("開始/終了未確定で休憩設定不可", result.errors().getFirst());
    }
}
