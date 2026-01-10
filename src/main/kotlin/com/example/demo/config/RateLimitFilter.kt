package com.example.demo.config

import com.example.demo.util.IpUtils
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * レート制限フィルター
 * APIエンドポイントへのリクエスト数を制限してDDoS攻撃やブルートフォース攻撃を防ぐ
 * 設定: 1分間に300リクエスト（平均5リクエスト/秒）
 */
@Component
@Order(1)
class RateLimitFilter(
    @param:Value("\${app.trust-proxy:false}") private val trustProxy: Boolean
) : OncePerRequestFilter() {

    private val cache: MutableMap<String, Bucket> = ConcurrentHashMap()

    companion object {
        private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)
        private const val CAPACITY = 300L
        private val REFILL_DURATION = Duration.ofMinutes(1)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = IpUtils.clientIp(request, trustProxy)
        val bucket = cache.computeIfAbsent(clientIp) { createBucket() }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
        } else {
            log.warn("レート制限超過: IP=$clientIp, URI=${request.requestURI}")
            response.apply {
                status = HttpStatus.TOO_MANY_REQUESTS.value()
                contentType = "application/json"
                characterEncoding = "UTF-8"
                writer.write(
                    """{"error":"Too Many Requests","message":"リクエスト数が多すぎます。しばらくしてから再度お試しください。"}"""
                )
            }
        }
    }

    private fun createBucket(): Bucket =
        Bucket.builder()
            .addLimit { limit ->
                limit.capacity(CAPACITY).refillGreedy(CAPACITY, REFILL_DURATION)
            }
            .build()

    override fun shouldNotFilterAsyncDispatch(): Boolean = false
    override fun shouldNotFilterErrorDispatch(): Boolean = false
}
