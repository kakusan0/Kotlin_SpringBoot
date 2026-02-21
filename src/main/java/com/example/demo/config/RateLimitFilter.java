package com.example.demo.config;

import com.example.demo.util.IpUtils;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レート制限フィルター
 * APIエンドポイントへのリクエスト数を制限してDDoS攻撃やブルートフォース攻撃を防ぐ
 * 設定: 1分間に300リクエスト（平均5リクエスト/秒）
 */
@Slf4j
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long CAPACITY = 300L;
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    private final boolean trustProxy;

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.trust-proxy:false}") boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientIp = IpUtils.clientIp(request, trustProxy);
        Bucket bucket = cache.computeIfAbsent(clientIp, key -> createBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("レート制限超過: IP={}, URI={}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"message\":\"リクエスト数が多すぎます。しばらくしてから再度お試しください。\"}"
            );
        }
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(CAPACITY).refillGreedy(CAPACITY, REFILL_DURATION))
                .build();
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
