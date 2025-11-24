-- ============================================
-- access_logs
-- ============================================
CREATE TABLE IF NOT EXISTS access_logs
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
CREATE INDEX IF NOT EXISTS idx_access_logs_created_at ON access_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_access_logs_remote_ip ON access_logs (remote_ip);
CREATE INDEX IF NOT EXISTS idx_access_logs_user_agent ON access_logs USING BTREE(user_agent);
CREATE INDEX IF NOT EXISTS idx_access_logs_path ON access_logs (path);

-- ============================================
-- blacklist_events
-- ============================================
CREATE TABLE IF NOT EXISTS blacklist_events
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
CREATE INDEX IF NOT EXISTS idx_blacklist_events_ip ON blacklist_events (ip_address);
CREATE INDEX IF NOT EXISTS idx_blacklist_events_created ON blacklist_events (created_at);

-- ============================================
-- blacklist_ips
-- ============================================
CREATE TABLE IF NOT EXISTS blacklist_ips
(
    id         BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    times      INTEGER     NOT NULL DEFAULT 1
    );
CREATE INDEX IF NOT EXISTS idx_blacklist_ips_deleted ON blacklist_ips (deleted);

-- ============================================
-- whitelist_ips
-- ============================================
CREATE TABLE IF NOT EXISTS whitelist_ips
(
    id                BIGSERIAL PRIMARY KEY,
    ip_address        VARCHAR(64) NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    blacklisted BOOLEAN DEFAULT FALSE,
    blacklisted_count INTEGER DEFAULT 0
    );
CREATE INDEX IF NOT EXISTS idx_whitelist_ips_blacklisted ON whitelist_ips (blacklisted);

-- ============================================
-- ua_blacklist_rules
-- ============================================
CREATE TABLE IF NOT EXISTS ua_blacklist_rules
(
    id         BIGSERIAL PRIMARY KEY,
    pattern    VARCHAR(512) NOT NULL,
    match_type VARCHAR(32)  NOT NULL DEFAULT 'EXACT',
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    );
CREATE INDEX IF NOT EXISTS idx_ua_blacklist_rules_deleted ON ua_blacklist_rules (deleted);
CREATE INDEX IF NOT EXISTS idx_ua_blacklist_rules_pattern ON ua_blacklist_rules (pattern);

-- ============================================
-- timesheet_entries
-- ============================================
CREATE TABLE IF NOT EXISTS timesheet_entries
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    work_date
    DATE
    NOT
    NULL,
    user_name
    VARCHAR
(
    128
) NOT NULL,
    start_time TIME,
    end_time TIME,
    note VARCHAR
(
    512
),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    break_minutes INTEGER DEFAULT 0,
    duration_minutes INTEGER,
    working_minutes INTEGER,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_timesheet_user_date UNIQUE (user_name, work_date)
    );
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_work_date ON timesheet_entries (work_date);
CREATE INDEX IF NOT EXISTS idx_timesheet_user_date ON timesheet_entries (user_name, work_date);

-- ============================================
-- timesheet_monthly_view
-- ============================================
CREATE
OR REPLACE VIEW timesheet_monthly_view AS
SELECT
    user_name
    , date_trunc('month', work_date)::date AS month_first, SUM(COALESCE(working_minutes, 0)) total_working_minutes
    , SUM(COALESCE(break_minutes, 0)) total_break_minutes
    , COUNT(*)                        total_days
FROM
    timesheet_entries
GROUP BY
    user_name, date_trunc('month', work_date);

-- ============================================
-- report_jobs
-- ============================================
CREATE TABLE IF NOT EXISTS report_jobs
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    username
    VARCHAR
(
    128
) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    format VARCHAR
(
    16
) NOT NULL,
    status VARCHAR
(
    32
) NOT NULL DEFAULT 'PENDING',
    file_path TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );
CREATE INDEX IF NOT EXISTS idx_report_jobs_username ON report_jobs(username);
CREATE INDEX IF NOT EXISTS idx_report_jobs_status ON report_jobs(status);
