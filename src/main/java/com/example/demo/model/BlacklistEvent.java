package com.example.demo.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
}
