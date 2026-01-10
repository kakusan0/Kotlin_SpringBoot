package com.example.demo.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.header.writers.StaticHeadersWriter
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
import org.springframework.security.web.session.HttpSessionEventPublisher
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.session.security.SpringSessionBackedSessionRegistry
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val customAuthenticationFailureHandler: AuthenticationFailureHandler,
    private val loginRateLimitFilter: LoginRateLimitFilter
) {

    @Value("\${app.csp.connect-src:'self'}")
    private lateinit var cspConnectSrc: String

    @Value("\${webauthn.rp.id:localhost}")
    private lateinit var webAuthnRpId: String

    @Value("\${webauthn.rp.name:Dev RP}")
    private lateinit var webAuthnRpName: String

    @Value("\${webauthn.rp.origin:http://localhost:8080}")
    private lateinit var webAuthnRpOrigin: String

    @Value("\${webauthn.rp.allowed-origins:}")
    private var webAuthnAllowedOrigins: String = ""

    companion object {
        private const val HSTS_MAX_AGE = 31536000L // 1年
        private const val CORS_MAX_AGE = 3600L // 1時間
        private val ALLOWED_ORIGINS = listOf("https://localhost:8443", "http://localhost:8080", "https://infoapp.org")
        private val ALLOWED_METHODS = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
    }

    @Bean
    fun filterChain(http: HttpSecurity, sessionRegistry: SessionRegistry): SecurityFilterChain {
        val cspPolicy = buildString {
            append("default-src 'self'; ")
            append("script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; ")
            append("style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; ")
            append("img-src 'self' data: https:; ")
            append("font-src 'self' data: https://cdn.jsdelivr.net; ")
            append("connect-src 'self' https://date.nager.at ").append(cspConnectSrc).append("; ")
            append("frame-ancestors 'none'; ")
            append("base-uri 'self'; ")
            append("form-action 'self'")
        }

        http
            // CSRF保護を有効化（CookieベースのCSRFトークン + BREACH攻撃対策）
            .csrf { csrf ->
                val csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                val requestHandler = XorCsrfTokenRequestAttributeHandler()
                requestHandler.setCsrfRequestAttributeName(null) // リクエスト属性名をnullにしてdeferred token loadを有効化
                csrf.csrfTokenRepository(csrfTokenRepository)
                csrf.csrfTokenRequestHandler(requestHandler)
                csrf.ignoringRequestMatchers("/api/webauthn/**") // WebAuthnはフロントJSから直接叩くのでCSRF除外
            }

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
                    // キャッシュ制御: ブラウザの戻るボタンでキャッシュページを表示させない
                    .cacheControl { }
            }

            // 認可設定
            .authorizeHttpRequests { auth ->
                auth
                    // 静的リソースは認証不要
                    .requestMatchers(
                        "/css/**", "/js/**", "/webjars/**",
                        "/favicon.ico", "/favicon.svg", "/.well-known/**",
                        "/login", "/api/webauthn/**"
                    ).permitAll()
                    // メイン画面は認証不要（閲覧のみ）
                    .requestMatchers("/", "/home", "/home/**", "/tools", "/tools/**").permitAll()
                    // 勤務表はUNISSロールのみアクセス可能
                    .requestMatchers("/timesheet", "/timesheet/**").hasRole("UNISS")
                    // 祝日APIは閲覧のみ認証不要
                    .requestMatchers("/api/calendar/holidays").permitAll()
                    .requestMatchers("/api/calendar/holidays/list").permitAll()
                    .requestMatchers("/api/calendar/holidays/range").permitAll()
                    .requestMatchers("/api/calendar/holidays/**").authenticated() // 追加・削除は認証必須
                    // 管理画面は認証必須
                    .requestMatchers("/manage/**").hasRole("ADMIN")
                    // その他のAPIは認証必須
                    .requestMatchers("/api/**").authenticated()
                    // Actuatorエンドポイントは制限
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").denyAll()
                    // エラーページは許可
                    .requestMatchers("/error").permitAll()
                    // その他は認証必須（デフォルト拒否の原則）
                    .anyRequest().authenticated()
            }
            // ログイン前にログイン試行のレート制限フィルターを追加
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)

            // フォームログインを有効化
            .formLogin {
                it.loginPage("/login").permitAll()
                it.defaultSuccessUrl("/tools")
                it.failureHandler(customAuthenticationFailureHandler)
            }
            .webAuthn {
                it.rpName(webAuthnRpName)
                it.rpId(webAuthnRpId)
                // 複数オリジンをサポート（カンマ区切り）
                val origins = if (webAuthnAllowedOrigins.isNotBlank()) {
                    webAuthnAllowedOrigins.split(",").map { o -> o.trim() }.toSet()
                } else {
                    setOf(webAuthnRpOrigin)
                }
                it.allowedOrigins(origins)
            }
            .httpBasic { it.disable() }
            .logout {
                it.logoutUrl("/logout")
                it.logoutSuccessUrl("/login?logout=true") // ログアウト後はログインモーダル表示
                it.invalidateHttpSession(true) // セッションを無効化
                it.deleteCookies("JSESSIONID", "SESSION") // セッションCookieを削除
                it.clearAuthentication(true) // 認証情報をクリア
                it.permitAll()
            }

            // セッション管理: セッション無効（タイムアウトや強制ログアウト）時のリダイレクト先
            // および同一ユーザ最大セッション数を 1 にして古いセッションを切断（新ログインを許可）
            .sessionManagement { sess ->
                sess.sessionFixation { it.migrateSession() } // セッション固定攻撃対策
                sess.invalidSessionUrl("/tools") // セッション無効時もツールへ
                sess.maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .expiredUrl("/tools") // セッション期限切れもツールへ
                    .sessionRegistry(sessionRegistry)
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
            allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",") ?: ALLOWED_ORIGINS
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

    /**
     * ユーザー認証設定
     *
     * 【本番環境での注意事項】
     * - InMemoryUserDetailsManagerは開発用。本番ではDBまたはLDAP/OIDC等を使用すること
     * - パスワードは最低12文字以上、大文字・小文字・数字・記号を含むこと
     * - ユーザー名とパスワードを同一にしないこと
     */
    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        val user1 = User.builder()
            .username("角谷亮洋")
            .password(passwordEncoder.encode("角谷亮洋"))
            .roles("USER", "UNISS") // UNISSロールを追加（勤務表アクセス用）
            .build()

        // 管理者ユーザー（ADMINロール）
        val admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("Admin@2026!Secure"))
            .roles("ADMIN")
            .build()

        return InMemoryUserDetailsManager(user1, admin)
    }

    @Bean
    fun sessionRegistry(repoProvider: ObjectProvider<FindByIndexNameSessionRepository<Session>>): SessionRegistry {
        val repo = repoProvider.ifAvailable
        return if (repo != null) {
            SpringSessionBackedSessionRegistry(repo)
        } else {
            SessionRegistryImpl()
        }
    }

    @Bean
    fun httpSessionEventPublisher(): ServletListenerRegistrationBean<HttpSessionEventPublisher> {
        return ServletListenerRegistrationBean(HttpSessionEventPublisher())
    }
}
