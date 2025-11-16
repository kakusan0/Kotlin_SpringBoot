package com.example.demo

import com.example.demo.model.UserMenu
import com.example.demo.service.UserMenuService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user-menu")
class ApiUserMenuController(
    private val userMenuService: UserMenuService
) {
    @PostMapping
    fun assign(@Valid @RequestBody req: UserMenu): ResponseEntity<UserMenu> =
        ResponseEntity.status(HttpStatus.CREATED).body(req.also { userMenuService.insert(it) })

    @DeleteMapping
    fun unassign(@RequestParam username: String, @RequestParam menuId: Long): ResponseEntity<Void> {
        userMenuService.deleteByUsernameAndMenuId(username, menuId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/assigned-users/{menuId}")
    fun getAssignedUsers(@PathVariable("menuId") menuId: Long): ResponseEntity<List<String>> =
        ResponseEntity.ok(userMenuService.getByMenuId(menuId).mapNotNull { it.username })
}
