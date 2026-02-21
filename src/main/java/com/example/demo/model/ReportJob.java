package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportJob {
    private Long id;
    private String username;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String format;
    @Builder.Default
    private String status = "PENDING";
    private String filePath;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
