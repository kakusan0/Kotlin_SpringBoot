-- User-Agent blacklist rules
CREATE TABLE IF NOT EXISTS ua_blacklist_rules (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  pattern TEXT NOT NULL,
  match_type VARCHAR(16) NOT NULL DEFAULT 'EXACT', -- EXACT | PREFIX | REGEX
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- 同一パターン・種別の重複防止
CREATE UNIQUE INDEX IF NOT EXISTS ux_ua_blacklist_pattern_type ON ua_blacklist_rules(pattern, match_type) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS ix_ua_blacklist_active ON ua_blacklist_rules(deleted) WHERE deleted = FALSE;

