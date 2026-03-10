package com.example.demo.util;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Input sanitization helpers.
 * <p>
 * 注意: SQLインジェクション対策はMyBatisの {@code #{}} パラメータバインドで行うこと。
 * アプリ層でのキーワード検出は誤検知が多いため非推奨。
 */
@UtilityClass
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
