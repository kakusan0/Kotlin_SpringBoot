package com.example.demo.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Daily timesheet entry. While endTime is null, the user is "working".
 */
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
    private Integer version = 0;
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

    public TimesheetEntry() {
    }

    public TimesheetEntry(Long id, LocalDate workDate, String userName, LocalTime startTime, LocalTime endTime,
                          String note, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                          Integer breakMinutes, Integer durationMinutes, Integer workingMinutes,
                          Integer version, Boolean holidayWork, String workLocation,
                          String irregularWorkType, String irregularWorkDesc, String irregularWorkData,
                          String lateTime, String lateDesc, String earlyTime, String earlyDesc,
                          String freeNote, String paidLeave) {
        this.id = id;
        this.workDate = workDate;
        this.userName = userName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.breakMinutes = breakMinutes;
        this.durationMinutes = durationMinutes;
        this.workingMinutes = workingMinutes;
        if (version != null) {
            this.version = version;
        }
        if (holidayWork != null) {
            this.holidayWork = holidayWork;
        }
        this.workLocation = workLocation;
        this.irregularWorkType = irregularWorkType;
        this.irregularWorkDesc = irregularWorkDesc;
        this.irregularWorkData = irregularWorkData;
        this.lateTime = lateTime;
        this.lateDesc = lateDesc;
        this.earlyTime = earlyTime;
        this.earlyDesc = earlyDesc;
        this.freeNote = freeNote;
        this.paidLeave = paidLeave;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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

    public Integer getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(Integer breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getWorkingMinutes() {
        return workingMinutes;
    }

    public void setWorkingMinutes(Integer workingMinutes) {
        this.workingMinutes = workingMinutes;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getHolidayWork() {
        return holidayWork;
    }

    public void setHolidayWork(Boolean holidayWork) {
        this.holidayWork = holidayWork;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public void setWorkLocation(String workLocation) {
        this.workLocation = workLocation;
    }

    public String getIrregularWorkType() {
        return irregularWorkType;
    }

    public void setIrregularWorkType(String irregularWorkType) {
        this.irregularWorkType = irregularWorkType;
    }

    public String getIrregularWorkDesc() {
        return irregularWorkDesc;
    }

    public void setIrregularWorkDesc(String irregularWorkDesc) {
        this.irregularWorkDesc = irregularWorkDesc;
    }

    public String getIrregularWorkData() {
        return irregularWorkData;
    }

    public void setIrregularWorkData(String irregularWorkData) {
        this.irregularWorkData = irregularWorkData;
    }

    public String getLateTime() {
        return lateTime;
    }

    public void setLateTime(String lateTime) {
        this.lateTime = lateTime;
    }

    public String getLateDesc() {
        return lateDesc;
    }

    public void setLateDesc(String lateDesc) {
        this.lateDesc = lateDesc;
    }

    public String getEarlyTime() {
        return earlyTime;
    }

    public void setEarlyTime(String earlyTime) {
        this.earlyTime = earlyTime;
    }

    public String getEarlyDesc() {
        return earlyDesc;
    }

    public void setEarlyDesc(String earlyDesc) {
        this.earlyDesc = earlyDesc;
    }

    public String getFreeNote() {
        return freeNote;
    }

    public void setFreeNote(String freeNote) {
        this.freeNote = freeNote;
    }

    public String getPaidLeave() {
        return paidLeave;
    }

    public void setPaidLeave(String paidLeave) {
        this.paidLeave = paidLeave;
    }
}