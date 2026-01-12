package com.example.demo.controller

import com.example.demo.service.AipoLoginResult
import com.example.demo.service.AipoLoginService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class AipoLoginRequest(
    val username: String,
    val password: String,
    val yearMonth: String? = null,  // "YYYY-MM"形式 (例: "2024-01")
    val autoSubmit: Boolean = false
)

data class AipoSubmitRequest(
    val submitButtonId: String
)

@RestController
@RequestMapping("/api/aipo")
class AipoLoginController(
    private val aipoLoginService: AipoLoginService
) {

    /**
     * Aipoにログイン
     */
    @PostMapping("/login")
    fun login(
        auth: Authentication,
        @RequestBody request: AipoLoginRequest
    ): ResponseEntity<AipoLoginResult> {
        val result = aipoLoginService.login(
            username = auth.name,
            aipoUsername = request.username,
            aipoPassword = request.password,
            yearMonth = request.yearMonth,
            autoSubmit = request.autoSubmit
        )

        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.badRequest().body(result)
        }
    }

    /**
     * Aipoからログアウト
     */
    @PostMapping("/logout")
    fun logout(auth: Authentication): ResponseEntity<Map<String, Any>> {
        val success = aipoLoginService.logout(auth.name)
        return ResponseEntity.ok(
            mapOf(
                "success" to success,
                "message" to if (success) "ログアウトしました" else "セッションが見つかりません"
            )
        )
    }

    /**
     * Aipoログイン状態を確認
     */
    @GetMapping("/status")
    fun status(auth: Authentication): ResponseEntity<Map<String, Any>> {
        val loggedIn = aipoLoginService.isLoggedIn(auth.name)
        return ResponseEntity.ok(
            mapOf(
                "loggedIn" to loggedIn
            )
        )
    }

    /**
     * Aipoで申請を実行
     */
    @PostMapping("/submit")
    fun submit(
        auth: Authentication,
        @RequestBody request: AipoSubmitRequest
    ): ResponseEntity<Map<String, Any>> {
        val result = aipoLoginService.submitRequest(auth.name, request.submitButtonId)
        return if (result.first) {
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to result.second
                )
            )
        } else {
            ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "message" to result.second
                )
            )
        }
    }
}

