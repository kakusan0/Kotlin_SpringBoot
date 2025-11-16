package com.example.demo

import com.example.demo.model.Menu
import com.example.demo.model.MenuSetting
import com.example.demo.model.RoleMenu
import com.example.demo.service.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/menus")
class ApiMenuController(
    private val menuService: MenuService,
    private val userMenuService: UserMenuService,
    private val menuSettingService: MenuSettingService,
    private val roleMenuService: RoleMenuService,
    private val effectiveMenuService: EffectiveMenuService
) {
    @GetMapping("/all")
    fun all(): ResponseEntity<List<Menu>> {
        val auth = SecurityContextHolder.getContext().authentication
        val isAuthenticated = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        val isAdmin = isAuthenticated && auth!!.authorities.any { it.authority == "ROLE_ADMIN" }

        val allMenus = menuService.getAll()
        val result = if (isAdmin) allMenus else {
            val allowedIds = effectiveMenuService.getAllowedMenuIds(auth)
            allMenus.filter { it.id in allowedIds }
        }
        return ResponseEntity.ok(result)
    }

    // 必須メニューフラグの一覧取得（管理用）
    @GetMapping("/settings")
    fun getMenuSettings(): ResponseEntity<List<MenuSetting>> =
        ResponseEntity.ok(menuSettingService.getAll())

    // 必須メニューフラグの更新（管理者のみと想定）
    @PutMapping("/settings")
    fun upsertMenuSetting(@RequestBody setting: MenuSetting): ResponseEntity<Void> {
        require(setting.menuId != null) { "menuId は必須です" }
        menuSettingService.upsert(setting)
        return ResponseEntity.noContent().build()
    }

    // ロール別の割当一覧（管理用）
    @GetMapping("/role-assignments/{role}")
    fun getRoleAssignments(@PathVariable("role") role: String): ResponseEntity<List<Long>> =
        ResponseEntity.ok(roleMenuService.getMenuIdsByRole(role))

    // ロールにメニューを追加
    @PostMapping("/role-assignments")
    fun assignRoleMenu(@RequestBody req: RoleMenu): ResponseEntity<RoleMenu> =
        ResponseEntity.status(HttpStatus.CREATED).body(req.also { roleMenuService.insert(it) })

    // ロールからメニューを外す
    @DeleteMapping("/role-assignments")
    fun removeRoleMenu(@RequestParam role: String, @RequestParam menuId: Long): ResponseEntity<Void> {
        roleMenuService.deleteByRoleAndMenu(role, menuId)
        return ResponseEntity.noContent().build()
    }

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
