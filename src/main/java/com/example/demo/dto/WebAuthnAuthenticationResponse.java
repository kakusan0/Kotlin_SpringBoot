package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WebAuthnAuthenticationResponse {
    @NotBlank
    private String authenticatorData;

    @NotBlank
    private String clientDataJSON;

    @NotBlank
    private String signature;

    private String userHandle;
}

