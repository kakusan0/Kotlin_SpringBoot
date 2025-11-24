package com.example.demo.config

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * If a DuplicateKeyException occurs during request processing (e.g. concurrent session attribute insert),
 * redirect user to the login page immediately instead of showing a 500 error.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class DuplicateSessionExceptionFilter : Filter {
    override fun init(filterConfig: FilterConfig?) {}

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        try {
            chain.doFilter(request, response)
        } catch (ex: Exception) {
            if (isDuplicateKeyException(ex)) {
                val req = request as? HttpServletRequest
                val res = response as? HttpServletResponse
                try {
                    val path = req?.contextPath ?: ""
                    res?.sendRedirect(path + "/login")
                    return
                } catch (_: Exception) {
                    // ignore
                }
            }
            // Re-throw if not handled
            if (ex is ServletException) throw ex
            if (ex is IOException) throw ex
            throw ServletException(ex)
        }
    }

    override fun destroy() {}

    private fun isDuplicateKeyException(ex: Throwable?): Boolean {
        var cur: Throwable? = ex
        while (cur != null) {
            if (cur is DuplicateKeyException) return true
            cur = cur.cause
        }
        return false
    }
}
