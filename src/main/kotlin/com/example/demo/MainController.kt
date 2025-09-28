package com.example.demo

import com.example.demo.constants.ApplicationConstants
import com.example.demo.service.ContentItemService
import com.example.demo.service.MenuService
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.Size

@Controller
@Validated
class MainController(
    private val contentItemService: ContentItemService,
    private val menuService: MenuService
) {
    // 共通のモデル属性をセットするヘルパ
    private fun addCommonAttributes(model: Model) {
        val screens = contentItemService.getAll()
        model.addAttribute("screens", screens)
        val menus = menuService.getAll()
        model.addAttribute("menus", menus)
        // visibleMenus: Home 画面のサイドバーには「pathName が有効」な画面に割り当てられている menuName のみを対象にする
        val assignedMenuNames = screens
            .filter { s ->
                val pn = s.pathName?.trim()
                !pn.isNullOrEmpty() && !pn.equals("null", ignoreCase = true)
            }
            .mapNotNull { it.menuName }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val visibleMenus = if (assignedMenuNames.isEmpty()) {
            emptyList<com.example.demo.model.Menu>()
        } else {
            menus.filter { it.name != null && assignedMenuNames.contains(it.name!!.trim()) }
        }
        model.addAttribute("visibleMenus", visibleMenus)
    }

    @GetMapping(ApplicationConstants.ROOT)
    fun root(model: Model): String {
        addCommonAttributes(model)
        // ensure template has these attributes to avoid Thymeleaf evaluating a null fragment name
        model.addAttribute("currentScreen", "")
        model.addAttribute("selectedScreenName", "メニューを選択")
        model.addAttribute("currentScreenPath", "")
        return "main"
    }

    @GetMapping(ApplicationConstants.CONTENT)
    fun selectItem(
        @RequestParam(name = "screenName", defaultValue = "未選択") @Size(max = 255, message = "screenName: 255文字以内で入力してください") screenName: String,
        model: Model
    ): String {
        model.addAttribute("errorMessage", null)
        addCommonAttributes(model)

        // currentScreen は未選択なら空文字、それ以外はクエリ値をそのまま使う
        val current = if (screenName == "未選択") "" else screenName
        model.addAttribute("currentScreen", current)

        // --- 変更: selectedScreenName は画面名ではなく menuName を優先して表示する ---
        // Find the ContentItem once and reuse. Use trimmed comparison to be tolerant of whitespace.
        val requestedName = if (screenName == "未選択") null else screenName.trim()
        val found = if (requestedName == null) null else contentItemService.getAll().firstOrNull {
            val itemName = it.itemName?.trim()
            if (itemName == null) false else itemName == requestedName
        }

        // If the found item has a non-blank menuName, show that; otherwise fall back to screenName (or placeholder)
        val selected = when {
            requestedName == null -> "メニューを選択"
            found?.menuName?.trim()?.isNotEmpty() == true -> found.menuName!!.trim()
            else -> requestedName
        }
        model.addAttribute("selectedScreenName", selected)

        // 追加: 選択された画面名に対応する ContentItem を探して pathName を currentScreenPath としてモデルにセット
        val currentPath = if (requestedName == null) {
            ""
        } else {
            if (found == null) {
                ""
            } else {
                val pn = found.pathName
                // defensive: treat literal "null" (from bad DB values) as missing
                val normalizedPn = pn?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                if (normalizedPn.isNullOrBlank()) {
                    ""
                } else {
                    val candidate = normalizedPn
                    val resourcePath = "templates/fragments/${candidate}.html"
                    val res = ClassPathResource(resourcePath)
                    if (res.exists()) candidate else {
                        model.addAttribute("errorMessage", "指定された画面（${candidate}）のテンプレートが見つかりません。")
                        ""
                    }
                }
            }
        }
        model.addAttribute("currentScreenPath", currentPath)

        return "main"
    }
}
