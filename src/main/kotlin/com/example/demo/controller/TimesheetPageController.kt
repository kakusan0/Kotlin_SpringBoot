package com.example.demo.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class TimesheetPageController {
    @GetMapping("/timesheet/ui")
    fun page(): String {
        // 初期表示で特別なデータは JS が fetch するので空
        return "timesheet"
    }
}
