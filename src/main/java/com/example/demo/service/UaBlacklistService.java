package com.example.demo.service;

import com.example.demo.mapper.UaBlacklistRuleMapper;
import com.example.demo.model.UaBlacklistRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UaBlacklistService {

    private static final long TTL_MS = 60_000L;

    private final UaBlacklistRuleMapper ruleMapper;
    private final AtomicReference<List<UaBlacklistRule>> cacheRef = new AtomicReference<>(List.of());
    private final AtomicLong lastLoadEpochMs = new AtomicLong(0);


    private void ensureLoaded() {
        long now = Instant.now().toEpochMilli();
        if (now - lastLoadEpochMs.get() > TTL_MS) {
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
        for (UaBlacklistRule r : cacheRef.get()) {
            String matchType = r.getMatchType() != null ? r.getMatchType().toUpperCase() : "EXACT";
            switch (matchType) {
                case "EXACT" -> {
                    if (userAgent.equalsIgnoreCase(r.getPattern())) {
                        return true;
                    }
                }
                case "PREFIX" -> {
                    if (userAgent.regionMatches(true, 0, r.getPattern(), 0, r.getPattern().length())) {
                        return true;
                    }
                }
                case "REGEX" -> {
                    try {
                        if (Pattern.compile(r.getPattern(), Pattern.CASE_INSENSITIVE).matcher(userAgent).find()) {
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
