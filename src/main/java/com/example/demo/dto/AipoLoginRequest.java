package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record AipoLoginRequest(
    @NotBlank
    String username,

    @NotBlank
    String password,

    @NotBlank
    String yearMonth,

    boolean autoSubmit
) {}

