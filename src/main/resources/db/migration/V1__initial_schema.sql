/*
 Table: access_logs
 Purpose: Store every HTTP request for auditing and analysis.
*/
CREATE TABLE access_logs
(
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    request_id     VARCHAR(128) NOT NULL,
    method         VARCHAR(16)  NOT NULL,
    path           VARCHAR(512) NOT NULL,
    query          VARCHAR(1024),
    status         INTEGER      NOT NULL,
    duration_ms    BIGINT,
    remote_ip      VARCHAR(64),
    user_agent     TEXT,
    referer        TEXT,
    username       VARCHAR(128),
    request_bytes  BIGINT,
    response_bytes BIGINT
);
CREATE INDEX idx_access_logs_created_at ON access_logs (created_at);
CREATE INDEX idx_access_logs_remote_ip ON access_logs (remote_ip);
CREATE INDEX idx_access_logs_user_agent ON access_logs USING BTREE(user_agent);
CREATE INDEX idx_access_logs_path ON access_logs (path);

/*
 Table: blacklist_events
 Purpose: Record events that triggered blacklist actions.
*/
CREATE TABLE blacklist_events
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    request_id VARCHAR(128),
    ip_address VARCHAR(64)  NOT NULL,
    method     VARCHAR(16),
    path       VARCHAR(512),
    status     INTEGER,
    user_agent TEXT,
    referer    TEXT,
    reason     VARCHAR(512) NOT NULL,
    source     VARCHAR(128) NOT NULL
);
CREATE INDEX idx_blacklist_events_ip ON blacklist_events (ip_address);
CREATE INDEX idx_blacklist_events_created ON blacklist_events (created_at);

/*
 Table: blacklist_ips
 Purpose: Track IP addresses that are blacklisted plus count of occurrences.
*/
CREATE TABLE blacklist_ips
(
    id         BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    times      INTEGER     NOT NULL DEFAULT 1
);
CREATE INDEX idx_blacklist_ips_deleted ON blacklist_ips (deleted);

/*
 Table: whitelist_ips
 Purpose: Track IP addresses explicitly whitelisted with blacklist stats.
*/
CREATE TABLE whitelist_ips
(
    id                BIGSERIAL PRIMARY KEY,
    ip_address        VARCHAR(64) NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    blacklisted       BOOLEAN              DEFAULT FALSE,
    blacklisted_count INTEGER              DEFAULT 0
);
CREATE INDEX idx_whitelist_ips_blacklisted ON whitelist_ips (blacklisted);

/*
 Table: ua_blacklist_rules
 Purpose: User-Agent pattern rules for blacklist (pattern matching types: EXACT / PREFIX / REGEX)
*/
CREATE TABLE ua_blacklist_rules
(
    id         BIGSERIAL PRIMARY KEY,
    pattern    VARCHAR(512) NOT NULL,
    match_type VARCHAR(32)  NOT NULL DEFAULT 'EXACT',
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ua_blacklist_rules_deleted ON ua_blacklist_rules (deleted);
CREATE INDEX idx_ua_blacklist_rules_pattern ON ua_blacklist_rules (pattern);

/*
 Table: timesheet_entries (future use for勤務表機能; currently UI is client-side only)
*/
CREATE TABLE timesheet_entries
(
    id         BIGSERIAL PRIMARY KEY,
    work_date  DATE         NOT NULL,
    user_name  VARCHAR(128) NOT NULL,
    start_time TIME,
    end_time   TIME,
    note       VARCHAR(512),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_timesheet_user_date UNIQUE (user_name, work_date)
);
CREATE INDEX idx_timesheet_entries_work_date ON timesheet_entries (work_date);
