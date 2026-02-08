package com.example.demo.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(
            @RequestParam(name = "logout", required = false) Boolean logout,
            @RequestParam(name = "error", required = false) Boolean error,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "message", required = false) String message,
            Model model
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        if (isAuthenticated && (logout == null || !logout) && (error == null || !error)) {
            return "redirect:/tools";
        }

        model.addAttribute("currentScreen", "ツール");
        model.addAttribute("selectedScreenName", "ツール");
        model.addAttribute("currentScreenPath", "toolsList");

        model.addAttribute("showLoginModal", true);
        if (Boolean.TRUE.equals(logout)) {
            model.addAttribute("loginMessage", message != null ? message : "ログアウトしました");
        }
        if (Boolean.TRUE.equals(error)) {
            String errorMessage = message != null ? message : "ユーザー名またはパスワードが正しくありません";
            model.addAttribute("loginError", errorMessage);
            if (username != null && !username.isBlank()) {
                model.addAttribute("loginUsername", username);
            }
        }

        return "main";
    }
}
