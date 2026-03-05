package com.example.demo.controller;

import com.example.demo.mapper.AccessLogMapper;
import com.example.demo.mapper.BlacklistIpMapper;
import com.example.demo.mapper.UaBlacklistRuleMapper;
import com.example.demo.mapper.WhitelistIpMapper;
import com.example.demo.model.UaBlacklistRule;
import com.example.demo.service.BlacklistEventService;
import com.example.demo.service.UaBlacklistService;
import com.example.demo.util.BlacklistEventFactory;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User-Agentブラックリスト管理API
 * ADMINロールのみアクセス可能
 */
@RestController
@RequestMapping("/api/ua-blacklist")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ApiUaBlacklistController {

    private final UaBlacklistRuleMapper ruleMapper;
    private final AccessLogMapper accessLogMapper;
    private final BlacklistIpMapper blacklistIpMapper;
    private final WhitelistIpMapper whitelistIpMapper;
    private final BlacklistEventService blacklistEventService;
    private final UaBlacklistService uaBlacklistService;


    @GetMapping
    public ResponseEntity<Object> list() {
        return ResponseEntity.ok(ruleMapper.selectActive());
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody CreateRuleRequest req) {
        String mt = req.getMatchType() != null ? req.getMatchType().toUpperCase() : "EXACT";
        Set<String> allowed = new HashSet<>(List.of("EXACT", "PREFIX", "REGEX"));
        if (!allowed.contains(mt)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("message", "matchType must be EXACT|PREFIX|REGEX"));
        }

        UaBlacklistRule rule = new UaBlacklistRule();
        rule.setPattern(req.getPattern());
        rule.setMatchType(mt);
        rule.setDeleted(false);
        ruleMapper.insert(rule);
        uaBlacklistService.evictCache();

        int blockedCount = 0;
        try {
            List<String> matchingIps = accessLogMapper.selectIpsByUserAgentPattern(req.getPattern(), mt);
            if (!matchingIps.isEmpty()) {
                blacklistIpMapper.upsertIncrementTimesBulk(matchingIps);
                whitelistIpMapper.markBlacklistedAndIncrementBulk(matchingIps);
                blockedCount = matchingIps.size();

                for (String ip : matchingIps) {
                    try {
                        blacklistEventService.recordEvent(
                                BlacklistEventFactory.create(
                                        ip,
                                        "UA_RULE_" + mt,
                                        "AUTO",
                                        UUID.randomUUID().toString(),
                                        "AUTO",
                                        "/api/ua-blacklist",
                                        HttpStatus.CREATED.value(),
                                        null,
                                        null,
                                        null
                                )
                        );
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }

        //noinspection SpringUntrustedDataFlow
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateRuleResponse(rule.getId(), blockedCount));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleMapper.logicalDelete(id);
        uaBlacklistService.evictCache();
        return ResponseEntity.noContent().build();
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class CreateRuleRequest {
        @NotBlank
        private String pattern;
        @NotBlank
        private String matchType = "EXACT";
    }

    public record CreateRuleResponse(Long ruleId, int blockedIpsCount) {
    }
}
