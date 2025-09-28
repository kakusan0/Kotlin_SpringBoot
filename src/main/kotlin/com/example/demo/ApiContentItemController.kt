package com.example.demo

import com.example.demo.model.ContentItem
import com.example.demo.service.ContentItemService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/content")
class ApiContentItemController(
    private val contentItemService: ContentItemService
) {
    @GetMapping("/all")
    fun all(): List<ContentItem> = contentItemService.getAll()

    @PostMapping
    fun create(@RequestBody item: ContentItem): ResponseEntity<ContentItem> {
        contentItemService.insert(item)
        return ResponseEntity.ok(item)
    }

    @PutMapping
    fun update(@RequestBody item: ContentItem): ResponseEntity<ContentItem> {
        if (item.id == null) return ResponseEntity.badRequest().build()
        contentItemService.update(item)
        return ResponseEntity.ok(item)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        contentItemService.delete(id)
        return ResponseEntity.noContent().build()
    }
}

