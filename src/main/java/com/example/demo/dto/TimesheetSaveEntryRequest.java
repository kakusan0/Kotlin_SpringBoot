package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record TimesheetSaveEntryRequest(
    @NotBlank
    String workDate,
    String startTime,
    String endTime,
    @Min(0)
    Integer breakMinutes,
    Boolean force,
    Boolean holidayWork,
    String note,
    String workLocation,
    String irregularWorkType,
    String irregularWorkDesc,
    String irregularWorkData,
    String lateTime,
    String lateDesc,
    String earlyTime,
    String earlyDesc,
    String freeNote,
    String paidLeave
) {
}
