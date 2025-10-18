package com.example.demo.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class MyDnsUpdateService(
    @Value("\${mydns.username}") private val username: String,
    @Value("\${mydns.password}") private val password: String,
    @Value("\${mydns.ipv4.url}") private val ipv4Url: String,
    @Value("\${mydns.ipv6.url}") private val ipv6Url: String,
    private val restClientBuilder: RestClient.Builder
) {
    private val logger = LoggerFactory.getLogger(MyDnsUpdateService::class.java)

    private val restClient: RestClient = restClientBuilder
        .build()

    fun updateDynamicDns(): Boolean {
        // 資格情報が未設定ならスキップ
        if (username.isBlank() || password.isBlank()) {
            logger.warn("MyDNS credentials are not set. Skipping MyDNS update.")
            return true // スキップとして成功扱い（起動やジョブを失敗させない）
        }

        logger.info("Starting MyDNS IP address update for both IPv4 and IPv6")

        val ipv4Success = updateDns(ipv4Url, "IPv4")
        val ipv6Success = updateDns(ipv6Url, "IPv6")

        val overallSuccess = ipv4Success && ipv6Success
        logger.info("MyDNS update completed. IPv4: ${if (ipv4Success) "SUCCESS" else "FAILED"}, IPv6: ${if (ipv6Success) "SUCCESS" else "FAILED"}")

        return overallSuccess
    }

    private fun updateDns(url: String, type: String): Boolean {
        return try {
            logger.info("Updating MyDNS $type address: $url")

            // Basic認証のヘッダーを作成
            val auth = "$username:$password"
            val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray(StandardCharsets.UTF_8))

            // HTTPリクエストを実行
            val response = restClient.get()
                .uri(url)
                .header("Authorization", "Basic $encodedAuth")
                .retrieve()
                .toEntity(String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                val responseBody = response.body ?: ""
                logger.info("MyDNS $type update successful. Response: ${responseBody.take(200)}")
                true
            } else {
                logger.error("MyDNS $type update failed with status: ${response.statusCode}")
                false
            }
        } catch (e: RestClientException) {
            logger.error("Error updating MyDNS $type: ${e.message}", e)
            false
        } catch (e: Exception) {
            logger.error("Unexpected error updating MyDNS $type: ${e.message}", e)
            false
        }
    }
}
