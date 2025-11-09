package com.example.demo.model

data class UaBlacklistRule(
    val id: Long? = null,
    val pattern: String,
    val matchType: String = "EXACT",
    val deleted: Boolean = false,
)

