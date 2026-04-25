package com.example.demo.service;

import com.example.demo.mapper.UaBlacklistRuleMapper;
import com.example.demo.model.UaBlacklistRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UaBlacklistService {

    private static final long TTL_MS = 60_000L;
    private static final String MATCH_TYPE_EXACT = "EXACT";
    private static final String MATCH_TYPE_PREFIX = "PREFIX";
    private static final String MATCH_TYPE_REGEX = "REGEX";

    private final UaBlacklistRuleMapper ruleMapper;
    private volatile CacheSnapshot snapshot = new CacheSnapshot(List.of(), 0L);

    private void ensureLoaded() {
        long now = Instant.now().toEpochMilli();
        CacheSnapshot current = snapshot;
        if (now - current.loadedAtMs() <= TTL_MS) {
            return;
        }

        synchronized (this) {
            current = snapshot;
            now = Instant.now().toEpochMilli();
            if (now - current.loadedAtMs() <= TTL_MS) {
                return;
            }

            List<UaBlacklistRule> rules = ruleMapper.selectActive();
            List<CachedRule> cached = new ArrayList<>(rules.size());
            for (UaBlacklistRule rule : rules) {
                if (rule == null || rule.getPattern() == null || rule.getPattern().isBlank()) {
                    continue;
                }
                String pattern = rule.getPattern();
                String matchType = rule.getMatchType() != null
                        ? rule.getMatchType().toUpperCase(Locale.ROOT)
                        : MATCH_TYPE_EXACT;
                Pattern regex = null;
                if (MATCH_TYPE_REGEX.equals(matchType)) {
                    try {
                        regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                    } catch (Exception ignored) {
                        continue;
                    }
                }
                cached.add(new CachedRule(matchType, pattern, pattern.toLowerCase(Locale.ROOT), regex));
            }
            snapshot = new CacheSnapshot(cached, now);
        }
    }

    public void evictCache() {
        snapshot = new CacheSnapshot(List.of(), 0L);
    }

    public boolean matches(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        ensureLoaded();
        CacheSnapshot current = snapshot;
        String userAgentLower = null;
        for (CachedRule r : current.rules()) {
            switch (r.matchType()) {
                case MATCH_TYPE_EXACT -> {
                    if (userAgentLower == null) {
                        userAgentLower = userAgent.toLowerCase(Locale.ROOT);
                    }
                    if (userAgentLower.equals(r.normalizedPattern())) {
                        return true;
                    }
                }
                case MATCH_TYPE_PREFIX -> {
                    if (userAgentLower == null) {
                        userAgentLower = userAgent.toLowerCase(Locale.ROOT);
                    }
                    if (userAgentLower.startsWith(r.normalizedPattern())) {
                        return true;
                    }
                }
                case MATCH_TYPE_REGEX -> {
                    if (r.regex() != null && r.regex().matcher(userAgent).find()) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private record CacheSnapshot(List<CachedRule> rules, long loadedAtMs) {
    }

    private record CachedRule(String matchType, String pattern, String normalizedPattern, Pattern regex) {
    }
}
