-- 必須メニュー設定テーブル
CREATE TABLE IF NOT EXISTS menu_settings
(
    menu_id
    BIGINT
    PRIMARY
    KEY,
    required
    BOOLEAN
    NOT
    NULL
    DEFAULT
    FALSE,
    updated_at
    TIMESTAMP
    WITH
    TIME
    ZONE
    DEFAULT
    now
(
)
    );

-- ロール別メニュー割当テーブル
CREATE TABLE IF NOT EXISTS role_menu
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    role_name
    VARCHAR
(
    64
) NOT NULL,
    menu_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    CONSTRAINT uk_role_menu UNIQUE
(
    role_name,
    menu_id
)
    );

CREATE INDEX IF NOT EXISTS idx_role_menu_role ON role_menu (role_name);
CREATE INDEX IF NOT EXISTS idx_role_menu_menu ON role_menu (menu_id);

-- 初期化: 既存メニューの必須は全てFALSE
INSERT INTO
    menu_settings (menu_id, required)
SELECT
    id
    , FALSE
FROM
    menus m ON CONFLICT (menu_id) DO NOTHING;

