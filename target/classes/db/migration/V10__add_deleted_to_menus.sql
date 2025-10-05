-- V10__add_deleted_to_menus.sql
-- メニューテーブルに論理削除フラグを追加

ALTER TABLE menus ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- 論理削除フラグのインデックスを作成
CREATE INDEX IF NOT EXISTS ix_menus_deleted ON menus(deleted);

COMMENT ON COLUMN menus.deleted IS '論理削除フラグ（TRUE=削除済み、FALSE=有効）';

