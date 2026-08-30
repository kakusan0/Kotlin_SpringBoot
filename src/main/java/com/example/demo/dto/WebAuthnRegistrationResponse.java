package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
public record WebAuthnRegistrationResponse(
    @NotBlank String attestationObject,
    @NotBlank String clientDataJSON
) {
}
