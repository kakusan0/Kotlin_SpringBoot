-- V7: 複数の変則勤務を管理するirregular_work_dataカラムを追加
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS irregular_work_data TEXT;

COMMENT
ON COLUMN timesheet_entries.irregular_work_data IS '複数の変則勤務データ (JSON形式)';

