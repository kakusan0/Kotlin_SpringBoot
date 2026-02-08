package com.example.demo.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {
    private IpUtils() {
    }

    public static String clientIp(HttpServletRequest request, boolean trustProxy) {
        if (trustProxy) {
            String fwd = request.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) {
                String first = fwd.split(",")[0].trim();
                if (!first.isBlank()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
