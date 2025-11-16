package com.example.demo.service

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

@Service
class EffectiveMenuService(
    private val menuSettingService: MenuSettingService,
    private val userMenuService: UserMenuService,
    private val roleMenuService: RoleMenuService,
    private val menuService: MenuService
) {
    fun getAllowedMenuIds(auth: Authentication?): List<Long> {
        val isAuthenticated = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        val username = if (isAuthenticated) auth.name else null
        val authorities = auth?.authorities?.map { it.authority } ?: emptyList()

        val required = menuSettingService.getAll().filter { it.required }.mapNotNull { it.menuId }

        val userAssigned: List<Long> = when {
            isAuthenticated && username != null -> userMenuService.getMenuIdsByUsername(username)
            else -> {
                // 未ログイン: 管理者(admin)の割当を優先。無ければ default をフォールバック。
                val adminAssigned = userMenuService.getMenuIdsByUsername("admin")
                if (adminAssigned.isNotEmpty()) adminAssigned else userMenuService.getMenuIdsByUsername("default")
            }
        }

        val roleAssigned = authorities.flatMap { role -> roleMenuService.getMenuIdsByRole(role) }

        val existingIds = menuService.getAll().mapNotNull { it.id }.toSet()

        return (required + userAssigned + roleAssigned)
            .filter { it in existingIds }
            .distinct()
    }
}
