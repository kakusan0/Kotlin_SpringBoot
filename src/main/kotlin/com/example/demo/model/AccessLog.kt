package com.example.demo.model

import java.time.OffsetDateTime

/**
 * HTTPアクセスログのエンティティ
 */
data class AccessLog(
    val id: Long? = null,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    val requestId: String,
    val method: String,
    val path: String,
    val query: String? = null,
    val status: Int,
    val durationMs: Long? = null,
    val remoteIp: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val username: String? = null,
    val requestBytes: Long? = null,
    val responseBytes: Long? = null,
)
