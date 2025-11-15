-- ユーザーメニュー関連テーブル
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

CREATE INDEX IF NOT EXISTS idx_user_menu_username ON user_menu(username);
CREATE INDEX IF NOT EXISTS idx_user_menu_menu_id ON user_menu(menu_id);

-- サンプルデータ: 一般ユーザー 'user' にメニューを割り当て
-- メニューIDは実際のデータベースに合わせて調整が必要
-- 例: INSERT INTO user_menu (username, menu_id) VALUES ('user', 1);
-- 例: INSERT INTO user_menu (username, menu_id) VALUES ('user', 2);

