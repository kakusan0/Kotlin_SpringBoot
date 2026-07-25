package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record AipoSubmitRequest(
    @NotBlank
    String submitButtonId
) {}

