package com.example.demo.util

import org.springframework.stereotype.Component

/**
 * 入力サニタイゼーションユーティリティ
 * XSS攻撃やSQLインジェクション攻撃を防ぐための入力検証とサニタイゼーション
 */
@Component
class SecurityUtils {

    companion object {
        // 危険な文字パターン
        private val XSS_PATTERNS = listOf(
            Regex("<script", RegexOption.IGNORE_CASE),
            Regex("javascript:", RegexOption.IGNORE_CASE),
            Regex("onerror=", RegexOption.IGNORE_CASE),
            Regex("onload=", RegexOption.IGNORE_CASE),
            Regex("<iframe", RegexOption.IGNORE_CASE),
            Regex("<object", RegexOption.IGNORE_CASE),
            Regex("<embed", RegexOption.IGNORE_CASE)
        )

        private val SQL_INJECTION_PATTERNS = listOf(
            Regex("('|(\\-\\-)|(;)|(\\|\\|)|(\\*))", RegexOption.IGNORE_CASE),
            Regex("\\b(union|select|insert|update|delete|drop|create|alter|exec|execute)\\b", RegexOption.IGNORE_CASE)
        )

        private val PATH_TRAVERSAL_PATTERNS = listOf(
            Regex("\\.\\./"),
            Regex("\\.\\.\\\\"),
            Regex("%2e%2e/", RegexOption.IGNORE_CASE),
            Regex("%2e%2e\\\\", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * XSS攻撃の可能性がある文字列を検出
     */
    fun containsXSSPattern(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return XSS_PATTERNS.any { it.containsMatchIn(input) }
    }

    /**
     * SQLインジェクション攻撃の可能性がある文字列を検出
     */
    fun containsSQLInjectionPattern(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return SQL_INJECTION_PATTERNS.any { it.containsMatchIn(input) }
    }

    /**
     * パストラバーサル攻撃の可能性がある文字列を検出
     */
    fun containsPathTraversalPattern(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return PATH_TRAVERSAL_PATTERNS.any { it.containsMatchIn(input) }
    }

    /**
     * HTMLエンティティをエスケープ
     */
    fun escapeHtml(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
    }

    /**
     * 安全な文字列かどうかを検証
     */
    fun isSafeInput(input: String?): Boolean {
        if (input.isNullOrBlank()) return true
        return !containsXSSPattern(input) &&
                !containsSQLInjectionPattern(input) &&
                !containsPathTraversalPattern(input)
    }

    /**
     * 英数字とアンダースコア、ハイフンのみを許可
     */
    fun isAlphanumericSafe(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return input.matches(Regex("^[A-Za-z0-9_-]+$"))
    }

    /**
     * 数値のみを許可
     */
    fun isNumericOnly(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return input.matches(Regex("^[0-9]+$"))
    }
}

