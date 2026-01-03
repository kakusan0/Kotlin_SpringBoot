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

COMMENT ON TABLE access_logs IS 'アクセスログ（リクエスト/レスポンスの記録）';
COMMENT ON COLUMN access_logs.id IS '主キー';
COMMENT ON COLUMN access_logs.created_at IS '記録日時（タイムゾーン付き）';
COMMENT ON COLUMN access_logs.request_id IS 'リクエスト識別子（ログ相関用）';
COMMENT ON COLUMN access_logs.method IS 'HTTPメソッド';
COMMENT ON COLUMN access_logs.path IS 'リクエストパス';
COMMENT ON COLUMN access_logs.query IS 'クエリ文字列（存在する場合）';
COMMENT ON COLUMN access_logs.status IS 'HTTPステータスコード';
COMMENT ON COLUMN access_logs.duration_ms IS '処理時間（ミリ秒）';
COMMENT ON COLUMN access_logs.remote_ip IS 'クライアントIPアドレス';
COMMENT ON COLUMN access_logs.user_agent IS 'User-Agentヘッダ';
COMMENT ON COLUMN access_logs.referer IS 'Refererヘッダ';
COMMENT ON COLUMN access_logs.username IS '認証ユーザー名（認証済みの場合）';
COMMENT ON COLUMN access_logs.request_bytes IS 'リクエストボディサイズ（バイト）';
COMMENT ON COLUMN access_logs.response_bytes IS 'レスポンスボディサイズ（バイト）';

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

COMMENT ON TABLE blacklist_events IS 'ブラックリスト登録/検知イベント（記録用）';
COMMENT ON COLUMN blacklist_events.id IS '主キー';
COMMENT ON COLUMN blacklist_events.created_at IS '発生日時（タイムゾーン付き）';
COMMENT ON COLUMN blacklist_events.request_id IS 'リクエスト識別子（ログ相関用）';
COMMENT ON COLUMN blacklist_events.ip_address IS '対象IPアドレス';
COMMENT ON COLUMN blacklist_events.method IS 'HTTPメソッド（記録用）';
COMMENT ON COLUMN blacklist_events.path IS 'リクエストパス（記録用）';
COMMENT ON COLUMN blacklist_events.status IS 'HTTPステータスコード（記録用）';
COMMENT ON COLUMN blacklist_events.user_agent IS 'User-Agentヘッダ（記録用）';
COMMENT ON COLUMN blacklist_events.referer IS 'Refererヘッダ（記録用）';
COMMENT ON COLUMN blacklist_events.reason IS 'ブラックリスト登録理由';
COMMENT ON COLUMN blacklist_events.source IS '登録元（例: AUTO/MANUAL 等）';

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

COMMENT ON TABLE blacklist_ips IS 'ブラックリストIP（アクセス拒否対象）';
COMMENT ON COLUMN blacklist_ips.id IS '主キー';
COMMENT ON COLUMN blacklist_ips.ip_address IS 'ブラックリスト対象IPアドレス（ユニーク）';
COMMENT ON COLUMN blacklist_ips.created_at IS '登録日時（タイムゾーン付き）';
COMMENT ON COLUMN blacklist_ips.deleted IS '論理削除フラグ（TRUE: 無効）';
COMMENT ON COLUMN blacklist_ips.times IS '検知/登録回数';

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

COMMENT ON TABLE whitelist_ips IS 'ホワイトリストIP（許可対象）';
COMMENT ON COLUMN whitelist_ips.id IS '主キー';
COMMENT ON COLUMN whitelist_ips.ip_address IS 'ホワイトリスト対象IPアドレス（ユニーク）';
COMMENT ON COLUMN whitelist_ips.created_at IS '登録日時（タイムゾーン付き）';
COMMENT ON COLUMN whitelist_ips.blacklisted IS 'ホワイトリストIPがブラックリスト化されたことがあるか';
COMMENT ON COLUMN whitelist_ips.blacklisted_count IS 'ブラックリスト化された回数';

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

COMMENT ON TABLE ua_blacklist_rules IS 'User-Agentブラックリストルール';
COMMENT ON COLUMN ua_blacklist_rules.id IS '主キー';
COMMENT ON COLUMN ua_blacklist_rules.pattern IS '照合パターン（文字列/正規表現）';
COMMENT ON COLUMN ua_blacklist_rules.match_type IS '照合方式（EXACT:完全一致 / PREFIX:前方一致 / REGEX:正規表現）';
COMMENT ON COLUMN ua_blacklist_rules.deleted IS '論理削除フラグ（TRUE: 無効）';
COMMENT ON COLUMN ua_blacklist_rules.created_at IS '作成日時（タイムゾーン付き）';
COMMENT ON COLUMN ua_blacklist_rules.updated_at IS '更新日時（タイムゾーン付き）';

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

COMMENT ON TABLE timesheet_entries IS '勤務表入力（ユーザー×日付の勤怠情報）';
COMMENT ON COLUMN timesheet_entries.id IS '主キー';
COMMENT ON COLUMN timesheet_entries.work_date IS '勤務日';
COMMENT ON COLUMN timesheet_entries.user_name IS 'ユーザー名';
COMMENT ON COLUMN timesheet_entries.start_time IS '開始時刻';
COMMENT ON COLUMN timesheet_entries.end_time IS '終了時刻';
COMMENT ON COLUMN timesheet_entries.note IS '備考';
COMMENT ON COLUMN timesheet_entries.created_at IS '作成日時（タイムゾーン付き）';
COMMENT ON COLUMN timesheet_entries.updated_at IS '更新日時（タイムゾーン付き）';
COMMENT ON COLUMN timesheet_entries.break_minutes IS '休憩時間（分）';
COMMENT ON COLUMN timesheet_entries.duration_minutes IS '拘束時間（分）（end-start等から算出）';
COMMENT ON COLUMN timesheet_entries.working_minutes IS '実働時間（分）（拘束-休憩等から算出）';
COMMENT ON COLUMN timesheet_entries.version IS '楽観ロック用バージョン';
COMMENT ON COLUMN timesheet_entries.holiday_work IS '休日出勤フラグ';
COMMENT ON COLUMN timesheet_entries.work_location IS '勤務地/勤務場所（例: 出社/在宅 等）';
COMMENT ON COLUMN timesheet_entries.irregular_work_type IS '変則勤務種別';
COMMENT ON COLUMN timesheet_entries.irregular_work_desc IS '変則勤務の説明';
COMMENT ON COLUMN timesheet_entries.late_time IS '遅刻時間（記録用）';
COMMENT ON COLUMN timesheet_entries.late_desc IS '遅刻理由/説明';
COMMENT ON COLUMN timesheet_entries.early_time IS '早退時間（記録用）';
COMMENT ON COLUMN timesheet_entries.early_desc IS '早退理由/説明';
COMMENT ON COLUMN timesheet_entries.paid_leave IS '有給休暇（種別/時間等の記録用）';
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

COMMENT ON VIEW timesheet_monthly_view IS 'ユーザー×月の勤務集計ビュー';
COMMENT ON COLUMN timesheet_monthly_view.user_name IS 'ユーザー名';
COMMENT ON COLUMN timesheet_monthly_view.month_first IS '対象月（1日固定）';
COMMENT ON COLUMN timesheet_monthly_view.total_working_minutes IS '月合計の実働時間（分）';
COMMENT ON COLUMN timesheet_monthly_view.total_break_minutes IS '月合計の休憩時間（分）';
COMMENT ON COLUMN timesheet_monthly_view.total_days IS '対象月のレコード件数（日数）';

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

COMMENT ON TABLE report_jobs IS '帳票出力ジョブ（非同期実行の状態管理）';
COMMENT ON COLUMN report_jobs.id IS '主キー';
COMMENT ON COLUMN report_jobs.username IS '対象ユーザー名';
COMMENT ON COLUMN report_jobs.from_date IS '対象期間（開始日）';
COMMENT ON COLUMN report_jobs.to_date IS '対象期間（終了日）';
COMMENT ON COLUMN report_jobs.format IS '出力形式（例: XLSX/PDF 等）';
COMMENT ON COLUMN report_jobs.status IS 'ジョブ状態（PENDING/RUNNING/DONE/FAILED 等）';
COMMENT ON COLUMN report_jobs.file_path IS '生成ファイルパス';
COMMENT ON COLUMN report_jobs.error_message IS 'エラーメッセージ（失敗時）';
COMMENT ON COLUMN report_jobs.created_at IS '作成日時（タイムゾーン付き）';
COMMENT ON COLUMN report_jobs.updated_at IS '更新日時（タイムゾーン付き）';

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

COMMENT ON TABLE calendar_holidays IS '祝日マスタ';
COMMENT ON COLUMN calendar_holidays.id IS '主キー';
COMMENT ON COLUMN calendar_holidays.holiday_date IS '祝日の日付';
COMMENT ON COLUMN calendar_holidays.name IS '祝日名称';
COMMENT ON COLUMN calendar_holidays.year IS '年';
COMMENT ON COLUMN calendar_holidays.created_at IS '作成日時（タイムゾーン付き）';
COMMENT ON COLUMN calendar_holidays.updated_at IS '更新日時（タイムゾーン付き）';

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
