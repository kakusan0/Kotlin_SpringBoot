-- content_items に username カラムを追加（ユーザー単位の画面登録対応）
ALTER TABLE content_items
    ADD COLUMN IF NOT EXISTS username VARCHAR (255);

-- 検索性能向上のための複合インデックス（メニュー名 + ユーザー名）
CREATE INDEX IF NOT EXISTS idx_content_items_menu_user ON content_items (menu_name, username);

