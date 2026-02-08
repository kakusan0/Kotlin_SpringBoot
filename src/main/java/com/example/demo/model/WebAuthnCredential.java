package com.example.demo.model;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * WebAuthn registered credential.
 */
@Getter
@Setter
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
