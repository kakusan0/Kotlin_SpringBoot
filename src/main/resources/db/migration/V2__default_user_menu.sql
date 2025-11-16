-- デフォルトユーザー（未ログイン時）のメニュー設定
-- 管理者が "default" ユーザーにメニューを割り当てることで、未ログイン時の表示を制御できる

-- デフォルトユーザー: メニューID 1 と 2 を未ログイン時のデフォルトとして設定
INSERT INTO
    user_menu (username, menu_id)
SELECT
    'default'
    , 1 WHERE EXISTS (SELECT 1 FROM menus WHERE id = 1)
ON CONFLICT (username, menu_id) DO NOTHING;

INSERT INTO
    user_menu (username, menu_id)
SELECT
    'default'
    , 2 WHERE EXISTS (SELECT 1 FROM menus WHERE id = 2)
ON CONFLICT (username, menu_id) DO NOTHING;

-- 管理者ユーザー: 全メニューにアクセス可能（例: メニューID 1, 2, 3）
INSERT INTO
    user_menu (username, menu_id)
SELECT
    'admin'
    , id
FROM
    menus
WHERE
    id IN (1, 2, 3) ON CONFLICT (username, menu_id) DO NOTHING;

-- 一般ユーザー1: メニューID 1 のみ
INSERT INTO
    user_menu (username, menu_id)
SELECT
    'user1'
    , 1 WHERE EXISTS (SELECT 1 FROM menus WHERE id = 1)
ON CONFLICT (username, menu_id) DO NOTHING;

-- 一般ユーザー2: メニューID 2 のみ
INSERT INTO
    user_menu (username, menu_id)
SELECT
    'user2'
    , 2 WHERE EXISTS (SELECT 1 FROM menus WHERE id = 2)
ON CONFLICT (username, menu_id) DO NOTHING;

