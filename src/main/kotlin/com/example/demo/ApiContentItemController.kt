package com.example.demo

import com.example.demo.model.ContentItem
import com.example.demo.service.ContentItemService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

@RestController
@RequestMapping("/api/content")
class ApiContentItemController(
    private val contentItemService: ContentItemService
) {
    @GetMapping("/all")
    fun all(): List<ContentItem> = contentItemService.getAll()

    @GetMapping
    fun all(@RequestParam(required = false) menuName: String?): List<ContentItem> {
        return if (menuName.isNullOrBlank()) contentItemService.getAll()
        else contentItemService.getByMenuName(menuName)
    }

    @PostMapping
    fun create(@Valid @RequestBody item: ContentItem): ResponseEntity<ContentItem> {
        contentItemService.insert(item)
        return ResponseEntity.ok(item)
    }

    @PutMapping
    fun update(@Valid @RequestBody item: ContentItem): ResponseEntity<ContentItem> {
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
