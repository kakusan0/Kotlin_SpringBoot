-- Optimize IP management tables
-- Whitelist: ordering by created_at
CREATE INDEX IF NOT EXISTS ix_whitelist_ips_created_at ON whitelist_ips(created_at);

-- Blacklist: fast active lookup and ordering
CREATE INDEX IF NOT EXISTS ix_blacklist_ips_active ON blacklist_ips(ip_address) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS ix_blacklist_ips_created_at ON blacklist_ips(created_at);

