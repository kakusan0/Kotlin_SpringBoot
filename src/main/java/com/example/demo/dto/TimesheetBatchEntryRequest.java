package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TimesheetBatchEntryRequest {
    @NotBlank
    private String workDate;
    private String startTime;
    private String endTime;
    @Min(0)
    private Integer breakMinutes;
    private Boolean holidayWork;
}

