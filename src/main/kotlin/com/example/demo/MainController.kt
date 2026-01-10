package com.example.demo

import com.example.demo.constants.ApplicationConstants
import com.example.demo.util.TimesheetGenerator
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.YearMonth

@Controller
@Validated
class MainController {
    @GetMapping(ApplicationConstants.ROOT)
    @Suppress("SpringMVCViewInspection")
    fun index(): String = "${ApplicationConstants.REDIRECT}/tools"

    @GetMapping("/tools")
    fun tools(model: Model): String {
        model.addAttribute("screens", emptyList<Any>())
        model.addAttribute("currentScreen", "ツール")
        model.addAttribute("selectedScreenName", "ツール")
        model.addAttribute("currentScreenPath", "toolsList")
        return "main"
    }

    @GetMapping("/timesheet")
    fun timesheet(
        @RequestParam(name = "month", required = false) monthParam: String?,
        model: Model,
        principal: Authentication
    ): String {
        val yearMonth = monthParam
            ?.takeIf { it.matches(Regex("\\d{4}-\\d{2}")) }
            ?.let {
                runCatching { YearMonth.parse(it) }.getOrElse { YearMonth.now() }
            } ?: YearMonth.now()
        val dates = TimesheetGenerator.generateDates(yearMonth)
        model.apply {
            addAttribute("currentScreen", "勤務表")
            addAttribute("selectedScreenName", "勤務表")
            addAttribute("currentScreenPath", "timesheetMonth")
            addAttribute("monthDisplay", TimesheetGenerator.formatYearMonth(yearMonth))
            addAttribute("yearMonthValue", yearMonth.toString())
            addAttribute("dates", dates)
            addAttribute("currentUserName", principal.name)
        }
        return "main"
    }

    @GetMapping("/tools/password")
    fun passwordTool(model: Model): String {
        model.addAttribute("screens", emptyList<Any>())
        model.addAttribute("currentScreen", "パスワード生成")
        model.addAttribute("selectedScreenName", "パスワード生成")
        model.addAttribute("currentScreenPath", "passwordGeneration")
        return "main"
    }

    @GetMapping("/tools/passkey")
    fun passkeyTool(model: Model, principal: Authentication?): String {
        model.addAttribute("screens", emptyList<Any>())
        model.addAttribute("currentScreen", "パスキー")
        model.addAttribute("selectedScreenName", "パスキー")
        model.addAttribute("currentScreenPath", "passkeyTools")
        principal?.name?.let { model.addAttribute("currentUserName", it) }
        return "main"
    }
}
