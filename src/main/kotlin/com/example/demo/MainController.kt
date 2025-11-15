package com.example.demo

import com.example.demo.constants.ApplicationConstants
import com.example.demo.service.ContentItemService
import com.example.demo.service.MenuService
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@Validated
class MainController(
    private val contentItemService: ContentItemService,
    private val menuService: MenuService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MainController::class.java)
    }

    private fun addCommonAttributes(model: Model) {
        val screens = contentItemService.getAllForHome()
        val menus = menuService.getAll()

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
                if (assignedNames.isEmpty()) emptyList()
                else menus.filter { it.name?.trim() in assignedNames }
            }

        model.addAttribute("visibleMenus", visibleMenus)
    }

    @GetMapping(ApplicationConstants.ROOT)
    fun index(): String = "${ApplicationConstants.REDIRECT}${ApplicationConstants.HOME}"

    @GetMapping(ApplicationConstants.HOME)
    fun root(model: Model): String {
        addCommonAttributes(model)
        model.addAttribute("currentScreen", "")
        model.addAttribute("selectedScreenName", "メニューを選択")
        model.addAttribute("currentScreenPath", "")
        return "main"
    }

    @GetMapping(ApplicationConstants.CONTENT)
    fun selectItem(
        @RequestParam(name = "menuName", defaultValue = "未選択")
        @Size(max = 255, message = "menuName: 255文字以内で入力してください")
        menuName: String,
        @RequestParam(name = "screenName", defaultValue = "未選択")
        @Size(max = 255, message = "screenName: 255文字以内で入力してください")
        screenName: String,
        model: Model
    ): String {
        model.addAttribute("errorMessage", null)
        addCommonAttributes(model)

        val requestedName = screenName.takeIf { it != "未選択" }?.trim()
        val found = requestedName?.let { name ->
            contentItemService.getAllForHome().find { it.itemName?.trim() == name }
        }

        val currentScreen = requestedName ?: ""
        val selectedName = when {
            requestedName == null -> "メニューを選択"
            found?.menuName?.trim()?.isNotEmpty() == true -> found.menuName!!.trim()
            else -> requestedName
        }

        val currentPath = found?.pathName?.trim()
            ?.takeIf { it.isNotEmpty() && it != "null" }
            ?.let { pathName ->
                if (ClassPathResource("templates/fragments/$pathName.html").exists()) {
                    pathName
                } else {
                    model.addAttribute("errorMessage", "指定された画面（$pathName）のテンプレートが見つかりません。")
                    ""
                }
            } ?: ""

        model.apply {
            addAttribute("currentScreen", currentScreen)
            addAttribute("selectedScreenName", selectedName)
            addAttribute("currentScreenPath", currentPath)
        }

        return "main"
    }
}
