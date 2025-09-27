package com.example.demo.model.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * REST用の入力DTO（作成/更新）
 */
data class ContentItemRequest(
    @field:NotBlank(message = "itemName: 必須です")
    @field:Size(max = 255, message = "itemName: 255文字以内で入力してください")
    val itemName: String
)

