package com.example.demo.model

import java.time.LocalDateTime

data class MyDnsLog(
    val id: Long? = null,
    val executedAt: LocalDateTime,
    val success: Boolean,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val ipAddress: String? = null,
    val errorMessage: String? = null,
    val createdAt: LocalDateTime? = null
)
