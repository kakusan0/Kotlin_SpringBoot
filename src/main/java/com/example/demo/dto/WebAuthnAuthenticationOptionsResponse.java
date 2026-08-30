package com.example.demo.dto;

import java.util.List;

public record WebAuthnAuthenticationOptionsResponse(
    long timeout,
    String userVerification,
    String challenge,
    String rpId,
    List<WebAuthnAllowCredential> allowCredentials
) {
    public WebAuthnAuthenticationOptionsResponse(String challenge, String rpId,
                                                 List<WebAuthnAllowCredential> allowCredentials) {
        this(60000, "preferred", challenge, rpId, allowCredentials);
    }
}
