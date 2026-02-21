package com.example.demo.util;

import com.example.demo.model.BlacklistEvent;
import lombok.experimental.UtilityClass;

import java.time.OffsetDateTime;
import java.util.UUID;

@UtilityClass
public class BlacklistEventFactory {

    public BlacklistEvent create(
            String ipAddress,
            String reason,
            String source,
            String requestId,
            String method,
            String path,
            Integer status,
            String userAgent,
            String referer,
            OffsetDateTime createdAt
    ) {
        return BlacklistEvent.builder()
                .createdAt(createdAt != null ? createdAt : OffsetDateTime.now())
                .requestId(requestId != null ? requestId : UUID.randomUUID().toString())
                .ipAddress(ipAddress)
                .method(method)
                .path(path)
                .status(status)
                .userAgent(userAgent)
                .referer(referer)
                .reason(reason)
                .source(source)
                .build();
    }
}
