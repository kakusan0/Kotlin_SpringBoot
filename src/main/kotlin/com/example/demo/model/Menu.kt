package com.example.demo.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class Menu(
    var id: Long? = null,

    @field:NotBlank(message = "メニュー名は必須です")
    @field:Size(max = 255, message = "メニュー名は最大255文字までです")
    var name: String? = null,

    var createdAt: OffsetDateTime? = null,
    var updatedAt: OffsetDateTime? = null
)
