package com.example.demo.model

import java.time.LocalDateTime

data class BlacklistIp(
    val id: Long? = null,
    val ipAddress: String,
    val createdAt: LocalDateTime? = null,
    val deleted: Boolean? = null,
    val times: Int? = null
)
