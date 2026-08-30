package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record WebAuthnFinishAuthenticationRequest(
    @NotBlank String id,
    @NotBlank String rawId,
    @NotBlank String type,
    @Valid @NotNull WebAuthnAuthenticationResponse response
) {
}
