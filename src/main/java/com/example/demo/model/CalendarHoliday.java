package com.example.demo.model;

import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Calendar holiday entry.
 */
@Getter
@Setter
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
