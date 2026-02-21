package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * User settings for timesheet.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {
    private Long id;
    private String userName;
    private String companyAffiliation;
    private Integer section;
    private String branchOffice;
    private Integer workGroup;
    private String employeeNumber;
    private LocalTime siteRegularHours;
    private String displayName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
