package com.example.demo

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AuthController {
    @GetMapping("/login")
    fun login(
        @RequestParam(name = "logout", required = false) logout: Boolean?,
        @RequestParam(name = "error", required = false) error: Boolean?,
        @RequestParam(name = "username", required = false) username: String?,
        @RequestParam(name = "message", required = false) message: String?,
        model: Model
    ): String {
        // 既にログイン済みで、明示的なログアウト/エラー指定がない場合はツールへ
        val auth: Authentication? = SecurityContextHolder.getContext().authentication
        val isAuthenticated = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        if (isAuthenticated && (logout != true) && (error != true)) {
            return "redirect:/tools"
        }

        // 背景はツール画面相当を表示
        model.addAttribute("currentScreen", "ツール")
        model.addAttribute("selectedScreenName", "ツール")
        model.addAttribute("currentScreenPath", "toolsList")

        // ログインモーダル表示用フラグ/メッセージ
        model.addAttribute("showLoginModal", true)
        if (logout == true) {
            model.addAttribute("loginMessage", message ?: "ログアウトしました")
        }
        if (error == true) {
            val errorMessage = message ?: "ユーザー名またはパスワードが正しくありません"
            model.addAttribute("loginError", errorMessage)
            if (!username.isNullOrBlank()) model.addAttribute("loginUsername", username)
        }

        return "main"
    }
}
