-- Create access_logs table and indexes (PostgreSQL)
-- Mirrors AccessLogSchemaInitializer DDL, now managed by Flyway

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

