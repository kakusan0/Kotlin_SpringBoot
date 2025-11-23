package com.example.demo

import com.example.demo.constants.ApplicationConstants
import com.example.demo.service.ContentItemService
import com.example.demo.util.TimesheetGenerator
import jakarta.validation.constraints.Size
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.YearMonth

@Controller
@Validated
class MainController(
    private val contentItemService: ContentItemService
) {
    @GetMapping(ApplicationConstants.ROOT)
    @Suppress("SpringMVCViewInspection")
    fun index(): String = "${ApplicationConstants.REDIRECT}${ApplicationConstants.HOME}"

    @GetMapping(ApplicationConstants.HOME)
    fun root(model: Model): String {
        model.addAttribute("currentScreen", "")
        model.addAttribute("selectedScreenName", "ホーム")
        model.addAttribute("currentScreenPath", "")
        return "main"
    }

    @GetMapping("/tools")
    fun tools(model: Model): String {
        val screens = contentItemService.getAllForHome()
            .filter { screen ->
                screen.pathName?.trim()?.takeIf { it.isNotEmpty() && it != "null" } != null &&
                        screen.itemName?.trim()?.isNotEmpty() == true &&
                        screen.enabled == true
            }
        model.addAttribute("screens", screens)
        model.addAttribute("currentScreen", "tools")
        model.addAttribute("selectedScreenName", "ツール")
        model.addAttribute("currentScreenPath", "toolsList")
        return "main"
    }

    @GetMapping("/timesheet")
    fun timesheet(model: Model): String {
        val yearMonth = YearMonth.now()
        val dates = TimesheetGenerator.generateDates(yearMonth)
        model.addAttribute("currentScreen", "timesheet")
        model.addAttribute("selectedScreenName", "勤務表")
        model.addAttribute("currentScreenPath", "timesheetMonth")
        model.addAttribute("monthDisplay", TimesheetGenerator.formatYearMonth(yearMonth))
        model.addAttribute("dates", dates)
        return "main"
    }

    @GetMapping(ApplicationConstants.CONTENT)
    fun selectItem(
        @RequestParam(name = "screenName", defaultValue = "未選択")
        @Size(max = 255, message = "screenName: 255文字以内で入力してください")
        screenName: String,
        model: Model
    ): String {
        model.addAttribute("errorMessage", null)

        val requestedName = screenName.takeIf { it != "未選択" }?.trim()
        val found = requestedName?.let { name ->
            contentItemService.getAllForHome().find { it.itemName?.trim() == name }
        }

        val currentScreen = requestedName ?: ""
        val selectedName = found?.itemName?.trim() ?: requestedName ?: "画面を選択"

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
