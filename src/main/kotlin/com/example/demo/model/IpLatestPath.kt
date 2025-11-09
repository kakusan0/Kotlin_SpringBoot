package com.example.demo.model

data class IpLatestPath(
    val ipAddress: String,
    val path: String?,
    val userAgent: String? = null
)
