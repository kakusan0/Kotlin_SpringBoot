package com.example.demo.service

import com.example.demo.mapper.UaBlacklistRuleMapper
import com.example.demo.model.UaBlacklistRule
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Service
class UaBlacklistService(
    private val ruleMapper: UaBlacklistRuleMapper
) {
    private val cacheRef = AtomicReference<List<UaBlacklistRule>>(emptyList())
    private val lastLoadEpochMs = AtomicLong(0)

    // キャッシュTTL（ms）
    private val ttlMs = 60_000L

    private fun ensureLoaded() {
        val now = Instant.now().toEpochMilli()
        if (now - lastLoadEpochMs.get() > ttlMs) {
            val rules = ruleMapper.selectActive()
            cacheRef.set(rules)
            lastLoadEpochMs.set(now)
        }
    }

    fun matches(userAgent: String?): Boolean {
        if (userAgent.isNullOrBlank()) return false
        ensureLoaded()
        val ua = userAgent
        for (r in cacheRef.get()) {
            when (r.matchType.uppercase()) {
                "EXACT" -> if (ua.equals(r.pattern, ignoreCase = true)) return true
                "PREFIX" -> if (ua.startsWith(r.pattern, ignoreCase = true)) return true
                "REGEX" -> {
                    try {
                        if (Regex(r.pattern, RegexOption.IGNORE_CASE).containsMatchIn(ua)) return true
                    } catch (_: Exception) {}
                }
            }
        }
        return false
    }
}

