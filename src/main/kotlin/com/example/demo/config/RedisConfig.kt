package com.example.demo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession

/**
 * Redis セッション管理の設定
 *
 * Spring Session Data Redis を使用してセッションを Redis に保存します。
 * セッション情報は JSON 形式でシリアライズされます。
 *
 * EnableRedisIndexedHttpSession を使用することで、FindByIndexNameSessionRepository が有効になり、
 * ユーザー名によるセッション検索（同時セッション制御）が可能になります。
 */
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800) // 30分
class RedisConfig {

    /**
     * RedisTemplate のカスタマイズ
     *
     * デフォルトでは JDK シリアライゼーションが使用されますが、
     * JSON シリアライゼーションに変更して相互運用性を向上させます。
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)

            // キーのシリアライザ
            keySerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()

            // 値のシリアライザ (JSON 形式)
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
        }
    }
}
