package com.example.demo.config

import com.example.demo.mapper.AccessLogMapper
import com.example.demo.model.AccessLog
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.util.UUID

/**
 * 全HTTPアクセスをDBに記録するフィルター
 */
@Component
@Order(2)
class SecurityAuditFilter(
    private val accessLogMapper: AccessLogMapper,
    private val whitelistIpMapper: com.example.demo.mapper.WhitelistIpMapper,
    private val blacklistIpMapper: com.example.demo.mapper.BlacklistIpMapper,
    private val geoIpCountryService: com.example.demo.service.GeoIpCountryService
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
        val remoteIp = getClientIp(request) ?: ""

        // 海外IP（許可国以外）は即時ブロック + ブラックリストへupsert（回数+1）
        if (remoteIp.isNotBlank() && geoIpCountryService.isEnabled() && !geoIpCountryService.isAllowedCountry(remoteIp)) {
            try {
                blacklistIpMapper.upsertIncrementTimes(remoteIp)
                whitelistIpMapper.markBlacklistedAndIncrement(remoteIp)
            } catch (_: Exception) {}
            response.status = 404
            response.writer.write("")
            return
        }

        // ブラックリスト判定
        if (remoteIp.isNotBlank() && blacklistIpMapper.existsByIp(remoteIp)) {
            // ブロック回数をインクリメント + ホワイト側フラグ
            try {
                blacklistIpMapper.upsertIncrementTimes(remoteIp)
                whitelistIpMapper.markBlacklistedAndIncrement(remoteIp)
            } catch (_: Exception) {}
            response.status = 404
            response.writer.write("")
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
            try { resWrap.copyBodyToResponse() } catch (_: Exception) {}

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
                log.warn("アクセスログ保存に失敗: method={}, path={}, status={}, err={}", request.method, request.requestURI, status, e.toString())
            }
        }
    }

    private fun getClientIp(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?: request.remoteAddr

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
