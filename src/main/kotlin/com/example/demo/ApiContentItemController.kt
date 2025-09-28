package com.example.demo

import com.example.demo.model.ContentItem
import com.example.demo.service.ContentItemService
import com.example.demo.service.PathService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

@RestController
@RequestMapping("/api/content")
class ApiContentItemController(
    private val contentItemService: ContentItemService,
    private val pathService: PathService
) {
    @GetMapping("/all")
    fun all(): List<ContentItem> = contentItemService.getAll()

    @GetMapping
    fun all(@RequestParam(required = false) menuName: String?): List<ContentItem> {
        val items = if (menuName.isNullOrBlank()) contentItemService.getAll()
        else contentItemService.getByMenuName(menuName)
        return applyPathEnabledFilter(items)
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

    private fun applyPathEnabledFilter(items: List<ContentItem>): List<ContentItem> {
        // 有効なパス名一覧をセット化（null/空は除外）
        val activePathNames: Set<String> = pathService.getAllActive()
            .mapNotNull { it.name?.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (activePathNames.isEmpty()) {
            // パスマスタ未定義または全て無効の場合、すべての pathName を null 扱いで返す
            return items.map { it.copy(pathName = null) }
        }
        return items.map { item ->
            val pn = item.pathName?.trim().orEmpty()
            if (pn.isEmpty() || !activePathNames.contains(pn)) item.copy(pathName = null) else item
        }
    }
}
