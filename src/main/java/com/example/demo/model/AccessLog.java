package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * HTTP access log entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessLog {
    private Long id;
    @Builder.Default
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
}
