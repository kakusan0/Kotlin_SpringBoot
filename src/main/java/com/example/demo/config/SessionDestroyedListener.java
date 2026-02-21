package com.example.demo.config;

import com.example.demo.service.AipoLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * セッション破棄イベントリスナー
 * <p>
 * Spring Securityによるセッション破棄（タイムアウト、強制ログアウト、同時セッション制限等）を検知し、
 * 関連する外部サービス（Aipo等）のセッションもクリーンアップする。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionDestroyedListener {

    private final AipoLoginService aipoLoginService;

    /**
     * HTTPセッションが破棄されたときに呼び出される
     * - セッションタイムアウト
     * - ユーザーによるログアウト
     * - 同時セッション制限による強制ログアウト
     * - 管理者による強制ログアウト
     */
    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        List<SecurityContext> securityContexts = event.getSecurityContexts();
        for (SecurityContext securityContext : securityContexts) {
            Authentication authentication = securityContext.getAuthentication();
            if (authentication != null) {
                String username = authentication.getName();
                log.info("Session destroyed for user: {}. Logging out from Aipo...", username);

                try {
                    boolean success = aipoLoginService.logout(username);
                    if (success) {
                        log.info("Successfully logged out from Aipo for user: {}", username);
                    } else {
                        log.debug("No Aipo session found for user: {}", username);
                    }
                } catch (Exception e) {
                    log.warn("Failed to logout from Aipo for user: {}", username, e);
                }
            }
        }
    }
}
