package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
public class TimesheetBatchSaveRequest {
    @NotNull
    @NotEmpty
    private List<@Valid TimesheetBatchEntryRequest> entries = Collections.emptyList();
}

