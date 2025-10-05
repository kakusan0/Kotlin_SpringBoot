package com.example.demo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig {

    companion object {
        private const val HSTS_MAX_AGE = 31536000L // 1年
        private const val CORS_MAX_AGE = 3600L // 1時間
        private val ALLOWED_ORIGINS = listOf("https://localhost:8443")
        private val ALLOWED_METHODS = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")

        private const val CSP_POLICY =
            "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                    "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                    "img-src 'self' data: https:; " +
                    "font-src 'self' data: https://cdn.jsdelivr.net; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self'"
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // CSRF保護を有効化（CookieベースのCSRFトークン）
            .csrf { it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) }

            // CORS設定
            .cors { it.configurationSource(corsConfigurationSource()) }

            // セキュリティヘッダーの設定
            .headers { headers ->
                headers
                    // X-Frame-Options: DENY（クリックジャッキング対策）
                    .frameOptions { it.deny() }
                    // X-Content-Type-Options: nosniff
                    .contentTypeOptions { }
                    // X-XSS-Protection: 1; mode=block
                    .xssProtection { it.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK) }
                    // Strict-Transport-Security（HTTPS使用時に有効化）
                    .httpStrictTransportSecurity { it.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE) }
                    // Referrer-Policy: 外部サイトへのリファラー情報を制限
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                    // Content-Security-Policy: XSS攻撃対策
                    .contentSecurityPolicy { it.policyDirectives(CSP_POLICY) }
            }

            // 認証設定（現時点では全アクセスを許可）
            .authorizeHttpRequests { auth ->
                auth
                    // 静的リソースは認証不要
                    .requestMatchers(
                        "/css/**", "/js/**", "/webjars/**",
                        "/favicon.ico", "/favicon.svg", "/.well-known/**"
                    ).permitAll()
                    // APIエンドポイント（CSRF保護あり）
                    .requestMatchers("/api/**").permitAll()
                    // Actuatorエンドポイントは制限を検討
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").denyAll() // その他のactuatorエンドポイントは拒否
                    // その他は全て許可（将来的に認証を追加する場合はここを変更）
                    .anyRequest().permitAll()
            }

            // フォームログインは現時点では無効化（必要に応じて有効化）
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }

        return http.build()
    }

    /**
     * CORS設定: 本番環境では適切に制限すること
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            // 本番環境では特定のオリジンのみ許可すべき
            allowedOrigins = ALLOWED_ORIGINS
            allowedMethods = ALLOWED_METHODS
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = CORS_MAX_AGE
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
