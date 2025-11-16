package com.example.demo.model

import java.time.OffsetDateTime

data class RoleMenu(
    var id: Long? = null,
    var roleName: String? = null,
    var menuId: Long? = null,
    var createdAt: OffsetDateTime? = null
)

