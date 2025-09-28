package com.example.demo.model

import java.time.LocalDateTime
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Pattern

data class Path(
    var id: Long? = null,
    @field:Size(max = 100, message = "パス名は最大100文字までです")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "パス名は英数字、アンダースコア、ハイフンのみ使用できます")
    var name: String? = null,
    var deleted: Boolean? = false,
    var createdAt: LocalDateTime? = null,
    var updatedAt: LocalDateTime? = null
)

