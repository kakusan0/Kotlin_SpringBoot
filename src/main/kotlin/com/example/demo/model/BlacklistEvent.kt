package com.example.demo.model

import java.time.OffsetDateTime

data class BlacklistEvent(
    val id: Long? = null,
    val createdAt: OffsetDateTime? = null,
    val requestId: String? = null,
    val ipAddress: String,
    val method: String? = null,
    val path: String? = null,
    val status: Int? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val reason: String,
    val source: String,
)

