package com.example.demo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WebAuthnDiscoverableAuthenticationOptionsResponse {
    private final long timeout = 60000;
    private final String userVerification = "preferred";
    private String challenge;
    private String rpId;
}

