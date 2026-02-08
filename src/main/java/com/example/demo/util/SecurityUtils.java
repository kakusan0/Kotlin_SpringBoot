package com.example.demo.util;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Input sanitization helpers.
 */
@Component
public class SecurityUtils {

    private static final List<Pattern> XSS_PATTERNS = List.of(
            Pattern.compile("<script", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onerror=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onload=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
            Pattern.compile("('|(--)|(;)|(\\|\\|)|(\\*))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(union|select|insert|update|delete|drop|create|alter|exec|execute)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = List.of(
            Pattern.compile("\\.\\./"),
            Pattern.compile("\\.\\.\\\\"),
            Pattern.compile("%2e%2e/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%2e%2e\\\\", Pattern.CASE_INSENSITIVE)
    );

    public boolean containsXSSPattern(String input) {
        if (input == null || input.isBlank()) return false;
        return XSS_PATTERNS.stream().anyMatch(p -> p.matcher(input).find());
    }

    public boolean containsSQLInjectionPattern(String input) {
        if (input == null || input.isBlank()) return false;
        return SQL_INJECTION_PATTERNS.stream().anyMatch(p -> p.matcher(input).find());
    }

    public boolean containsPathTraversalPattern(String input) {
        if (input == null || input.isBlank()) return false;
        return PATH_TRAVERSAL_PATTERNS.stream().anyMatch(p -> p.matcher(input).find());
    }

    public String escapeHtml(String input) {
        if (input == null || input.isBlank()) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    public boolean isSafeInput(String input) {
        if (input == null || input.isBlank()) return true;
        return !containsXSSPattern(input) &&
                !containsSQLInjectionPattern(input) &&
                !containsPathTraversalPattern(input);
    }

    public boolean isAlphanumericSafe(String input) {
        if (input == null || input.isBlank()) return false;
        return input.matches("^[A-Za-z0-9_-]+$");
    }

    public boolean isNumericOnly(String input) {
        if (input == null || input.isBlank()) return false;
        return input.matches("^[0-9]+$");
    }
}
