package com.example.demo

import com.example.demo.model.ContentItem
import com.example.demo.service.ContentItemService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/content")
class ApiContentItemController(
    private val contentItemService: ContentItemService
) {
    @GetMapping("/all")
    fun all(): ResponseEntity<List<ContentItem>> =
        ResponseEntity.ok(contentItemService.getAll())

    @GetMapping("/all-for-home")
    fun allForHome(): ResponseEntity<List<ContentItem>> =
        ResponseEntity.ok(contentItemService.getAllForHome())

    @PostMapping
    fun create(@Valid @RequestBody item: ContentItem): ResponseEntity<ContentItem> =
        ResponseEntity.status(HttpStatus.CREATED).body(item.also { contentItemService.insert(it) })

    @PutMapping
    fun update(@Valid @RequestBody item: ContentItem): ResponseEntity<ContentItem> {
        require(item.id != null) { "IDは更新時に必須です" }
        contentItemService.update(item)
        return ResponseEntity.ok(item)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        contentItemService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
