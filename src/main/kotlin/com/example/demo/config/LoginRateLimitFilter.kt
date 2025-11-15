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
 * ログイン試行レート制限フィルター
 * ログインエンドポイントへのリクエスト数を制限してブルートフォース攻撃を防ぐ
 * 設定: 5分間に5回のログイン試行まで許可
 */
@Component
@Order(2)
class LoginRateLimitFilter(
    @Value("\${app.trust-proxy:false}") private val trustProxy: Boolean,
    @Value("\${app.login.rate-limit.capacity:5}") private val capacity: Long,
    @Value("\${app.login.rate-limit.refill-minutes:5}") private val refillMinutes: Long
) : OncePerRequestFilter() {

    private val cache: MutableMap<String, Bucket> = ConcurrentHashMap()

    companion object {
        private val log = LoggerFactory.getLogger(LoginRateLimitFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // ログインエンドポイント以外はスキップ
        if (!request.requestURI.equals("/login", ignoreCase = true) || request.method != "POST") {
            filterChain.doFilter(request, response)
            return
        }

        val clientIp = IpUtils.clientIp(request, trustProxy)
        val bucket = cache.computeIfAbsent(clientIp) { createBucket() }

        if (bucket.tryConsume(1)) {
            log.debug("ログイン試行許可: IP=$clientIp, 残り試行回数=${bucket.availableTokens}")
            filterChain.doFilter(request, response)
        } else {
            log.warn("ログイン試行レート制限超過: IP=$clientIp")

            response.apply {
                status = HttpStatus.TOO_MANY_REQUESTS.value()
                contentType = "text/html; charset=UTF-8"
                characterEncoding = "UTF-8"

                // ログイン画面にリダイレクトしてエラーメッセージを表示
                sendRedirect(
                    "/login?error=true&message=" +
                            java.net.URLEncoder.encode(
                                "ログイン試行回数が上限に達しました。${refillMinutes}分後に再度お試しください。",
                                "UTF-8"
                            )
                )
            }
        }
    }

    private fun createBucket(): Bucket =
        Bucket.builder()
            .addLimit { limit ->
                limit.capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(refillMinutes))
            }
            .build()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // POSTメソッドの/loginのみフィルタリング
        return !(request.requestURI.equals("/login", ignoreCase = true) && request.method == "POST")
    }

    override fun shouldNotFilterAsyncDispatch(): Boolean = false
    override fun shouldNotFilterErrorDispatch(): Boolean = false
}

