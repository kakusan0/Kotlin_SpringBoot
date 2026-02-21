package com.example.demo.model;

import java.time.OffsetDateTime;

public class BlacklistEvent {
    private Long id;
    private OffsetDateTime createdAt;
    private String requestId;
    private String ipAddress;
    private String method;
    private String path;
    private Integer status;
    private String userAgent;
    private String referer;
    private String reason;
    private String source;

    public BlacklistEvent() {
    }

    public BlacklistEvent(Long id, OffsetDateTime createdAt, String requestId, String ipAddress, String method,
                          String path, Integer status, String userAgent, String referer, String reason, String source) {
        this.id = id;
        this.createdAt = createdAt;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.method = method;
        this.path = path;
        this.status = status;
        this.userAgent = userAgent;
        this.referer = referer;
        this.reason = reason;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
