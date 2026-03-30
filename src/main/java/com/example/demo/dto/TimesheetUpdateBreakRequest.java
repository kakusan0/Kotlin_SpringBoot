package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TimesheetUpdateBreakRequest {
    @Min(0)
    private Integer minutes = 0;
}

