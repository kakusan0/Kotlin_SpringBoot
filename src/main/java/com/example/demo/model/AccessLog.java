package com.example.demo.model;

import java.time.OffsetDateTime;

/**
 * HTTP access log entity.
 */
public class AccessLog {
    private Long id;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private String requestId;
    private String method;
    private String path;
    private String query;
    private Integer status;
    private Long durationMs;
    private String remoteIp;
    private String userAgent;
    private String referer;
    private String username;
    private Long requestBytes;
    private Long responseBytes;

    public AccessLog() {
    }

    public AccessLog(Long id, OffsetDateTime createdAt, String requestId, String method, String path,
                     String query, Integer status, Long durationMs, String remoteIp, String userAgent,
                     String referer, String username, Long requestBytes, Long responseBytes) {
        this.id = id;
        if (createdAt != null) {
            this.createdAt = createdAt;
        }
        this.requestId = requestId;
        this.method = method;
        this.path = path;
        this.query = query;
        this.status = status;
        this.durationMs = durationMs;
        this.remoteIp = remoteIp;
        this.userAgent = userAgent;
        this.referer = referer;
        this.username = username;
        this.requestBytes = requestBytes;
        this.responseBytes = responseBytes;
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

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getRequestBytes() {
        return requestBytes;
    }

    public void setRequestBytes(Long requestBytes) {
        this.requestBytes = requestBytes;
    }

    public Long getResponseBytes() {
        return responseBytes;
    }

    public void setResponseBytes(Long responseBytes) {
        this.responseBytes = responseBytes;
    }
}
