package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddHolidayRequest(
    @NotNull
    LocalDate date,

    @NotBlank
    @Size(max = 128)
    String name
) {}

