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

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // CSRF保護を有効化（CookieベースのCSRFトークン）
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // APIエンドポイントでもCSRF保護を有効にする
            }

            // CORS設定
            .cors { cors ->
                cors.configurationSource(corsConfigurationSource())
            }

            // セキュリティヘッダーの設定
            .headers { headers ->
                headers
                    // X-Frame-Options: DENY（クリックジャッキング対策）
                    .frameOptions { it.deny() }
                    // X-Content-Type-Options: nosniff
                    .contentTypeOptions { }
                    // X-XSS-Protection: 1; mode=block
                    .xssProtection { xss ->
                        xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                    }
                    // Strict-Transport-Security（HTTPS使用時に有効化）
                    .httpStrictTransportSecurity { hsts ->
                        hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000) // 1年
                    }
                    // Referrer-Policy: 外部サイトへのリファラー情報を制限
                    .referrerPolicy { referrer ->
                        referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                    }
                    // Content-Security-Policy: XSS攻撃対策
                    .contentSecurityPolicy { csp ->
                        csp.policyDirectives(
                            "default-src 'self'; " +
                                    "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                    "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                    "img-src 'self' data: https:; " +
                                    "font-src 'self' data: https://cdn.jsdelivr.net; " +
                                    "connect-src 'self'; " +
                                    "frame-ancestors 'none'; " +
                                    "base-uri 'self'; " +
                                    "form-action 'self'"
                        )
                    }
            }

            // 認証設定（現時点では全アクセスを許可）
            .authorizeHttpRequests { auth ->
                auth
                    // 静的リソースは認証不要
                    .requestMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.ico", "/favicon.svg").permitAll()
                    // ブラウザの自動リクエストを許可（Chrome DevToolsなど）
                    .requestMatchers("/.well-known/**").permitAll()
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

        return http.build()
    }

    /**
     * CORS設定: 本番環境では適切に制限すること
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        // 本番環境では特定のオリジンのみ許可すべき
        configuration.allowedOrigins = listOf("https://localhost:8443")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
