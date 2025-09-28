package com.example.demo.model

import java.time.LocalDateTime

data class Menu(
    var id: Long? = null,
    var name: String? = null,
    var createdAt: LocalDateTime? = null,
    var updatedAt: LocalDateTime? = null
)

