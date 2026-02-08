package com.example.demo.model;

import lombok.*;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * User settings for timesheet.
 */
@Getter
@Setter
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
