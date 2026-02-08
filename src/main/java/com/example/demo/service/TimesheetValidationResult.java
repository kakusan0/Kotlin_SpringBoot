package com.example.demo.service;

import java.util.List;

public record TimesheetValidationResult(List<String> errors) {

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }
}
