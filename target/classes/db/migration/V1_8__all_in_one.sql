-- V1__create_content_items.sql
DROP TABLE IF EXISTS content_items;
CREATE TABLE content_items (
  id BIGSERIAL PRIMARY KEY, -- 一意のID
  item_name VARCHAR(255) NOT NULL, -- アイテム名
  menu_name VARCHAR(255) NOT NULL DEFAULT '', -- メニュー名
  path_name VARCHAR(255) DEFAULT '', -- パス名
  created_at TIMESTAMP DEFAULT (now() AT TIME ZONE 'UTC'), -- 作成日時
  updated_at TIMESTAMP DEFAULT (now() AT TIME ZONE 'UTC'), -- 更新日時
  enabled BOOLEAN NOT NULL DEFAULT TRUE -- 有効フラグ
);
CREATE INDEX idx_item_name ON content_items(item_name);
CREATE INDEX IF NOT EXISTS idx_menu_name ON content_items(menu_name);
CREATE INDEX IF NOT EXISTS idx_path_name ON content_items(path_name);
CREATE OR REPLACE FUNCTION pg_trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER set_updated_at
BEFORE UPDATE ON content_items
FOR EACH ROW
EXECUTE FUNCTION pg_trigger_set_updated_at();

-- V5__create_menus.sql
CREATE TABLE IF NOT EXISTS menus (
  id BIGSERIAL PRIMARY KEY, -- 一意のID
  name VARCHAR(255) NOT NULL, -- メニュー名
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(), -- 作成日時
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() -- 更新日時
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_menu_name_unique ON menus(name);
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger t
    JOIN pg_class c ON t.tgrelid = c.oid
    WHERE t.tgname = 'set_updated_at_menus' AND c.relname = 'menus'
  ) THEN
    EXECUTE $trg$CREATE TRIGGER set_updated_at_menus
      BEFORE UPDATE ON menus
      FOR EACH ROW
      EXECUTE FUNCTION pg_trigger_set_updated_at();$trg$;
  END IF;
END
$$;

-- V7__create_paths.sql
CREATE TABLE IF NOT EXISTS paths (
  id BIGSERIAL PRIMARY KEY, -- 一意のID
  name VARCHAR(100) NOT NULL, -- パス名
  deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 論理削除フラグ
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(), -- 作成日時
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now() -- 更新日時
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_paths_name ON paths(name);
CREATE INDEX IF NOT EXISTS ix_paths_deleted ON paths(deleted);
CREATE OR REPLACE FUNCTION pg_trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS set_updated_at_paths ON paths;
CREATE TRIGGER set_updated_at_paths
BEFORE UPDATE ON paths
FOR EACH ROW
EXECUTE FUNCTION pg_trigger_set_updated_at();

-- V8__fix_null_menu_names.sql
