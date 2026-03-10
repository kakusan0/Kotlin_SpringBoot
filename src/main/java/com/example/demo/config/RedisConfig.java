package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

/**
 * Redis セッション管理の設定
 * <p>
 * Spring Session Data Redis を使用してセッションを Redis に保存します。
 * セッション情報は JSON 形式でシリアライズされます。
 * <p>
 * EnableRedisIndexedHttpSession を使用することで、FindByIndexNameSessionRepository が有効になり、
 * ユーザー名によるセッション検索（同時セッション制御）が可能になります。
 */
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisConfig {

    /**
     * RedisTemplate のカスタマイズ
     * <p>
     * キーは String シリアライゼーション、値は JSON シリアライゼーションを使用。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());


        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
