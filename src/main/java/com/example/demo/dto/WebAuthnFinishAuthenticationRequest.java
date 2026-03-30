package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WebAuthnFinishAuthenticationRequest {
    @NotBlank
    private String id;

    @NotBlank
    private String rawId;

    @NotBlank
    private String type;

    @Valid
    @NotNull
    private WebAuthnAuthenticationResponse response;
}

