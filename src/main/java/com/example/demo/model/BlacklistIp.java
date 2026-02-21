package com.example.demo.model;

import java.time.OffsetDateTime;

public class BlacklistIp {
    private Long id;
    private String ipAddress;
    private OffsetDateTime createdAt;
    private Boolean deleted;
    private Integer times;

    public BlacklistIp() {
    }

    public BlacklistIp(Long id, String ipAddress, OffsetDateTime createdAt, Boolean deleted, Integer times) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
        this.deleted = deleted;
        this.times = times;
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

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public Integer getTimes() {
        return times;
    }

    public void setTimes(Integer times) {
        this.times = times;
    }
}
