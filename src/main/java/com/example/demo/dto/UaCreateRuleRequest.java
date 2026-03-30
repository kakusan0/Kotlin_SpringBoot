package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UaCreateRuleRequest {
    @NotBlank
    private String pattern;

    @NotBlank
    private String matchType = "EXACT";
}

