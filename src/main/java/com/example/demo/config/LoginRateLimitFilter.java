package com.example.demo.config;

import com.example.demo.util.IpUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * ログイン試行レート制限フィルター
 * ログインエンドポイントへのリクエスト数を制限してブルートフォース攻撃を防ぐ
 * 設定: 5分間に5回のログイン試行まで許可
 */
@Slf4j
@Component
@Order(2)
public class LoginRateLimitFilter extends OncePerRequestFilter {


    private final boolean trustProxy;
    private final long capacity;
    private final long refillMinutes;

    private final Cache<String, Bucket> cache;

    public LoginRateLimitFilter(
            @Value("${app.trust-proxy:false}") boolean trustProxy,
            @Value("${app.login.rate-limit.capacity:5}") long capacity,
            @Value("${app.login.rate-limit.refill-minutes:5}") long refillMinutes
    ) {
        this.trustProxy = trustProxy;
        this.capacity = capacity;
        this.refillMinutes = refillMinutes;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Math.max(refillMinutes * 2, 10), TimeUnit.MINUTES)
                .build();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!"/login".equalsIgnoreCase(request.getRequestURI()) || !"POST".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = IpUtils.clientIp(request, trustProxy);
        Bucket bucket = cache.get(clientIp, key -> createBucket());

        if (bucket.tryConsume(1)) {
            log.debug("ログイン試行許可: IP={}, 残り試行回数={}", clientIp, bucket.getAvailableTokens());
            filterChain.doFilter(request, response);
        } else {
            log.warn("ログイン試行レート制限超過: IP={}", clientIp);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/html; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            String message = "ログイン試行回数が上限に達しました。" + refillMinutes + "分後に再度お試しください。";
            response.sendRedirect(
                    "/login?error=true&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8)
            );
        }
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(refillMinutes)))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("/login".equalsIgnoreCase(request.getRequestURI()) && "POST".equals(request.getMethod()));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
