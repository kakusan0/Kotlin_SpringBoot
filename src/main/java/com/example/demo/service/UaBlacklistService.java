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

    private final UaBlacklistRuleMapper ruleMapper;
    private volatile CacheSnapshot snapshot = new CacheSnapshot(List.of(), 0L);


    private void ensureLoaded() {
        long now = Instant.now().toEpochMilli();
        CacheSnapshot current = snapshot;
        if (now - current.loadedAtMs() > TTL_MS) {
            List<UaBlacklistRule> rules = ruleMapper.selectActive();
            List<CachedRule> cached = new ArrayList<>(rules.size());
            for (UaBlacklistRule rule : rules) {
                if (rule == null || rule.getPattern() == null || rule.getPattern().isBlank()) {
                    continue;
                }
                String matchType = rule.getMatchType() != null
                        ? rule.getMatchType().toUpperCase(Locale.ROOT)
                        : "EXACT";
                Pattern regex = null;
                if ("REGEX".equals(matchType)) {
                    try {
                        regex = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE);
                    } catch (Exception ignored) {
                        continue;
                    }
                }
                cached.add(new CachedRule(matchType, rule.getPattern(), regex));
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
        for (CachedRule r : snapshot.rules()) {
            switch (r.matchType()) {
                case "EXACT" -> {
                    if (userAgent.equalsIgnoreCase(r.pattern())) {
                        return true;
                    }
                }
                case "PREFIX" -> {
                    if (userAgent.regionMatches(true, 0, r.pattern(), 0, r.pattern().length())) {
                        return true;
                    }
                }
                case "REGEX" -> {
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

    private record CachedRule(String matchType, String pattern, Pattern regex) {
    }
}
