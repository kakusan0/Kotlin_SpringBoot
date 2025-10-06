package com.example.demo.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * アクセスログテーブルを起動時に作成（存在しない場合）
 * Flywayが無効でも動作するように安全なIF NOT EXISTSで作成します。
 */
@Component
class AccessLogSchemaInitializer(
    private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {

    companion object {
        private val log = LoggerFactory.getLogger(AccessLogSchemaInitializer::class.java)
        private val DDL = """
            CREATE TABLE IF NOT EXISTS access_logs (
              id BIGSERIAL PRIMARY KEY,
              created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
              request_id VARCHAR(36) NOT NULL,
              method VARCHAR(16) NOT NULL,
              path TEXT NOT NULL,
              query TEXT,
              status INTEGER NOT NULL,
              duration_ms BIGINT,
              remote_ip VARCHAR(64),
              user_agent TEXT,
              referer TEXT,
              username VARCHAR(255),
              request_bytes BIGINT,
              response_bytes BIGINT
            );
            CREATE INDEX IF NOT EXISTS idx_access_logs_created_at ON access_logs(created_at);
            CREATE INDEX IF NOT EXISTS idx_access_logs_status ON access_logs(status);
            CREATE INDEX IF NOT EXISTS idx_access_logs_path ON access_logs(path);
        """.trimIndent()
    }

    override fun run(args: ApplicationArguments?) {
        try {
            jdbcTemplate.execute(DDL)
        } catch (e: Exception) {
            log.warn("アクセスログテーブルの初期化に失敗: {}", e.toString())
        }
    }
}
