package com.example.demo.model;

import java.time.OffsetDateTime;

/**
 * WebAuthn registered credential.
 */
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

    public WebAuthnCredential() {
    }

    public WebAuthnCredential(Long id, String username, byte[] credentialId, byte[] publicKeyCose, Long signCount,
                              String transports, String attestationType, String aaguid,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.credentialId = credentialId;
        this.publicKeyCose = publicKeyCose;
        this.signCount = signCount;
        this.transports = transports;
        this.attestationType = attestationType;
        this.aaguid = aaguid;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public byte[] getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(byte[] credentialId) {
        this.credentialId = credentialId;
    }

    public byte[] getPublicKeyCose() {
        return publicKeyCose;
    }

    public void setPublicKeyCose(byte[] publicKeyCose) {
        this.publicKeyCose = publicKeyCose;
    }

    public Long getSignCount() {
        return signCount;
    }

    public void setSignCount(Long signCount) {
        this.signCount = signCount;
    }

    public String getTransports() {
        return transports;
    }

    public void setTransports(String transports) {
        this.transports = transports;
    }

    public String getAttestationType() {
        return attestationType;
    }

    public void setAttestationType(String attestationType) {
        this.attestationType = attestationType;
    }

    public String getAaguid() {
        return aaguid;
    }

    public void setAaguid(String aaguid) {
        this.aaguid = aaguid;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
