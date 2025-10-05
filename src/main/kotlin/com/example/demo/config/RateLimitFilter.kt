package com.example.demo.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * レート制限フィルター
 * APIエンドポイントへのリクエスト数を制限してDDoS攻撃やブルートフォース攻撃を防ぐ
 */
@Component
@Order(1)
class RateLimitFilter : OncePerRequestFilter() {

    // IPアドレスごとにバケットを管理
    private val cache: MutableMap<String, Bucket> = ConcurrentHashMap()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = getClientIp(request)
        val bucket = resolveBucket(clientIp)

        if (bucket.tryConsume(1)) {
            // トークンが利用可能な場合、リクエストを続行
            filterChain.doFilter(request, response)
        } else {
            // レート制限を超えた場合、429エラーを返す
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.writer.write(
                """{"error":"Too Many Requests","message":"リクエスト数が多すぎます。しばらくしてから再度お試しください。"}"""
            )
        }
    }

    /**
     * IPアドレスごとにバケットを取得または作成
     */
    private fun resolveBucket(clientIp: String): Bucket {
        return cache.computeIfAbsent(clientIp) { newBucket() }
    }

    /**
     * 新しいバケットを作成
     * 設定: 1分間に60リクエスト（1秒間に1リクエスト）
     */
    private fun newBucket(): Bucket {
        val bandwidth = Bandwidth.classic(
            60, // 容量
            Refill.intervally(60, Duration.ofMinutes(1)) // 1分間に60トークン補充
        )
        return Bucket.builder()
            .addLimit(bandwidth)
            .build()
    }

    /**
     * クライアントのIPアドレスを取得
     * プロキシ経由の場合も考慮
     */
    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrEmpty()) {
            xForwardedFor.split(",")[0].trim()
        } else {
            request.remoteAddr
        }
    }

    override fun shouldNotFilterAsyncDispatch(): Boolean = false

    override fun shouldNotFilterErrorDispatch(): Boolean = false
}

