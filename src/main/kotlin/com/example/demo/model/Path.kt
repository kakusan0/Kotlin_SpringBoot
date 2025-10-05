package com.example.demo.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class Path(
    var id: Long? = null,

    @field:NotBlank(message = "パス名は必須です")
    @field:Size(max = 100, message = "パス名は最大100文字までです")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "パス名は英数字、アンダースコア、ハイフンのみ使用できます")
    var name: String? = null,

    var deleted: Boolean? = false,
    var createdAt: OffsetDateTime? = null,
    var updatedAt: OffsetDateTime? = null
)
