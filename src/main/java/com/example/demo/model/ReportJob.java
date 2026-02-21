package com.example.demo.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public class ReportJob {
    private Long id;
    private String username;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String format;
    private String status = "PENDING";
    private String filePath;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public ReportJob() {
    }

    public ReportJob(Long id, String username, LocalDate fromDate, LocalDate toDate, String format,
                     String status, String filePath, String errorMessage,
                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.format = format;
        this.status = status != null ? status : this.status;
        this.filePath = filePath;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
