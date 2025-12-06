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
CREATE INDEX IF NOT EXISTS idx_access_logs_user_agent ON access_logs USING BTREE (user_agent);
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
    blacklisted       BOOLEAN              DEFAULT FALSE,
    blacklisted_count INTEGER              DEFAULT 0
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
    id                  BIGSERIAL PRIMARY KEY,
    work_date           DATE         NOT NULL,
    user_name           VARCHAR(128) NOT NULL,
    start_time          TIME,
    end_time            TIME,
    note                VARCHAR(512),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    break_minutes       INTEGER               DEFAULT 0,
    duration_minutes    INTEGER,
    working_minutes     INTEGER,
    version             INTEGER      NOT NULL DEFAULT 0,
    holiday_work        BOOLEAN      NOT NULL DEFAULT FALSE,
    work_location       VARCHAR(10)           DEFAULT NULL,
    irregular_work_type VARCHAR(20)           DEFAULT NULL,
    irregular_work_desc VARCHAR(255)          DEFAULT NULL,
    late_time           TEXT                  DEFAULT NULL,
    late_desc           VARCHAR(255)          DEFAULT NULL,
    early_time          TEXT                  DEFAULT NULL,
    early_desc          VARCHAR(255)          DEFAULT NULL,
    paid_leave          TEXT                  DEFAULT NULL,
    irregular_work_data TEXT,
    CONSTRAINT uq_timesheet_user_date UNIQUE (user_name, work_date)
);
COMMENT ON COLUMN timesheet_entries.irregular_work_data IS '複数の変則勤務データ (JSON形式, text型)';
CREATE INDEX IF NOT EXISTS idx_timesheet_entries_work_date ON timesheet_entries (work_date);
CREATE INDEX IF NOT EXISTS idx_timesheet_user_date ON timesheet_entries (user_name, work_date);

-- ============================================
-- timesheet_monthly_view
-- ============================================
CREATE OR REPLACE VIEW timesheet_monthly_view AS
SELECT
    user_name
    , date_trunc('month', work_date)::date month_first
    , SUM(COALESCE(working_minutes, 0))    total_working_minutes
    , SUM(COALESCE(break_minutes, 0))      total_break_minutes
    , COUNT(*)                             total_days
FROM
    timesheet_entries
GROUP BY
    user_name, date_trunc('month', work_date);

-- ============================================
-- report_jobs
-- ============================================
CREATE TABLE IF NOT EXISTS report_jobs
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(128) NOT NULL,
    from_date     DATE         NOT NULL,
    to_date       DATE         NOT NULL,
    format        VARCHAR(16)  NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    file_path     TEXT,
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_report_jobs_username ON report_jobs (username);
CREATE INDEX IF NOT EXISTS idx_report_jobs_status ON report_jobs (status);

-- V3__set_default_created_at_blacklist_events.sql
BEGIN;
UPDATE blacklist_events
SET
    created_at = CURRENT_TIMESTAMP
WHERE
    created_at IS NULL;
ALTER TABLE blacklist_events
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
COMMIT;

-- V4__add_calendar_holidays.sql
CREATE TABLE IF NOT EXISTS calendar_holidays
(
    id           BIGSERIAL PRIMARY KEY,
    holiday_date DATE         NOT NULL,
    name         VARCHAR(128) NOT NULL,
    year         INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_calendar_holiday_date UNIQUE (holiday_date)
);
CREATE INDEX IF NOT EXISTS idx_calendar_holidays_date ON calendar_holidays (holiday_date);
CREATE INDEX IF NOT EXISTS idx_calendar_holidays_year ON calendar_holidays (year);
-- 祝日データ初期投入（例）
INSERT INTO
    calendar_holidays (holiday_date, name, year)
VALUES
    ('2024-01-01', '元日', 2024)
    , ('2024-01-08', '成人の日', 2024)
    , ('2024-02-11', '建国記念の日', 2024)
    , ('2024-02-12', '振替休日', 2024)
    , ('2024-02-23', '天皇誕生日', 2024)
    , ('2024-03-20', '春分の日', 2024)
    , ('2024-04-29', '昭和の日', 2024)
    , ('2024-05-03', '憲法記念日', 2024)
    , ('2024-05-04', 'みどりの日', 2024)
    , ('2024-05-05', 'こどもの日', 2024)
    , ('2024-05-06', '振替休日', 2024)
    , ('2024-07-15', '海の日', 2024);
