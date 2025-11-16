package com.example.demo.model

import java.time.OffsetDateTime

data class UserMenu(
    var id: Long? = null,
    var username: String? = null,
    var menuId: Long? = null,
    var createdAt: OffsetDateTime? = null
)

