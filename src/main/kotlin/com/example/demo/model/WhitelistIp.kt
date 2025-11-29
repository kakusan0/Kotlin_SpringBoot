package com.example.demo.model

import java.time.OffsetDateTime

data class WhitelistIp(
    val id: Long? = null,
    val ipAddress: String,
    val createdAt: OffsetDateTime? = null,
    val blacklisted: Boolean? = null,
    val blacklistedCount: Int? = null
)
