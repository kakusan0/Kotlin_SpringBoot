package com.example.demo.controller

import com.example.demo.model.UserSettings
import com.example.demo.service.UserSettingsService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalTime

@RestController
@RequestMapping("/api/user-settings")
class UserSettingsController(private val userSettingsService: UserSettingsService) {

    /**
     * ユーザー設定を取得
     */
    @GetMapping
    fun getSettings(auth: Authentication): UserSettings? {
        return userSettingsService.getSettings(auth.name)
    }

    /**
     * ユーザー設定を保存または更新
     */
    @PostMapping
    fun saveSettings(auth: Authentication, @RequestBody body: Map<String, String?>): UserSettings {
        val settings = UserSettings(
            userName = auth.name,
            companyAffiliation = body["companyAffiliation"]?.takeIf { it.isNotBlank() },
            section = body["section"]?.takeIf { it.isNotBlank() }?.toIntOrNull(),
            branchOffice = body["branchOffice"]?.takeIf { it.isNotBlank() },
            workGroup = body["workGroup"]?.takeIf { it.isNotBlank() }?.toIntOrNull(),
            employeeNumber = body["employeeNumber"]?.takeIf { it.isNotBlank() },
            siteRegularHours = body["siteRegularHours"]?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) },
            displayName = body["displayName"]?.takeIf { it.isNotBlank() } ?: auth.name
        )

        return userSettingsService.saveOrUpdate(settings)
    }
}
