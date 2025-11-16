package com.example.demo

import com.example.demo.constants.ApplicationConstants
import com.example.demo.service.ContentItemService
import com.example.demo.service.EffectiveMenuService
import com.example.demo.service.MenuService
import com.example.demo.service.UserMenuService
import jakarta.validation.constraints.Size
import org.springframework.core.io.ClassPathResource
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@Validated
class MainController(
    private val contentItemService: ContentItemService,
    private val menuService: MenuService,
    private val userMenuService: UserMenuService,
    private val effectiveMenuService: EffectiveMenuService
) {
    private fun addCommonAttributes(model: Model) {
        val auth = SecurityContextHolder.getContext().authentication
        val isAuthenticated = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        val isAdmin = isAuthenticated && auth.authorities.any { it.authority == "ROLE_ADMIN" }
        val username = if (isAuthenticated) auth.name else null

        val allowedMenuIds = if (isAdmin) {
            menuService.getAll().mapNotNull { it.id }
        } else {
            effectiveMenuService.getAllowedMenuIds(auth)
        }
        val allMenus = menuService.getAll()
        val menus = allMenus.filter { it.id in allowedMenuIds }
        val allowedMenuNames = menus.mapNotNull { it.name?.trim() }.toSet()

        val allScreens = contentItemService.getAllForHome()
        val screens = when {
            isAdmin -> allScreens.filter { it.menuName?.trim() in allowedMenuNames }
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

        model.addAttribute("screens", screens)
        model.addAttribute("menus", menus)

        val visibleMenus = screens
            .filter { screen ->
                screen.pathName?.trim()?.takeIf { it.isNotEmpty() && it != "null" } != null &&
                        screen.itemName?.trim()?.isNotEmpty() == true &&
                        screen.menuName?.trim()?.isNotEmpty() == true
            }
            .mapNotNull { it.menuName?.trim() }
            .toSet()
            .let { assignedNames ->
                if (assignedNames.isEmpty()) emptyList() else menus.filter { it.name?.trim() in assignedNames }
            }

        model.addAttribute("visibleMenus", visibleMenus)
    }

    @GetMapping(ApplicationConstants.ROOT)
    fun index(): String = "${ApplicationConstants.REDIRECT}${ApplicationConstants.HOME}"

    @GetMapping(ApplicationConstants.HOME)
    fun root(model: Model): String {
        val auth = SecurityContextHolder.getContext().authentication
        val isAuthenticated = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        addCommonAttributes(model)
        model.addAttribute("currentScreen", "")
        model.addAttribute("selectedScreenName", "メニューを選択")
        model.addAttribute("currentScreenPath", "")
        model.addAttribute("isUserAuthenticated", isAuthenticated)
        return "main"
    }

    @GetMapping(ApplicationConstants.CONTENT)
    fun selectItem(
        @RequestParam(name = "menuName", defaultValue = "未選択") @Size(max = 255) menuName: String,
        @RequestParam(name = "screenName", defaultValue = "未選択") @Size(max = 255) screenName: String,
        model: Model
    ): String {
        model.addAttribute("errorMessage", null)
        addCommonAttributes(model)
        val requestedName = screenName.takeIf { it != "未選択" }?.trim()
        val found =
            requestedName?.let { name -> contentItemService.getAllForHome().find { it.itemName?.trim() == name } }
        val currentScreen = requestedName ?: ""
        val selectedName = when {
            requestedName == null -> "メニューを選択"
            found?.menuName?.trim()?.isNotEmpty() == true -> found.menuName!!.trim()
            else -> requestedName
        }
        val currentPath = found?.pathName?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.let { pathName ->
            if (ClassPathResource("templates/fragments/$pathName.html").exists()) pathName else {
                model.addAttribute("errorMessage", "指定された画面（$pathName）のテンプレートが見つかりません。")
                ""
            }
        } ?: ""
        model.addAttribute("currentScreen", currentScreen)
        model.addAttribute("selectedScreenName", selectedName)
        model.addAttribute("currentScreenPath", currentPath)
        return "main"
    }
}
