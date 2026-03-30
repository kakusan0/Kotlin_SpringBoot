package com.example.demo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class WebAuthnAuthenticationOptionsResponse {
    private final long timeout = 60000;
    private final String userVerification = "preferred";
    private String challenge;
    private String rpId;
    private List<WebAuthnAllowCredential> allowCredentials;
}

