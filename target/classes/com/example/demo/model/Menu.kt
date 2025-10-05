package com.example.demo.model

import java.time.OffsetDateTime

data class Menu(
    var id: Long? = null,
    var name: String? = null,
    var createdAt: OffsetDateTime? = null,
    var updatedAt: OffsetDateTime? = null
)
