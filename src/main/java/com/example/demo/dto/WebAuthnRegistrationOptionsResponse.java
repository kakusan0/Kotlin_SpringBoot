package com.example.demo.dto;

import java.util.List;

public record WebAuthnRegistrationOptionsResponse(
    long timeout,
    String attestation,
    WebAuthnAuthenticatorSelection authenticatorSelection,
    String challenge,
    WebAuthnRp rp,
    WebAuthnUser user,
    List<WebAuthnPubKeyParam> pubKeyCredParams
) {
    public WebAuthnRegistrationOptionsResponse(String challenge, WebAuthnRp rp,
                                               WebAuthnUser user,
                                               List<WebAuthnPubKeyParam> pubKeyCredParams) {
        this(60000, "none", new WebAuthnAuthenticatorSelection(), challenge, rp, user, pubKeyCredParams);
    }
}
