package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TimesheetSaveEntryRequest {
    @NotBlank
    private String workDate;
    private String startTime;
    private String endTime;
    @Min(0)
    private Integer breakMinutes;
    private Boolean force;
    private Boolean holidayWork;
    private String note;
    private String workLocation;
    private String irregularWorkType;
    private String irregularWorkDesc;
    private String irregularWorkData;
    private String lateTime;
    private String lateDesc;
    private String earlyTime;
    private String earlyDesc;
    private String freeNote;
    private String paidLeave;
}

