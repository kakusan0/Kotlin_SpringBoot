package com.example.demo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // CSRF保護を有効化（CookieベースのCSRFトークン）
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
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
                            .maxAgeInSeconds(31536000)
                    }
            }

            // 認証設定（現時点では全アクセスを許可）
            .authorizeHttpRequests { auth ->
                auth
                    // 静的リソースは認証不要
                    .requestMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.ico").permitAll()
                    // Actuatorエンドポイントは制限を検討
                    .requestMatchers("/actuator/**").permitAll()
                    // その他は全て許可（将来的に認証を追加する場合はここを変更）
                    .anyRequest().permitAll()
            }

            // フォームログインは現時点では無効化（必要に応じて有効化）
            .formLogin { it.disable() }
            .httpBasic { it.disable() }

        return http.build()
    }
}

