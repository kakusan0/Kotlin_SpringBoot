package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class TimesheetEvaluatorTest {

    @Test
    void evaluateNormalDayCalculatesDurationAndWorking() {
        TimesheetEval result = TimesheetEvaluator.evaluate(
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                60
        );

        assertTrue(result.isValid());
        assertEquals(540, result.durationMinutes());
        assertEquals(480, result.workingMinutes());
    }

    @Test
    void evaluateCrossMidnightCalculatesCorrectDuration() {
        TimesheetEval result = TimesheetEvaluator.evaluate(
                LocalTime.of(22, 0),
                LocalTime.of(2, 0),
                30
        );

        assertTrue(result.isValid());
        assertEquals(240, result.durationMinutes());
        assertEquals(210, result.workingMinutes());
    }

    @Test
    void evaluateBreakTooLargeAddsErrors() {
        TimesheetEval result = TimesheetEvaluator.evaluate(
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                61
        );

        assertFalse(result.isValid());
        assertNotNull(result.errors());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("休憩")));
    }
}

