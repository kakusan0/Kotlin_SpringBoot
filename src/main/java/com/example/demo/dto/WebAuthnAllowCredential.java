package com.example.demo.dto;

import java.util.List;

public record WebAuthnAllowCredential(String type, String id, List<String> transports) {
    public WebAuthnAllowCredential {
        transports = transports == null ? List.of() : List.copyOf(transports);
    }
}
