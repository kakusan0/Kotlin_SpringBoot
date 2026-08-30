package com.example.demo.dto;

public record WebAuthnDiscoverableAuthenticationOptionsResponse(
    long timeout,
    String userVerification,
    String challenge,
    String rpId
) {
    public WebAuthnDiscoverableAuthenticationOptionsResponse(String challenge, String rpId) {
        this(60000, "preferred", challenge, rpId);
    }
}
