-- ============================================
-- V5__add_free_note_to_timesheet_entries.sql
-- 自由備考（free_note）カラムをtimesheet_entriesテーブルに追加
-- ============================================

-- テーブルに free_note カラムを追加（既存データ対応）
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS free_note TEXT DEFAULT NULL;

-- カラムのコメント
COMMENT ON COLUMN timesheet_entries.free_note IS '自由入力の備考';

