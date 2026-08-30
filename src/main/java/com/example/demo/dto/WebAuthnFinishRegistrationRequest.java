package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record WebAuthnFinishRegistrationRequest(
    @NotBlank String id,
    @NotBlank String rawId,
    @NotBlank String type,
    @Valid @NotNull WebAuthnRegistrationResponse response
) {
}
