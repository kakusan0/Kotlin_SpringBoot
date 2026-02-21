package com.example.demo.config;

import com.example.demo.mapper.AccessLogMapper;
import com.example.demo.mapper.BlacklistIpMapper;
import com.example.demo.mapper.WhitelistIpMapper;
import com.example.demo.model.AccessLog;
import com.example.demo.model.WhitelistIp;
import com.example.demo.service.BlacklistEventService;
import com.example.demo.service.GeoIpCountryService;
import com.example.demo.service.UaBlacklistService;
import com.example.demo.util.BlacklistEventFactory;
import com.example.demo.util.IpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * 全HTTPアクセスをDBに記録するフィルター
 */
@Component
@Order(2)
public class SecurityAuditFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SecurityAuditFilter.class);

    private final AccessLogMapper accessLogMapper;
    private final WhitelistIpMapper whitelistIpMapper;
    private final BlacklistIpMapper blacklistIpMapper;
    private final GeoIpCountryService geoIpCountryService;
    private final BlacklistEventService blacklistEventService;
    private final UaBlacklistService uaBlacklistService;

    private final boolean trustProxy;

    public SecurityAuditFilter(
            AccessLogMapper accessLogMapper,
            WhitelistIpMapper whitelistIpMapper,
            BlacklistIpMapper blacklistIpMapper,
            GeoIpCountryService geoIpCountryService,
            BlacklistEventService blacklistEventService,
            UaBlacklistService uaBlacklistService,
            @Value("${app.trust-proxy:false}") boolean trustProxy
    ) {
        this.accessLogMapper = accessLogMapper;
        this.whitelistIpMapper = whitelistIpMapper;
        this.blacklistIpMapper = blacklistIpMapper;
        this.geoIpCountryService = geoIpCountryService;
        this.blacklistEventService = blacklistEventService;
        this.uaBlacklistService = uaBlacklistService;
        this.trustProxy = trustProxy;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        request.setAttribute("requestId", requestId);
        String remoteIp = IpUtils.clientIp(request, trustProxy);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            userAgent = "";
        }

        class EarlyLog {
            void write(int statusCode, String reason) {
                long duration = System.currentTimeMillis() - start;
                AccessLog accessLog = new AccessLog();
                accessLog.setRequestId(requestId);
                accessLog.setMethod(request.getMethod());
                accessLog.setPath(request.getRequestURI());
                accessLog.setQuery(request.getQueryString());
                accessLog.setStatus(statusCode);
                accessLog.setDurationMs(duration);
                accessLog.setRemoteIp(remoteIp);
                accessLog.setUserAgent(request.getHeader("User-Agent"));
                accessLog.setReferer(request.getHeader("Referer"));
                accessLog.setUsername(request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null);
                try {
                    accessLogMapper.insert(accessLog);
                } catch (Exception e) {
                    log.warn(
                            "早期アクセスログ保存に失敗: method={}, path={}, status={}, err={}",
                            request.getMethod(),
                            request.getRequestURI(),
                            statusCode,
                            e.toString()
                    );
                }
                try {
                    blacklistEventService.recordEvent(
                            BlacklistEventFactory.create(
                                    remoteIp,
                                    reason,
                                    "FILTER",
                                    requestId,
                                    request.getMethod(),
                                    request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""),
                                    statusCode,
                                    request.getHeader("User-Agent"),
                                    request.getHeader("Referer"),
                                    null
                            )
                    );
                } catch (Exception e) {
                    log.warn("ブラックリストイベント保存に失敗: ip={}, reason={}, err={}", remoteIp, reason, e.toString());
                }
            }
        }

        EarlyLog earlyLog = new EarlyLog();

        if (uaBlacklistService.matches(userAgent)) {
            if (remoteIp != null && !remoteIp.isBlank()) {
                try {
                    blacklistIpMapper.upsertIncrementTimes(remoteIp);
                    whitelistIpMapper.markBlacklistedAndIncrement(remoteIp);
                } catch (Exception ignored) {
                }
            }
            response.setStatus(404);
            try {
                response.getWriter().write("");
            } catch (Exception ignored) {
            }
            earlyLog.write(404, "UA");
            return;
        }

        if (remoteIp != null && !remoteIp.isBlank() && geoIpCountryService.isEnabled()
                && !geoIpCountryService.isAllowedCountry(remoteIp)) {
            try {
                blacklistIpMapper.upsertIncrementTimes(remoteIp);
                whitelistIpMapper.markBlacklistedAndIncrement(remoteIp);
            } catch (Exception ignored) {
            }
            response.setStatus(404);
            try {
                response.getWriter().write("");
            } catch (Exception ignored) {
            }
            earlyLog.write(404, "COUNTRY");
            return;
        }

        if (remoteIp != null && !remoteIp.isBlank() && blacklistIpMapper.existsByIp(remoteIp)) {
            try {
                blacklistIpMapper.upsertIncrementTimes(remoteIp);
                whitelistIpMapper.markBlacklistedAndIncrement(remoteIp);
            } catch (Exception ignored) {
            }
            response.setStatus(404);
            try {
                response.getWriter().write("");
            } catch (Exception ignored) {
            }
            earlyLog.write(404, "BLACKLIST");
            return;
        }

        if (remoteIp != null && !remoteIp.isBlank() && !whitelistIpMapper.existsByIp(remoteIp)) {
            WhitelistIp whitelistIp = new WhitelistIp();
            whitelistIp.setIpAddress(remoteIp);
            whitelistIpMapper.insert(whitelistIp);
        }

        ContentCachingRequestWrapper reqWrap = new ContentCachingRequestWrapper(request, 10240);
        ContentCachingResponseWrapper resWrap = new ContentCachingResponseWrapper(response);

        int status = 500;
        try {
            filterChain.doFilter(reqWrap, resWrap);
            status = resWrap.getStatus();
        } catch (Exception ex) {
            status = resWrap.getStatus() > 0 ? resWrap.getStatus() : 500;
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                resWrap.copyBodyToResponse();
            } catch (Exception ignored) {
            }

            AccessLog accessLog = new AccessLog();
            accessLog.setRequestId(requestId);
            accessLog.setMethod(request.getMethod());
            accessLog.setPath(request.getRequestURI());
            accessLog.setQuery(request.getQueryString());
            accessLog.setStatus(status);
            accessLog.setDurationMs(duration);
            accessLog.setRemoteIp(remoteIp);
            accessLog.setUserAgent(request.getHeader("User-Agent"));
            accessLog.setReferer(request.getHeader("Referer"));
            accessLog.setUsername(request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null);
            accessLog.setRequestBytes(computeRequestBytes(reqWrap));
            accessLog.setResponseBytes(computeResponseBytes(resWrap));
            try {
                accessLogMapper.insert(accessLog);
            } catch (Exception e) {
                log.warn(
                        "アクセスログ保存に失敗: method={}, path={}, status={}, err={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        e.toString()
                );
            }
        }
    }

    private long computeRequestBytes(ContentCachingRequestWrapper req) {
        String h = req.getHeader("Content-Length");
        if (h != null && !h.isBlank()) {
            try {
                return Long.parseLong(h);
            } catch (NumberFormatException ignored) {
            }
        }
        return req.getContentAsByteArray().length;
    }

    private long computeResponseBytes(ContentCachingResponseWrapper res) {
        String h = res.getHeader("Content-Length");
        if (h != null && !h.isBlank()) {
            try {
                return Long.parseLong(h);
            } catch (NumberFormatException ignored) {
            }
        }
        return res.getContentSize();
    }
}
