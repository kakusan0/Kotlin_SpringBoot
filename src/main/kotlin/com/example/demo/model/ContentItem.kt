package com.example.demo.model

import java.time.LocalDateTime

data class ContentItem(
    var id: Long? = null,
    var itemName: String? = null,
    var createdAt: LocalDateTime? = null,
    var updatedAt: LocalDateTime? = null
)
