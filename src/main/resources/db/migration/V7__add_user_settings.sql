-- ============================================
-- user_settings
-- ============================================
CREATE TABLE IF NOT EXISTS user_settings
(
    id BIGSERIAL PRIMARY KEY,
    user_name           VARCHAR(128) NOT NULL UNIQUE,
    company_affiliation VARCHAR(50),  -- 所属選択: ユーニスイースト, ユーニスウエスト
    section             INTEGER,      -- セクション: 1-5
    branch_office       VARCHAR(50),  -- 支社: 東京支社, 名古屋支社, 大阪支社
    work_group          INTEGER,      -- グループ: 1-5
    employee_number     VARCHAR(20),  -- 社員番号
    site_regular_hours  TIME,         -- 現場定時時間
    display_name        VARCHAR(128), -- 氏名
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_settings IS 'ユーザー設定（UNISS勤務表用）';
COMMENT ON COLUMN user_settings.id IS '主キー';
COMMENT ON COLUMN user_settings.user_name IS 'ユーザー名（ユニーク）';
COMMENT ON COLUMN user_settings.company_affiliation IS '所属選択（ユーニスイースト / ユーニスウエスト）';
COMMENT ON COLUMN user_settings.section IS 'セクション（1-5）';
COMMENT ON COLUMN user_settings.branch_office IS '支社（東京支社 / 名古屋支社 / 大阪支社）';
COMMENT ON COLUMN user_settings.work_group IS 'グループ（1-5）';
COMMENT ON COLUMN user_settings.employee_number IS '社員番号';
COMMENT ON COLUMN user_settings.site_regular_hours IS '現場定時時間';
COMMENT ON COLUMN user_settings.display_name IS '氏名';
COMMENT ON COLUMN user_settings.created_at IS '作成日時（タイムゾーン付き）';
COMMENT ON COLUMN user_settings.updated_at IS '更新日時（タイムゾーン付き）';

CREATE INDEX IF NOT EXISTS idx_user_settings_user_name ON user_settings (user_name);
