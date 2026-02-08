package com.example.demo.service;

import com.example.demo.mapper.UaBlacklistRuleMapper;
import com.example.demo.model.UaBlacklistRule;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class UaBlacklistService {

    private final UaBlacklistRuleMapper ruleMapper;
    private final AtomicReference<List<UaBlacklistRule>> cacheRef = new AtomicReference<>(List.of());
    private final AtomicLong lastLoadEpochMs = new AtomicLong(0);
    private final long ttlMs = 60_000L;

    public UaBlacklistService(UaBlacklistRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    private void ensureLoaded() {
        long now = Instant.now().toEpochMilli();
        if (now - lastLoadEpochMs.get() > ttlMs) {
            List<UaBlacklistRule> rules = ruleMapper.selectActive();
            cacheRef.set(rules);
            lastLoadEpochMs.set(now);
        }
    }

    public boolean matches(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        ensureLoaded();
        String ua = userAgent;
        for (UaBlacklistRule r : cacheRef.get()) {
            String matchType = r.getMatchType() != null ? r.getMatchType().toUpperCase() : "EXACT";
            switch (matchType) {
                case "EXACT" -> {
                    if (ua.equalsIgnoreCase(r.getPattern())) {
                        return true;
                    }
                }
                case "PREFIX" -> {
                    if (ua.regionMatches(true, 0, r.getPattern(), 0, r.getPattern().length())) {
                        return true;
                    }
                }
                case "REGEX" -> {
                    try {
                        if (Pattern.compile(r.getPattern(), Pattern.CASE_INSENSITIVE).matcher(ua).find()) {
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }
}
