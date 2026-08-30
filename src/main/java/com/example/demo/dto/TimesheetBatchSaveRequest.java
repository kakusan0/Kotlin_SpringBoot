package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TimesheetBatchSaveRequest(
    @NotNull
    @NotEmpty
    List<@Valid TimesheetBatchEntryRequest> entries
) {
    public TimesheetBatchSaveRequest() {
        this(List.of());
    }
}
