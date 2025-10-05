package com.example.demo

import com.example.demo.model.Menu
import com.example.demo.service.MenuService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/menus")
class ApiMenuController(
    private val menuService: MenuService
) {
    @GetMapping("/all")
    fun all(): List<Menu> = menuService.getAll()

    @GetMapping("/all-including-deleted")
    fun allIncludingDeleted(): List<Menu> = menuService.getAllIncludingDeleted()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Menu> {
        val menu = menuService.getById(id)
        return if (menu != null) ResponseEntity.ok(menu) else ResponseEntity.notFound().build()
    }

    @PostMapping
    fun create(@Valid @RequestBody menu: Menu): ResponseEntity<Menu> {
        menuService.insert(menu)
        return ResponseEntity.ok(menu)
    }

    @PutMapping
    fun update(@RequestBody menu: Menu): ResponseEntity<Menu> {
        if (menu.id == null) return ResponseEntity.badRequest().build()
        menuService.update(menu)
        return ResponseEntity.ok(menu)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        menuService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
