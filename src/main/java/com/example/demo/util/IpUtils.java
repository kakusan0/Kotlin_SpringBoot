package com.example.demo.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IpUtils {

    public String clientIp(HttpServletRequest request, boolean trustProxy) {
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
