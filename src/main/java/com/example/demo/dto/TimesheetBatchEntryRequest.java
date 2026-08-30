package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record TimesheetBatchEntryRequest(
    @NotBlank
    String workDate,
    String startTime,
    String endTime,
    @Min(0)
    Integer breakMinutes,
    Boolean holidayWork
) {
}
