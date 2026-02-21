package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Calendar holiday entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarHoliday {
    private Long id;
    private LocalDate holidayDate;
    private String name;
    private Integer year;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
