package com.example.demo.config;

import com.example.demo.util.IpUtils;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ログイン試行レート制限フィルター
 * ログインエンドポイントへのリクエスト数を制限してブルートフォース攻撃を防ぐ
 * 設定: 5分間に5回のログイン試行まで許可
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    @Value("${app.trust-proxy:false}")
    private final boolean trustProxy;

    @Value("${app.login.rate-limit.capacity:5}")
    private final long capacity;

    @Value("${app.login.rate-limit.refill-minutes:5}")
    private final long refillMinutes;

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!"/login".equalsIgnoreCase(request.getRequestURI()) || !"POST".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = IpUtils.clientIp(request, trustProxy);
        Bucket bucket = cache.computeIfAbsent(clientIp, key -> createBucket());

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
