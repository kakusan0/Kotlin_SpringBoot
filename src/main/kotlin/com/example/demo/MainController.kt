package com.example.demo

import com.example.demo.constants.ApplicationConstants
import com.example.demo.service.ContentItemService
import com.example.demo.service.MenuService
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
    @GetMapping(ApplicationConstants.ROOT)
    fun root(model: Model): String {
        val screens = contentItemService.getAll()
        model.addAttribute("screens", screens)
        val menus = menuService.getAll()
        model.addAttribute("menus", menus)
        return "main"
    }

    @GetMapping(ApplicationConstants.CONTENT)
    fun selectItem(
        @RequestParam(name = "screenName", defaultValue = "未選択") @Size(max = 255, message = "screenName: 255文字以内で入力してください") screenName: String,
        model: Model
    ): String {
        model.addAttribute("errorMessage", null)
        val screens = contentItemService.getAll()
        model.addAttribute("screens", screens)
        val menus = menuService.getAll()
        model.addAttribute("menus", menus)
        val current = if (screenName == "未選択") "" else screenName
        model.addAttribute("currentScreen", current)
        model.addAttribute("selectedScreenName", if (screenName == "未選択") "画面を選択" else screenName)
        return "main"
    }
}
