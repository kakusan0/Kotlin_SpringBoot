package com.example.demo.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationSuccessHandler : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val roles = AuthorityUtils.authorityListToSet(authentication.authorities)
        // ADMIN を最優先
        val target = when {
            roles.contains("ROLE_ADMIN") -> "/home"
            roles.contains("ROLE_MENU") -> "/manage"
            else -> "/login?error"
        }
        response.sendRedirect(target)
    }
}

