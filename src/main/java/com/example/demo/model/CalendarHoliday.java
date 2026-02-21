package com.example.demo.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Calendar holiday entry.
 */
public class CalendarHoliday {
    private Long id;
    private LocalDate holidayDate;
    private String name;
    private Integer year;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public CalendarHoliday() {
    }

    public CalendarHoliday(Long id, LocalDate holidayDate, String name, Integer year,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.holidayDate = holidayDate;
        this.name = name;
        this.year = year;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
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
