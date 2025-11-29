package com.example.demo.util

import com.example.demo.model.BlacklistEvent
import java.time.OffsetDateTime
import java.util.*

object BlacklistEventFactory {
    fun create(
        ipAddress: String,
        reason: String,
        source: String,
        requestId: String? = null,
        method: String? = null,
        path: String? = null,
        status: Int? = null,
        userAgent: String? = null,
        referer: String? = null,
        createdAt: OffsetDateTime? = null
    ): BlacklistEvent {
        return BlacklistEvent(
            createdAt = createdAt ?: OffsetDateTime.now(),
            requestId = requestId ?: UUID.randomUUID().toString(),
            ipAddress = ipAddress,
            method = method,
            path = path,
            status = status,
            userAgent = userAgent,
            referer = referer,
            reason = reason,
            source = source
        )
    }
}

