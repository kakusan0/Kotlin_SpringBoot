package com.example.demo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.header.writers.StaticHeadersWriter
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customAuthenticationFailureHandler: CustomAuthenticationFailureHandler,
    private val loginRateLimitFilter: LoginRateLimitFilter,
) {

    @Value("\${app.csp.connect-src:'self'}")
    private lateinit var cspConnectSrc: String

    companion object {
        private const val HSTS_MAX_AGE = 31536000L // 1年
        private const val CORS_MAX_AGE = 3600L // 1時間
        private val ALLOWED_ORIGINS = listOf("https://localhost:8443", "http://localhost:8080")
        private val ALLOWED_METHODS = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val cspPolicy = buildString {
            append("default-src 'self'; ")
            append("script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; ")
            append("style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; ")
            append("img-src 'self' data: https:; ")
            append("font-src 'self' data: https://cdn.jsdelivr.net; ")
            append("connect-src ").append(cspConnectSrc).append("; ")
            append("frame-ancestors 'none'; ")
            append("base-uri 'self'; ")
            append("form-action 'self'")
        }

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
                    // X-XSS-Protection: 1; mode=block（非推奨だが互換性のため残す）
                    .xssProtection { it.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK) }
                    // Strict-Transport-Security（HTTPS使用時のみ有効化、Tomcatがリバプロ背後でHTTPSを処理する場合も機能）
                    .httpStrictTransportSecurity { it.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE) }
                    // Referrer-Policy: 外部サイトへのリファラー情報を制限
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                    // Content-Security-Policy: XSS攻撃対策
                    .contentSecurityPolicy { it.policyDirectives(cspPolicy) }
                    // Permissions-Policy: ブラウザ機能の制限
                    .addHeaderWriter(
                        StaticHeadersWriter(
                            "Permissions-Policy",
                            "geolocation=(), microphone=(), camera=(), usb=(), payment=(), fullscreen()"
                        )
                    )
            }

            // 認可設定
            .authorizeHttpRequests { auth ->
                auth
                    // 静的リソース・ログインなどは認証不要
                    .requestMatchers(
                        "/css/**", "/js/**", "/webjars/**",
                        "/favicon.ico", "/favicon.svg", "/.well-known/**",
                        "/login"
                    ).permitAll()
                    // ホーム/コンテンツのGETは未ログインでも許可
                    .requestMatchers(HttpMethod.GET, "/", "/home", "/home/**", "/content", "/content/**").permitAll()
                    // 管理画面トップはMENUまたはADMINで表示可（詳細パスより先に定義）
                    .requestMatchers("/manage").hasAnyRole("MENU", "ADMIN")
                    // 管理画面の詳細はADMIN限定
                    .requestMatchers("/manage/**").hasRole("ADMIN")
                    // API は必要に応じて見直し（現状は公開）
                    .requestMatchers("/api/**").permitAll()
                    // Actuator
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").denyAll()
                    // その他は拒否
                    .anyRequest().denyAll()
            }

            // ログイン前にログイン試行のレート制限フィルターを追加
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)

            // フォームログインを有効化
            .formLogin {
                it.loginPage("/login").permitAll()
                it.failureHandler(customAuthenticationFailureHandler)
            }
            .webAuthn {
                it.rpName("Spring Security Relying Party")
                it.rpId("localhost")
                it.allowedOrigins(setOf("http://localhost:8080"))
            }
            .httpBasic { it.disable() }
            .logout {
                it.logoutUrl("/logout")
                it.logoutSuccessUrl("/home")
                it.permitAll()
            }

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

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        val admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("admin"))
            .roles("ADMIN", "MENU")
            .build()
        val user1 = User.builder()
            .username("user1")
            .password(passwordEncoder.encode("user1"))
            .roles("USER", "MENU")
            .build()
        val user2 = User.builder()
            .username("user2")
            .password(passwordEncoder.encode("user2"))
            .roles("USER", "MENU")
            .build()
        return InMemoryUserDetailsManager(admin, user1, user2)
    }
}
