package com.example.demo.model

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class ContentItem(
    var id: Long? = null,
    var itemName: String? = null,
    var menuName: String? = null,
    @field:Size(max = 100, message = "pathName は最大100文字までです")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "pathName は英数字、アンダースコア、ハイフンのみ使用できます")
    var pathName: String? = null,
    var createdAt: OffsetDateTime? = null,
    var updatedAt: OffsetDateTime? = null,
    var enabled: Boolean = true
)
