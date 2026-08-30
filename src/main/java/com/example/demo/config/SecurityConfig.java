package com.example.demo.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private static final long HSTS_MAX_AGE = 31536000L;
        private static final long CORS_MAX_AGE = 3600L;
        private static final List<String> ALLOWED_ORIGINS = List.of(
                        "https://localhost:8443",
                        "http://localhost:8080",
                        "https://infoapp.org");
        private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

        private final AuthenticationFailureHandler customAuthenticationFailureHandler;
        private final LoginRateLimitFilter loginRateLimitFilter;

        private final AppProperties appProperties;
        private final WebAuthnProperties webAuthnProperties;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) {
                String cspConnectSrc = appProperties.getCsp().getConnectSrc();
                String cspPolicy = "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                "img-src 'self' data: https:; " +
                                "font-src 'self' data: https://cdn.jsdelivr.net; " +
                                "connect-src 'self' https://date.nager.at " + cspConnectSrc + "; " +
                                "frame-ancestors 'none'; " +
                                "base-uri 'self'; " +
                                "form-action 'self'";

                http
                                .csrf(csrf -> {
                                        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository
                                                        .withHttpOnlyFalse();
                                        XorCsrfTokenRequestAttributeHandler requestHandler = new XorCsrfTokenRequestAttributeHandler();
                                        csrf.csrfTokenRepository(csrfTokenRepository);
                                        csrf.csrfTokenRequestHandler(requestHandler);
                                        csrf.ignoringRequestMatchers("/api/webauthn/**");
                                })
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .headers(headers -> headers
                                                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                                                .contentTypeOptions(Customizer.withDefaults())
                                                .xssProtection(xss -> xss.headerValue(
                                                                XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                                                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(HSTS_MAX_AGE))
                                                .referrerPolicy(referrer -> referrer.policy(
                                                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(cspPolicy))
                                                .addHeaderWriter(new StaticHeadersWriter(
                                                                "Permissions-Policy",
                                                                "geolocation=(), microphone=(), camera=(), usb=(), payment=(), fullscreen=()"))
                                                .cacheControl(Customizer.withDefaults()))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/css/**", "/js/**", "/webjars/**",
                                                                "/favicon.ico", "/favicon.svg", "/.well-known/**",
                                                                "/login", "/api/webauthn/**")
                                                .permitAll()
                                                .requestMatchers("/tools/passkey", "/tools/passkey/**").authenticated()
                                                .requestMatchers("/", "/home", "/home/**", "/tools", "/tools/**")
                                                .permitAll()
                                                .requestMatchers("/timesheet", "/timesheet/**").hasRole("UNISS")
                                                .requestMatchers("/api/calendar/holidays").permitAll()
                                                .requestMatchers("/api/calendar/holidays/list").permitAll()
                                                .requestMatchers("/api/calendar/holidays/range").permitAll()
                                                .requestMatchers("/api/calendar/holidays/**").authenticated()
                                                .requestMatchers("/manage/**").hasRole("ADMIN")
                                                .requestMatchers("/api/**").authenticated()
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                                .requestMatchers("/actuator/**").denyAll()
                                                .requestMatchers("/error").permitAll()
                                                .anyRequest().authenticated())
                                .exceptionHandling(ex -> ex
                                                .defaultAuthenticationEntryPointFor(
                                                                new LoginUrlAuthenticationEntryPoint("/tools"),
                                                                request -> request.getRequestURI() != null
                                                                                && request.getRequestURI().startsWith(
                                                                                                "/tools/passkey")))
                                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                                .formLogin(form -> form
                                                .loginPage("/login").permitAll()
                                                .defaultSuccessUrl("/tools")
                                                .failureHandler(customAuthenticationFailureHandler))
                                .webAuthn(webAuthn -> {
                                        webAuthn.rpName(webAuthnProperties.getName());
                                        webAuthn.rpId(webAuthnProperties.getId());
                                        Set<String> origins;
                                        if (webAuthnProperties.getAllowedOrigins() != null && !webAuthnProperties.getAllowedOrigins().isBlank()) {
                                                origins = Arrays.stream(webAuthnProperties.getAllowedOrigins().split(","))
                                                                .map(String::trim)
                                                                .collect(Collectors.toSet());
                                        } else {
                                                origins = Set.of(webAuthnProperties.getOrigin());
                                        }
                                        webAuthn.allowedOrigins(origins);
                                })
                                .httpBasic(HttpBasicConfigurer::disable)
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/tools")
                                                .invalidateHttpSession(true)
                                                .deleteCookies("JSESSIONID", "SESSION")
                                                .clearAuthentication(true)
                                                .permitAll())
                                .sessionManagement(sess -> sess
                                                .sessionFixation(
                                                                SessionManagementConfigurer.SessionFixationConfigurer::migrateSession)
                                                .invalidSessionUrl("/tools")
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false)
                                                .expiredUrl("/tools")
                                                .sessionRegistry(sessionRegistry));

                return http.build();
        }

        /**
         * CORS設定: 本番環境では適切に制限すること
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                String allowed = System.getenv("ALLOWED_ORIGINS");
                configuration.setAllowedOrigins(allowed != null ? Arrays.asList(allowed.split(",")) : ALLOWED_ORIGINS);
                configuration.setAllowedMethods(ALLOWED_METHODS);
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(CORS_MAX_AGE);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        /**
         * ユーザー認証（DB: users / authorities テーブル、Flyway V8 でスキーマ・初期データ投入）
         * <p>
         * パスワード変更・ユーザー追加は DB を更新するか、JdbcUserDetailsManager の API を利用すること。
         */
        @Bean
        public UserDetailsService userDetailsService(DataSource dataSource) {
                return new JdbcUserDetailsManager(dataSource);
        }

        @Bean
        public SessionRegistry sessionRegistry(ObjectProvider<FindByIndexNameSessionRepository<Session>> repoProvider) {
                FindByIndexNameSessionRepository<Session> repo = repoProvider.getIfAvailable();
                if (repo != null) {
                        return new SpringSessionBackedSessionRegistry<>(repo);
                }
                return new SessionRegistryImpl();
        }

        @Bean
        public SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
                ConcurrentSessionControlAuthenticationStrategy concurrent = new ConcurrentSessionControlAuthenticationStrategy(
                                sessionRegistry);
                concurrent.setMaximumSessions(1);
                concurrent.setExceptionIfMaximumExceeded(false);

                SessionFixationProtectionStrategy fixation = new SessionFixationProtectionStrategy();
                RegisterSessionAuthenticationStrategy register = new RegisterSessionAuthenticationStrategy(
                                sessionRegistry);
                return new CompositeSessionAuthenticationStrategy(List.of(concurrent, fixation, register));
        }

        @Bean
        public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
                return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
        }
}
