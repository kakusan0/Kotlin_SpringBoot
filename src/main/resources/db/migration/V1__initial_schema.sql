-- =============================================================================
-- V1: Initial Database Schema
-- 全てのテーブルとインデックスを統合したマイグレーション
-- =============================================================================

-- =============================================================================
-- 共通関数: updated_at自動更新トリガー
-- =============================================================================
CREATE
OR REPLACE FUNCTION pg_trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at
= now();
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

-- =============================================================================
-- コンテンツアイテムテーブル
-- =============================================================================
DROP TABLE IF EXISTS content_items CASCADE;
CREATE TABLE content_items
(
    id         BIGSERIAL PRIMARY KEY,
    item_name  VARCHAR(255) NOT NULL,
    menu_name  VARCHAR(255) NOT NULL DEFAULT '',
    path_name  VARCHAR(255),
    created_at TIMESTAMP             DEFAULT (now() AT TIME ZONE 'UTC'),
    updated_at TIMESTAMP             DEFAULT (now() AT TIME ZONE 'UTC'),
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_item_name ON content_items (item_name);
CREATE INDEX idx_menu_name ON content_items (menu_name);
CREATE INDEX idx_path_name ON content_items (path_name);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON content_items
    FOR EACH ROW
    EXECUTE FUNCTION pg_trigger_set_updated_at();

-- =============================================================================
-- メニューテーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS menus
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    name
    VARCHAR
(
    255
) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP
                         WITH TIME ZONE DEFAULT now()
    );

CREATE UNIQUE INDEX idx_menu_name_unique ON menus (name);
CREATE INDEX ix_menus_deleted ON menus (deleted);

CREATE TRIGGER set_updated_at_menus
    BEFORE UPDATE
    ON menus
    FOR EACH ROW
    EXECUTE FUNCTION pg_trigger_set_updated_at();

COMMENT
ON COLUMN menus.deleted IS '論理削除フラグ（TRUE=削除済み、FALSE=有効）';

-- =============================================================================
-- パステーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS paths
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    name
    VARCHAR
(
    100
) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP
                         WITH TIME ZONE DEFAULT now()
    );

CREATE UNIQUE INDEX ux_paths_name ON paths (name);
CREATE INDEX ix_paths_deleted ON paths (deleted);

CREATE TRIGGER set_updated_at_paths
    BEFORE UPDATE
    ON paths
    FOR EACH ROW
    EXECUTE FUNCTION pg_trigger_set_updated_at();

-- =============================================================================
-- ホワイトリストIPテーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS whitelist_ips
(
    id
    SERIAL
    PRIMARY
    KEY,
    ip_address
    VARCHAR
(
    45
) NOT NULL UNIQUE,
    blacklisted BOOLEAN DEFAULT FALSE,
    blacklisted_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX ix_whitelist_ips_created_at ON whitelist_ips (created_at);

-- =============================================================================
-- ブラックリストIPテーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS blacklist_ips
(
    id
    SERIAL
    PRIMARY
    KEY,
    ip_address
    VARCHAR
(
    45
) NOT NULL UNIQUE,
    deleted BOOLEAN DEFAULT FALSE,
    times INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX ix_blacklist_ips_active ON blacklist_ips (ip_address) WHERE deleted = FALSE;
CREATE INDEX ix_blacklist_ips_created_at ON blacklist_ips (created_at);

-- =============================================================================
-- アクセスログテーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS access_logs
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    created_at
    TIMESTAMP
    WITH
    TIME
    ZONE
    DEFAULT
    now
(
),
    request_id VARCHAR
(
    36
) NOT NULL,
    method VARCHAR
(
    16
) NOT NULL,
    path TEXT NOT NULL,
    query TEXT,
    status INTEGER NOT NULL,
    duration_ms BIGINT,
    remote_ip VARCHAR
(
    64
),
    user_agent TEXT,
    referer TEXT,
    username VARCHAR
(
    255
),
    request_bytes BIGINT,
    response_bytes BIGINT
    );

CREATE INDEX idx_access_logs_created_at ON access_logs (created_at);
CREATE INDEX idx_access_logs_status ON access_logs (status);
CREATE INDEX idx_access_logs_path ON access_logs (path);

-- =============================================================================
-- ブラックリストイベントテーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS blacklist_events
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    created_at
    TIMESTAMP
    WITH
    TIME
    ZONE
    DEFAULT
    now
(
),
    request_id VARCHAR
(
    36
),
    ip_address VARCHAR
(
    45
) NOT NULL,
    method VARCHAR
(
    16
),
    path TEXT,
    status INTEGER,
    user_agent TEXT,
    referer TEXT,
    reason VARCHAR
(
    32
) NOT NULL,
    source VARCHAR
(
    16
) NOT NULL
    );

CREATE INDEX ix_blacklist_events_created_at ON blacklist_events (created_at);
CREATE INDEX ix_blacklist_events_ip ON blacklist_events (ip_address);

-- =============================================================================
-- User-Agentブラックリストルールテーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS ua_blacklist_rules
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    created_at
    TIMESTAMP
    WITH
    TIME
    ZONE
    DEFAULT
    now
(
),
    pattern TEXT NOT NULL,
    match_type VARCHAR
(
    16
) NOT NULL DEFAULT 'EXACT',
    deleted BOOLEAN NOT NULL DEFAULT FALSE
    );

CREATE UNIQUE INDEX ux_ua_blacklist_pattern_type ON ua_blacklist_rules (pattern, match_type) WHERE deleted = FALSE;
CREATE INDEX ix_ua_blacklist_active ON ua_blacklist_rules (deleted) WHERE deleted = FALSE;

-- =============================================================================
-- ユーザーメニューテーブル
-- =============================================================================
CREATE TABLE IF NOT EXISTS user_menu
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    username
    VARCHAR
(
    255
) NOT NULL,
    menu_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_menu UNIQUE
(
    username,
    menu_id
)
    );

CREATE INDEX idx_user_menu_username ON user_menu (username);
CREATE INDEX idx_user_menu_menu_id ON user_menu (menu_id);

-- =============================================================================
-- サンプルデータ: ユーザーメニューの割り当て
-- =============================================================================
-- メニューID 1, 2 が存在する場合のみ挿入
INSERT INTO
    user_menu (username, menu_id)
SELECT
    'user'
    , 1 WHERE EXISTS (SELECT 1 FROM menus WHERE id = 1)
ON CONFLICT (username, menu_id) DO NOTHING;

INSERT INTO
    user_menu (username, menu_id)
SELECT
    'user'
    , 2 WHERE EXISTS (SELECT 1 FROM menus WHERE id = 2)
ON CONFLICT (username, menu_id) DO NOTHING;

