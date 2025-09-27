package com.example.demo.config

import io.undertow.server.DefaultByteBufferPool
import io.undertow.websockets.jsr.WebSocketDeploymentInfo
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.util.unit.DataSize

@Configuration
class UndertowWebSocketConfig(
    private val env: Environment
) {

    @Bean
    fun undertowServletWebServerFactory(): UndertowServletWebServerFactory {
        val factory = UndertowServletWebServerFactory()

        val customizer = UndertowDeploymentInfoCustomizer { di ->
            // Undertow の WebSocket 用バッファプールを設定（警告 UT026010 の解消）
            val direct = true // 直接バッファ（GC 負荷軽減）
            val dataSize: DataSize = env.getProperty(
                "server.undertow.buffer-size",
                DataSize::class.java,
                DataSize.ofKilobytes(16)
            )
            val bufferSize: Int = dataSize.toBytes().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val wsInfo = WebSocketDeploymentInfo()
            wsInfo.buffers = DefaultByteBufferPool(direct, bufferSize)
            (di as io.undertow.servlet.api.DeploymentInfo)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, wsInfo)
        }

        factory.setDeploymentInfoCustomizers(listOf(customizer))
        return factory
    }
}
