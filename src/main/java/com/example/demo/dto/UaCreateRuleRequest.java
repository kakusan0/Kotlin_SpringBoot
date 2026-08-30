package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
public record UaCreateRuleRequest(
    @NotBlank
    String pattern,

    @NotBlank
    String matchType
) {
    public UaCreateRuleRequest {
        if (matchType == null || matchType.isBlank()) {
            matchType = "EXACT";
        }
    }

    public UaCreateRuleRequest(String pattern) {
        this(pattern, "EXACT");
    }
}
