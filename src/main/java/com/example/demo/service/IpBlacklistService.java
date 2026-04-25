package com.example.demo.service;

import com.example.demo.dto.AutoBlacklistResult;
import com.example.demo.mapper.AccessLogMapper;
import com.example.demo.mapper.BlacklistIpMapper;
import com.example.demo.mapper.WhitelistIpMapper;
import com.example.demo.model.BlacklistEvent;
import com.example.demo.util.BlacklistEventFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IpBlacklistService {

    private final AccessLogMapper accessLogMapper;
    private final BlacklistIpMapper blacklistIpMapper;
    private final WhitelistIpMapper whitelistIpMapper;
    private final BlacklistEventService blacklistEventService;

    @Transactional
    public AutoBlacklistResult autoBlacklistMissingUserAgents() {
        List<String> candidates = accessLogMapper.selectIpsWithMissingUserAgent().stream()
                .filter(ip -> ip != null && !ip.isBlank())
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return new AutoBlacklistResult(0, 0);
        }

        blacklistIpMapper.upsertIncrementTimesBulk(candidates);
        whitelistIpMapper.markBlacklistedAndIncrementBulk(candidates);

        List<BlacklistEvent> events = candidates.stream()
                .map(ip -> BlacklistEventFactory.create(
                        ip,
                        "UA_MISSING",
                        "AUTO",
                        UUID.randomUUID().toString(),
                        "AUTO",
                        "/api/ip/auto-blacklist-ua-missing",
                        HttpStatus.CREATED.value(),
                        null,
                        null,
                        null))
                .toList();
        blacklistEventService.recordEventsSync(events);

        return new AutoBlacklistResult(candidates.size(), candidates.size());
    }
}