package com.example.demo.model;

import java.time.OffsetDateTime;

public class WhitelistIp {
    private Long id;
    private String ipAddress;
    private OffsetDateTime createdAt;
    private Boolean blacklisted;
    private Integer blacklistedCount;

    public WhitelistIp() {
    }

    public WhitelistIp(Long id, String ipAddress, OffsetDateTime createdAt, Boolean blacklisted, Integer blacklistedCount) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
        this.blacklisted = blacklisted;
        this.blacklistedCount = blacklistedCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(Boolean blacklisted) {
        this.blacklisted = blacklisted;
    }

    public Integer getBlacklistedCount() {
        return blacklistedCount;
    }

    public void setBlacklistedCount(Integer blacklistedCount) {
        this.blacklistedCount = blacklistedCount;
    }
}
