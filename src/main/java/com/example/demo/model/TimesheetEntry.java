package com.example.demo.model;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Daily timesheet entry. While endTime is null, the user is "working".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetEntry {
    private Long id;
    private LocalDate workDate;
    private String userName;
    private LocalTime startTime;
    private LocalTime endTime;
    private String note;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer breakMinutes;
    private Integer durationMinutes;
    private Integer workingMinutes;
    @Builder.Default
    private Integer version = 0;
    @Builder.Default
    private Boolean holidayWork = false;
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