package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record TimesheetAddNoteRequest(
    @NotBlank
    String note
) {}

