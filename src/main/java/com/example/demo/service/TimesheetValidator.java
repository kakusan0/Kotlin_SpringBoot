package com.example.demo.service;

import lombok.experimental.UtilityClass;

import java.time.LocalTime;

@UtilityClass
public class TimesheetValidator {

    public TimesheetValidationResult validate(LocalTime start, LocalTime end, Integer breakMinutes) {
        TimesheetEval r = TimesheetEvaluator.evaluate(start, end, breakMinutes);
        return new TimesheetValidationResult(r.errors());
    }
}
