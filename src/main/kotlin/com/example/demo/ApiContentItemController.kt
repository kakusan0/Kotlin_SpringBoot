package com.example.demo

import com.example.demo.model.ContentItem
import com.example.demo.service.ContentItemService
import com.example.demo.service.MenuService
import com.example.demo.service.UserMenuService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/content")
class ApiContentItemController(
    private val contentItemService: ContentItemService,
    private val menuService: MenuService,
    private val userMenuService: UserMenuService
) {
    @GetMapping("/all")
    fun all(): ResponseEntity<List<ContentItem>> =
        ResponseEntity.ok(contentItemService.getAll())

    @GetMapping("/all-for-home")
    fun allForHome(): ResponseEntity<List<ContentItem>> {
        val auth = SecurityContextHolder.getContext().authentication
        val isAuthenticated = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        val isAdmin = isAuthenticated && auth.authorities.any { it.authority == "ROLE_ADMIN" }
        val username = if (isAuthenticated) auth.name else null

        val allMenus = menuService.getAll()
        val allowedMenuIds: List<Long> = if (isAdmin) {
            allMenus.mapNotNull { it.id }
        } else {
            val key = username ?: "default"
            userMenuService.getMenuIdsByUsername(key)
        }
        val menus = allMenus.filter { it.id in allowedMenuIds }
        val allowedMenuNames = menus.mapNotNull { it.name?.trim() }.toSet()

        val allScreens = contentItemService.getAllForHome()

        val filtered = when {
            isAdmin -> allScreens.filter { s -> s.menuName?.trim() in allowedMenuNames }
            isAuthenticated -> allScreens.filter { s ->
                val menuOk = s.menuName?.trim() in allowedMenuNames
                val userOk = (s.username == null) || (s.username == username)
                menuOk && userOk
            }

            else -> allScreens.filter { s ->
                val menuOk = s.menuName?.trim() in allowedMenuNames
                val userOk = (s.username == null) || (s.username == "admin")
                menuOk && userOk
            }
        }
        return ResponseEntity.ok(filtered)
    }

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
