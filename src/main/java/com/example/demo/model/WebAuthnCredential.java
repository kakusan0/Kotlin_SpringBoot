package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * WebAuthn registered credential.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCredential {
    private Long id;
    private String username;
    private byte[] credentialId;
    private byte[] publicKeyCose;
    private Long signCount;
    private String transports;
    private String attestationType;
    private String aaguid;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
