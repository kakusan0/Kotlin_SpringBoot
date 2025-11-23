package com.example.demo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    private val loginRateLimitFilter: LoginRateLimitFilter
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
                    // X-XSS-Protection: 1; mode=block
                    .xssProtection { it.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK) }
                    // Strict-Transport-Security（HTTPS使用時に有効化）
                    .httpStrictTransportSecurity { it.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE) }
                    // Referrer-Policy: 外部サイトへのリファラー情報を制限
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                    // Content-Security-Policy: XSS攻撃対策
                    .contentSecurityPolicy { it.policyDirectives(cspPolicy) }
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
                    // 静的リソースは認証不要
                    .requestMatchers(
                        "/css/**", "/js/**", "/webjars/**",
                        "/favicon.ico", "/favicon.svg", "/.well-known/**",
                        "/login"
                    ).permitAll()
                    // APIエンドポイント（CSRF保護あり）
                    .requestMatchers("/api/**").permitAll()
                    // Actuatorエンドポイントは制限を検討
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").denyAll() // その他のactuatorエンドポイントは拒否
                    // /home, /tools, /timesheet, /manage, /content はログイン必須
                    .requestMatchers("/home/**", "/tools/**", "/timesheet/**", "/manage/**", "/content/**")
                    .authenticated()
                    // その他は全て許可（将来的に認証を追加する場合はここを変更）
                    .anyRequest().permitAll()
            }

            // ログイン前にログイン試行のレート制限フィルターを追加
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)

            // フォームログインを有効化
            .formLogin {
                it.loginPage("/login").permitAll()
                it.defaultSuccessUrl("/home")
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
                it.logoutSuccessUrl("/login?logout")
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
        val user1 = User.builder()
            .username("user1")
            .password(passwordEncoder.encode("user1"))
            .roles("USER")
            .build()
        return InMemoryUserDetailsManager(user1)
    }
}
