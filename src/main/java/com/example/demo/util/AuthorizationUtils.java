package com.example.demo.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;

public final class AuthorizationUtils {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private AuthorizationUtils() {
    }

    public static boolean canAccessUser(Principal principal, String targetUsername) {
        return principal != null
                && (targetUsername.equals(principal.getName()) || hasAdminRole());
    }

    private static boolean hasAdminRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_ROLE.equals(authority.getAuthority()));
    }
}
