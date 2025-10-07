package com.example.demo.util

import jakarta.servlet.http.HttpServletRequest

object IpUtils {
    fun clientIp(request: HttpServletRequest, trustProxy: Boolean): String {
        if (trustProxy) {
            val fwd = request.getHeader("X-Forwarded-For")
            if (!fwd.isNullOrBlank()) {
                val first = fwd.split(',').firstOrNull()?.trim()
                if (!first.isNullOrBlank()) return first
            }
        }
        return request.remoteAddr
    }
}

