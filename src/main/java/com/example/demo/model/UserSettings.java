package com.example.demo.model;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * User settings for timesheet.
 */
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

    public UserSettings() {
    }

    public UserSettings(
            Long id,
            String userName,
            String companyAffiliation,
            Integer section,
            String branchOffice,
            Integer workGroup,
            String employeeNumber,
            LocalTime siteRegularHours,
            String displayName,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.userName = userName;
        this.companyAffiliation = companyAffiliation;
        this.section = section;
        this.branchOffice = branchOffice;
        this.workGroup = workGroup;
        this.employeeNumber = employeeNumber;
        this.siteRegularHours = siteRegularHours;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getCompanyAffiliation() {
        return companyAffiliation;
    }

    public void setCompanyAffiliation(String companyAffiliation) {
        this.companyAffiliation = companyAffiliation;
    }

    public Integer getSection() {
        return section;
    }

    public void setSection(Integer section) {
        this.section = section;
    }

    public String getBranchOffice() {
        return branchOffice;
    }

    public void setBranchOffice(String branchOffice) {
        this.branchOffice = branchOffice;
    }

    public Integer getWorkGroup() {
        return workGroup;
    }

    public void setWorkGroup(Integer workGroup) {
        this.workGroup = workGroup;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public LocalTime getSiteRegularHours() {
        return siteRegularHours;
    }

    public void setSiteRegularHours(LocalTime siteRegularHours) {
        this.siteRegularHours = siteRegularHours;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
