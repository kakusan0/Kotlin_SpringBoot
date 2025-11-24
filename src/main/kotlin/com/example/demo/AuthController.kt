package com.example.demo

import com.example.demo.constants.ApplicationConstants
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
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
        // 既にログイン済みで、明示的なログアウト/エラー指定がない場合はホームへ
        val auth: Authentication? = SecurityContextHolder.getContext().authentication
        val isAuthenticated = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        if (isAuthenticated && (logout != true) && (error != true)) {
            return "${ApplicationConstants.REDIRECT}${ApplicationConstants.HOME}"
        }
        if (logout == true) {
            model.addAttribute("message", "ログアウトしました")
        }
        if (error == true) {
            // カスタムメッセージがある場合はそれを使用、なければデフォルトメッセージ
            val errorMessage = message ?: "ユーザー名またはパスワードが正しくありません"
            model.addAttribute("error", errorMessage)
            // ログイン失敗時にユーザー名を保持
            if (!username.isNullOrBlank()) {
                model.addAttribute("username", username)
            }
        }
        // 特別なメッセージキー: sessionExpired を解釈して分かりやすい説明を表示
        if (message == "sessionExpired") {
            model.addAttribute(
                "error",
                "セッションの有効期限が切れました（同一ユーザで別の端末からログインされた可能性があります）。再度ログインしてください。"
            )
        }
        return "login"
    }
}
