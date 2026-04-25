CREATE INDEX IF NOT EXISTS idx_access_logs_remote_ip_created_at_desc
    ON access_logs (remote_ip, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_access_logs_missing_user_agent_remote_ip
    ON access_logs (remote_ip)
    WHERE remote_ip IS NOT NULL
      AND (user_agent IS NULL OR btrim(user_agent) = '');

CREATE INDEX IF NOT EXISTS idx_access_logs_lower_user_agent_pattern
    ON access_logs (lower(user_agent) text_pattern_ops)
    WHERE remote_ip IS NOT NULL
      AND user_agent IS NOT NULL;