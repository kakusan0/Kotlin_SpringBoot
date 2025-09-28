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
    // 共通のモデル属性をセットするヘルパ
    private fun addCommonAttributes(model: Model) {
        val screens = contentItemService.getAll()
        model.addAttribute("screens", screens)
        val menus = menuService.getAll()
        model.addAttribute("menus", menus)
    }

    @GetMapping(ApplicationConstants.ROOT)
    fun root(model: Model): String {
        addCommonAttributes(model)
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

        // selectedScreenName は未選択のときはプレースホルダ。screenName が与えられていればそれを優先して表示。
        val selected = if (screenName == "未選択") "メニューを選択" else screenName
        model.addAttribute("selectedScreenName", selected)

        return "main"
    }
}
