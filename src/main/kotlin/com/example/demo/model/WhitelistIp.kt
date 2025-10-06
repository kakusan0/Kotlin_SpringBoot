package com.example.demo.model

import java.time.LocalDateTime

data class WhitelistIp(
    val id: Long? = null,
    val ipAddress: String,
    val createdAt: LocalDateTime? = null,
    val blacklisted: Boolean? = null,
    val blacklistedCount: Int? = null
)
