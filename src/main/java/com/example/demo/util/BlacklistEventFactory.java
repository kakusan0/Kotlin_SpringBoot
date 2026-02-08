package com.example.demo.util;

import com.example.demo.model.BlacklistEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class BlacklistEventFactory {
    private BlacklistEventFactory() {
    }

    public static BlacklistEvent create(
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
        OffsetDateTime created = createdAt != null ? createdAt : OffsetDateTime.now();
        String reqId = requestId != null ? requestId : UUID.randomUUID().toString();
        return new BlacklistEvent(
                null,
                created,
                reqId,
                ipAddress,
                method,
                path,
                status,
                userAgent,
                referer,
                reason,
                source
        );
    }
}
