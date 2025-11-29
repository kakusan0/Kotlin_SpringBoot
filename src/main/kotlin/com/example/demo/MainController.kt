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
        model.addAttribute("screens", emptyList<Any>())
        model.addAttribute("currentScreen", "tools")
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
            addAttribute("currentScreen", "timesheet")
            addAttribute("selectedScreenName", "勤務表")
            addAttribute("currentScreenPath", "timesheetMonth")
            addAttribute("monthDisplay", TimesheetGenerator.formatYearMonth(yearMonth))
            addAttribute("yearMonthValue", yearMonth.toString())
            addAttribute("dates", dates)
            addAttribute("currentUserName", principal.name)
        }
        return "main"
    }
}
