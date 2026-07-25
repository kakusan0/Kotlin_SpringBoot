package com.example.demo.controller;

import com.example.demo.dto.AutoBlacklistResult;
import com.example.demo.dto.IpBlacklistRequest;
import com.example.demo.mapper.BlacklistIpMapper;
import com.example.demo.mapper.WhitelistIpMapper;
import com.example.demo.service.BlacklistEventService;
import com.example.demo.service.IpBlacklistService;
import com.example.demo.util.BlacklistEventFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * IP管理API（ホワイトリスト/ブラックリスト）
 * ADMINロールのみアクセス可能
 */
@RestController
@RequestMapping("/api/ip")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@RequiredArgsConstructor
public class ApiIpController {

    private final WhitelistIpMapper whitelistIpMapper;
    private final BlacklistIpMapper blacklistIpMapper;
    private final BlacklistEventService blacklistEventService;
    private final IpBlacklistService ipBlacklistService;

    @GetMapping("/whitelist")
    public ResponseEntity<Object> listWhitelist() {
        return ResponseEntity.ok(whitelistIpMapper.getActive());
    }

    @GetMapping("/blacklist")
    public ResponseEntity<Object> listBlacklist() {
        return ResponseEntity.ok(blacklistIpMapper.getAll());
    }

    @PostMapping("/blacklist")
    public ResponseEntity<Void> addToBlacklist(@Valid @RequestBody IpBlacklistRequest req, HttpServletRequest httpReq) {
        blacklistIpMapper.upsertIncrementTimes(req.ipAddress());
        whitelistIpMapper.markBlacklistedAndIncrement(req.ipAddress());

        try {
            Object attr = httpReq.getAttribute("requestId");
            String requestId = attr instanceof String ? (String) attr : UUID.randomUUID().toString();
            String path = httpReq.getRequestURI()
                    + (httpReq.getQueryString() != null ? "?" + httpReq.getQueryString() : "");
            blacklistEventService.recordEvent(
                    BlacklistEventFactory.create(
                            req.ipAddress(),
                            "MANUAL",
                            "API",
                            requestId,
                            httpReq.getMethod(),
                            path,
                            HttpStatus.CREATED.value(),
                            httpReq.getHeader("User-Agent"),
                            httpReq.getHeader("Referer"),
                            null));
        } catch (Exception ignored) {
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<Void> deleteFromBlacklist(@PathVariable Long id) {
        blacklistIpMapper.markDeletedById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auto-blacklist-ua-missing")
    public ResponseEntity<AutoBlacklistResult> autoBlacklistUaMissing() {
        return ResponseEntity.ok(ipBlacklistService.autoBlacklistMissingUserAgents());
    }

}
