package com.example.demo

import com.example.demo.model.Menu
import com.example.demo.service.MenuService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/menus")
class ApiMenuController(
    private val menuService: MenuService
) {
    @GetMapping("/all")
    fun all(): ResponseEntity<List<Menu>> =
        ResponseEntity.ok(menuService.getAll())

    @GetMapping("/all-including-deleted")
    fun allIncludingDeleted(): ResponseEntity<List<Menu>> =
        ResponseEntity.ok(menuService.getAllIncludingDeleted())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Menu> =
        menuService.getById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping
    fun create(@Valid @RequestBody menu: Menu): ResponseEntity<Menu> =
        ResponseEntity.status(HttpStatus.CREATED).body(menu.also { menuService.insert(it) })

    @PutMapping
    fun update(@Valid @RequestBody menu: Menu): ResponseEntity<Menu> {
        require(menu.id != null) { "IDは更新時に必須です" }
        menuService.update(menu)
        return ResponseEntity.ok(menu)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        menuService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
