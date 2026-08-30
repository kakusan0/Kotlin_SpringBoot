package com.example.demo.dto;

import jakarta.validation.constraints.Min;
public record TimesheetUpdateBreakRequest(
    @Min(0)
    Integer minutes
) {
    public TimesheetUpdateBreakRequest() {
        this(0);
    }
}
