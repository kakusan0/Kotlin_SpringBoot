package com.example.demo.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class CustomAuthenticationFailureHandler : SimpleUrlAuthenticationFailureHandler() {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val username = request.getParameter("username") ?: ""
        val encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8.toString())

        setDefaultFailureUrl("/login?error=true&username=$encodedUsername")

        super.onAuthenticationFailure(request, response, exception)
    }
}

