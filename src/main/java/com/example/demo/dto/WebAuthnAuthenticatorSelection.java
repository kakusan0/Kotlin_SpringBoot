package com.example.demo.dto;

public record WebAuthnAuthenticatorSelection(
    String residentKey,
    boolean requireResidentKey,
    String userVerification,
    String authenticatorAttachment
) {
    public WebAuthnAuthenticatorSelection() {
        this("required", true, "preferred", null);
    }
}
