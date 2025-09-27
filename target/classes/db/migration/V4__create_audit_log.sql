-- Create audit_log table for Log4j2 JDBC Appender
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    level VARCHAR(16) NOT NULL,
    logger VARCHAR(255) NOT NULL,
    message TEXT,
    thread VARCHAR(128),
    exception TEXT,
    request_id VARCHAR(64),
    user_id VARCHAR(128),
    client_ip VARCHAR(64),
    method VARCHAR(16),
    uri TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_log_event_time ON audit_log(event_time);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_request_id ON audit_log(request_id);

