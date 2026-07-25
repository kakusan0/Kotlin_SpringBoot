package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record IpBlacklistRequest(
    @NotBlank
    String ipAddress
) {}

