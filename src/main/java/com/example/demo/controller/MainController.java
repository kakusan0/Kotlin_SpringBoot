package com.example.demo.controller;

import com.example.demo.util.TimesheetGenerator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

@Controller
@Validated
public class MainController {

    private static final Pattern YM_PATTERN = Pattern.compile("\\d{4}-\\d{2}");

    @GetMapping("/")
    public String index() {
        return "redirect:/tools";
    }

    @GetMapping("/tools")
    public String tools(Model model) {
        model.addAttribute("screens", List.of());
        model.addAttribute("currentScreen", "ツール");
        model.addAttribute("selectedScreenName", "ツール");
        model.addAttribute("currentScreenPath", "toolsList");
        return "main";
    }

    @GetMapping("/timesheet")
    public String timesheet(
            @RequestParam(name = "month", required = false) String monthParam,
            Model model,
            Authentication principal
    ) {
        YearMonth yearMonth = YearMonth.now();
        if (monthParam != null && YM_PATTERN.matcher(monthParam).matches()) {
            try {
                yearMonth = YearMonth.parse(monthParam);
            } catch (Exception ignored) {
            }
        }
        var dates = TimesheetGenerator.generateDates(yearMonth);
        model.addAttribute("currentScreen", "勤務表");
        model.addAttribute("selectedScreenName", "勤務表");
        model.addAttribute("currentScreenPath", "timesheetMonth");
        model.addAttribute("monthDisplay", TimesheetGenerator.formatYearMonth(yearMonth));
        model.addAttribute("yearMonthValue", yearMonth.toString());
        model.addAttribute("dates", dates);
        model.addAttribute("currentUserName", principal.getName());
        return "main";
    }

    @GetMapping("/tools/password")
    public String passwordTool(Model model) {
        model.addAttribute("screens", List.of());
        model.addAttribute("currentScreen", "パスワード生成");
        model.addAttribute("selectedScreenName", "パスワード生成");
        model.addAttribute("currentScreenPath", "passwordGeneration");
        return "main";
    }

    @GetMapping("/tools/passkey")
    public String passkeyTool(Model model, Authentication principal) {
        model.addAttribute("screens", List.of());
        model.addAttribute("currentScreen", "パスキー");
        model.addAttribute("selectedScreenName", "パスキー");
        model.addAttribute("currentScreenPath", "passkeyTools");
        if (principal != null) {
            model.addAttribute("currentUserName", principal.getName());
        }
        return "main";
    }
}
