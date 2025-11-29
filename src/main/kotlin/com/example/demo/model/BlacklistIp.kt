package com.example.demo.model

import java.time.OffsetDateTime

data class BlacklistIp(
    val id: Long? = null,
    val ipAddress: String,
    val createdAt: OffsetDateTime? = null,
    val deleted: Boolean? = null,
    val times: Int? = null
)
