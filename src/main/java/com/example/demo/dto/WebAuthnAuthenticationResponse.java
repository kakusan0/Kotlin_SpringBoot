package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
public record WebAuthnAuthenticationResponse(
    @NotBlank String authenticatorData,
    @NotBlank String clientDataJSON,
    @NotBlank String signature,
    String userHandle
) {
}
