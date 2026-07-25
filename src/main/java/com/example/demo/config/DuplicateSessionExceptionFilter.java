package com.example.demo.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * If a DuplicateKeyException occurs during request processing (e.g. concurrent session attribute insert),
 * redirect user to the login page immediately instead of showing a 500 error.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DuplicateSessionExceptionFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (Exception ex) {
            if (isDuplicateKeyException(ex)) {
                HttpServletRequest req = (request instanceof HttpServletRequest r) ? r : null;
                HttpServletResponse res = (response instanceof HttpServletResponse r) ? r : null;
                try {
                    String path = req != null ? req.getContextPath() : "";
                    if (res != null) {
                        res.sendRedirect(path + "/login");
                        return;
                    }
                } catch (Exception _) {
                }
            }
            if (ex instanceof ServletException se) {
                throw se;
            }
            if (ex instanceof IOException ioe) {
                throw ioe;
            }
            throw new ServletException(ex);
        }
    }

    @Override
    public void destroy() {
    }

    private boolean isDuplicateKeyException(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof DuplicateKeyException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
