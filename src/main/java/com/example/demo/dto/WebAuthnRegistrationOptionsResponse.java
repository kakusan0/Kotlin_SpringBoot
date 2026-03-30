package com.example.demo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class WebAuthnRegistrationOptionsResponse {
    private final long timeout = 60000;
    private final String attestation = "none";
    private final WebAuthnAuthenticatorSelection authenticatorSelection = new WebAuthnAuthenticatorSelection();
    private String challenge;
    private WebAuthnRp rp;
    private WebAuthnUser user;
    private List<WebAuthnPubKeyParam> pubKeyCredParams;
}

