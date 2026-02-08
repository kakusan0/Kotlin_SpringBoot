package com.example.demo.service;

import java.time.LocalTime;

public final class TimesheetValidator {
    private TimesheetValidator() {
    }

    public static TimesheetValidationResult validate(LocalTime start, LocalTime end, Integer breakMinutes) {
        TimesheetEval r = TimesheetEvaluator.evaluate(start, end, breakMinutes);
        return new TimesheetValidationResult(r.errors());
    }
}
