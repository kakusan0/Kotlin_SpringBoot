package com.example.demo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WebAuthnAuthenticatorSelection {
    private final String residentKey = "required";
    private final boolean requireResidentKey = true;
    private final String userVerification = "preferred";
    private final String authenticatorAttachment = null;
}

