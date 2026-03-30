package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WebAuthnRegistrationResponse {
    @NotBlank
    private String attestationObject;

    @NotBlank
    private String clientDataJSON;
}

