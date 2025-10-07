-- Table for recording blacklist-related events (auto blocks, manual adds)
CREATE TABLE IF NOT EXISTS blacklist_events (
  id BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  request_id VARCHAR(36),
  ip_address VARCHAR(45) NOT NULL,
  method VARCHAR(16),
  path TEXT,
  status INTEGER,
  user_agent TEXT,
  referer TEXT,
  reason VARCHAR(32) NOT NULL,    -- e.g., COUNTRY, BLACKLIST, MANUAL
  source VARCHAR(16) NOT NULL     -- e.g., FILTER, API
);

CREATE INDEX IF NOT EXISTS ix_blacklist_events_created_at ON blacklist_events(created_at);
CREATE INDEX IF NOT EXISTS ix_blacklist_events_ip ON blacklist_events(ip_address);

