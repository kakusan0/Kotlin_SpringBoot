package com.example.demo.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException exception
    ) throws ServletException, IOException {
        String username = request.getParameter("username");
        if (username == null) {
            username = "";
        }
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

        setDefaultFailureUrl("/login?error=true&username=" + encodedUsername);

        super.onAuthenticationFailure(request, response, exception);
    }
}
