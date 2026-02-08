package com.example.demo.service;

import java.util.List;

public record TimesheetEval(Integer durationMinutes, Integer workingMinutes, List<String> errors) {

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }
}
