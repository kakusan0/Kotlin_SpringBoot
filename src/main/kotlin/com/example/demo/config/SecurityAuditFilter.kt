package com.example.demo.config

import com.example.demo.mapper.AccessLogMapper
import com.example.demo.mapper.BlacklistEventMapper
import com.example.demo.model.AccessLog
import com.example.demo.service.BlacklistEventService
import com.example.demo.util.BlacklistEventFactory
import com.example.demo.util.IpUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.*

/**
 * 全HTTPアクセスをDBに記録するフィルター
 */
@Component
@Order(2)
class SecurityAuditFilter(
    private val accessLogMapper: AccessLogMapper,
    private val whitelistIpMapper: com.example.demo.mapper.WhitelistIpMapper,
    private val blacklistIpMapper: com.example.demo.mapper.BlacklistIpMapper,
    private val geoIpCountryService: com.example.demo.service.GeoIpCountryService,
    private val blacklistEventMapper: BlacklistEventMapper,
    private val blacklistEventService: BlacklistEventService,
    private val uaBlacklistService: com.example.demo.service.UaBlacklistService,
    @param:Value("\${app.trust-proxy:false}") private val trustProxy: Boolean
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(SecurityAuditFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val start = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        request.setAttribute("requestId", requestId)
        val remoteIp = IpUtils.clientIp(request, trustProxy)
        val userAgent = request.getHeader("User-Agent") ?: ""

        fun writeEarlyAccessLogAndEvent(statusCode: Int, reason: String) {
            val duration = System.currentTimeMillis() - start
            // access_logs に即時記録
            val accessLog = AccessLog(
                requestId = requestId,
                method = request.method,
                path = request.requestURI,
                query = request.queryString,
                status = statusCode,
                durationMs = duration,
                remoteIp = remoteIp,
                userAgent = request.getHeader("User-Agent"),
                referer = request.getHeader("Referer"),
                username = request.userPrincipal?.name,
                requestBytes = null,
                responseBytes = 0
            )
            try {
                accessLogMapper.insert(accessLog)
            } catch (e: Exception) {
                log.warn(
                    "早期アクセスログ保存に失敗: method={}, path={}, status={}, err={}",
                    request.method,
                    request.requestURI,
                    statusCode,
                    e.toString()
                )
            }
            // blacklist_events にも記録
            try {
                // 非同期で挿入（失敗はログに記録される）
                blacklistEventService.recordEvent(
                    BlacklistEventFactory.create(
                        ipAddress = remoteIp,
                        reason = reason,
                        source = "FILTER",
                        requestId = requestId,
                        method = request.method,
                        path = request.requestURI + (request.queryString?.let { "?$it" } ?: ""),
                        status = statusCode,
                        userAgent = request.getHeader("User-Agent"),
                        referer = request.getHeader("Referer")
                    )
                )
            } catch (e: Exception) {
                log.warn("ブラックリストイベント保存に失敗: ip={}, reason={}, err={}", remoteIp, reason, e.toString())
            }
        }

        // UAブラックリスト（DBルール）による即時ブロック
        if (uaBlacklistService.matches(userAgent)) {
            if (remoteIp.isNotBlank()) {
                try {
                    blacklistIpMapper.upsertIncrementTimes(remoteIp)
                    whitelistIpMapper.markBlacklistedAndIncrement(remoteIp)
                } catch (_: Exception) {
                }
            }
            response.status = 404
            try {
                response.writer.write("")
            } catch (_: Exception) {
            }
            writeEarlyAccessLogAndEvent(404, "UA")
            return
        }

        // 海外IP（許可国以外）は即時ブロック + ブラックリストへupsert（回数+1）
        if (remoteIp.isNotBlank() && geoIpCountryService.isEnabled() && !geoIpCountryService.isAllowedCountry(remoteIp)) {
            try {
                blacklistIpMapper.upsertIncrementTimes(remoteIp)
                whitelistIpMapper.markBlacklistedAndIncrement(remoteIp)
            } catch (_: Exception) {
            }
            response.status = 404
            try {
                response.writer.write("")
            } catch (_: Exception) {
            }
            writeEarlyAccessLogAndEvent(404, "COUNTRY")
            return
        }

        // ブラックリスト判定
        if (remoteIp.isNotBlank() && blacklistIpMapper.existsByIp(remoteIp)) {
            // ブロック回数をインクリメント + ホワイト側フラグ
            try {
                blacklistIpMapper.upsertIncrementTimes(remoteIp)
                whitelistIpMapper.markBlacklistedAndIncrement(remoteIp)
            } catch (_: Exception) {
            }
            response.status = 404
            try {
                response.writer.write("")
            } catch (_: Exception) {
            }
            writeEarlyAccessLogAndEvent(404, "BLACKLIST")
            return
        }
        // ホワイトリスト未登録なら登録（重複不可）
        if (remoteIp.isNotBlank() && !whitelistIpMapper.existsByIp(remoteIp)) {
            whitelistIpMapper.insert(
                com.example.demo.model.WhitelistIp(ipAddress = remoteIp)
            )
        }

        // ボディサイズ計測のためのラッパー
        val reqWrap = ContentCachingRequestWrapper(request)
        val resWrap = ContentCachingResponseWrapper(response)

        var status = 500
        try {
            filterChain.doFilter(reqWrap, resWrap)
            status = resWrap.status
        } catch (ex: Exception) {
            status = resWrap.status.takeIf { it > 0 } ?: 500
            throw ex
        } finally {
            val duration = System.currentTimeMillis() - start
            // レスポンスボディをクライアントへ返却
            try {
                resWrap.copyBodyToResponse()
            } catch (_: Exception) {
            }

            val accessLog = AccessLog(
                requestId = requestId,
                method = request.method,
                path = request.requestURI,
                query = request.queryString,
                status = status,
                durationMs = duration,
                remoteIp = remoteIp,
                userAgent = request.getHeader("User-Agent"),
                referer = request.getHeader("Referer"),
                username = request.userPrincipal?.name,
                requestBytes = computeRequestBytes(reqWrap),
                responseBytes = computeResponseBytes(resWrap)
            )
            try {
                accessLogMapper.insert(accessLog)
            } catch (e: Exception) {
                log.warn(
                    "アクセスログ保存に失敗: method={}, path={}, status={}, err={}",
                    request.method,
                    request.requestURI,
                    status,
                    e.toString()
                )
            }
        }
    }

    private fun computeRequestBytes(req: ContentCachingRequestWrapper): Long {
        val h = req.getHeader("Content-Length")
        if (!h.isNullOrBlank()) {
            h.toLongOrNull()?.let { return it }
        }
        return req.contentAsByteArray.size.toLong()
    }

    private fun computeResponseBytes(res: ContentCachingResponseWrapper): Long {
        val h = res.getHeader("Content-Length")
        if (!h.isNullOrBlank()) {
            h.toLongOrNull()?.let { return it }
        }
        return res.contentSize.toLong()
    }
}
