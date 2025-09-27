package com.example.demo.rest

import com.example.demo.model.ContentItem
import com.example.demo.model.dto.ContentItemRequest
import com.example.demo.service.ContentItemService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/content-items")
@Validated
class ContentItemRestController(
    private val service: ContentItemService
) {
    @PostMapping
    fun create(@Valid @RequestBody req: ContentItemRequest): ResponseEntity<ContentItem> {
        val now = LocalDateTime.now()
        val record = ContentItem(
            itemName = req.itemName,
            createdAt = now,
            updatedAt = now
        )
        service.insert(record)
        // MyBatis useGeneratedKeys=true により record.id が設定される想定
        return ResponseEntity.status(HttpStatus.CREATED).body(record)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable @Min(1, message = "id: 1以上を指定してください") id: Long): ResponseEntity<ContentItem> =
        service.getById(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{id}")
    fun update(
        @PathVariable @Min(1, message = "id: 1以上を指定してください") id: Long,
        @Valid @RequestBody req: ContentItemRequest
    ): ResponseEntity<ContentItem> {
        val now = LocalDateTime.now()
        val record = ContentItem(
            id = id,
            itemName = req.itemName,
            updatedAt = now
        )
        val updated = service.update(record)
        return if (updated > 0) ResponseEntity.ok(record) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable @Min(1, message = "id: 1以上を指定してください") id: Long): ResponseEntity<Void> {
        val deleted = service.delete(id)
        return if (deleted > 0) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}

