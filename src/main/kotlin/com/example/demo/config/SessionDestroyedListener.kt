package com.example.demo.config

import com.example.demo.service.AipoLoginService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.security.web.session.HttpSessionDestroyedEvent
import org.springframework.stereotype.Component

/**
 * セッション破棄イベントリスナー
 *
 * Spring Securityによるセッション破棄（タイムアウト、強制ログアウト、同時セッション制限等）を検知し、
 * 関連する外部サービス（Aipo等）のセッションもクリーンアップする。
 */
@Component
class SessionDestroyedListener(
    private val aipoLoginService: AipoLoginService
) {
    private val logger = LoggerFactory.getLogger(SessionDestroyedListener::class.java)

    /**
     * HTTPセッションが破棄されたときに呼び出される
     * - セッションタイムアウト
     * - ユーザーによるログアウト
     * - 同時セッション制限による強制ログアウト
     * - 管理者による強制ログアウト
     */
    @EventListener
    fun onSessionDestroyed(event: HttpSessionDestroyedEvent) {
        val securityContexts = event.securityContexts

        for (securityContext in securityContexts) {
            val authentication = securityContext.authentication
            if (authentication != null) {
                val username = authentication.name
                logger.info("Session destroyed for user: $username. Logging out from Aipo...")

                try {
                    // Aipoからもログアウト
                    val success = aipoLoginService.logout(username)
                    if (success) {
                        logger.info("Successfully logged out from Aipo for user: $username")
                    } else {
                        logger.debug("No Aipo session found for user: $username")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to logout from Aipo for user: $username", e)
                }
            }
        }
    }
}

