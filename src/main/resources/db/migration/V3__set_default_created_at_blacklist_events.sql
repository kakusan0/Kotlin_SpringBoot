-- V3__set_default_created_at_blacklist_events.sql
-- 目的: blacklist_events.created_at カラムに DEFAULT CURRENT_TIMESTAMP を付与し、既に NULL の行を現在時刻で埋める
-- 実行環境: PostgreSQL (Flyway を利用しているプロジェクトの db/migration 配下)
-- 注意: 大規模テーブルでは一括 UPDATE が長時間ロックを引き起こす可能性があるため、バッチ更新の方法もコメントで示します。

BEGIN;

-- 既存の NULL を埋める (小〜中規模テーブル向け: 単一 UPDATE)
UPDATE blacklist_events
SET
    created_at = CURRENT_TIMESTAMP
WHERE
    created_at IS NULL;

-- 注意: テーブルが非常に大きい場合は以下のバッチ更新スニペットを代わりに使ってください（コメントアウト）。
-- DO $$
-- DECLARE
--   batch_size INT := 10000;
--   updated_rows INT := 0;
-- BEGIN
--   LOOP
--     WITH c AS (
--       SELECT ctid FROM blacklist_events WHERE created_at IS NULL LIMIT batch_size
--     )
--     UPDATE blacklist_events b
--     SET created_at = CURRENT_TIMESTAMP
--     FROM c
--     WHERE b.ctid = c.ctid
--     RETURNING 1
--     INTO updated_rows;
--     IF NOT FOUND THEN
--       EXIT;
--     END IF;
--     -- 小休止（必要に応じて）
--     PERFORM pg_sleep(0.1);
--   END LOOP;
-- END$$;

-- カラムのデフォルトを CURRENT_TIMESTAMP に設定
ALTER TABLE blacklist_events
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

COMMIT;

