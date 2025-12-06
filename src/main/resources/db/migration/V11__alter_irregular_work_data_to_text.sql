-- V10: irregular_work_dataカラムをtext型に変更
ALTER TABLE timesheet_entries
ALTER
COLUMN paid_leave TYPE TEXT;

COMMENT
ON COLUMN timesheet_entries.irregular_work_data IS '複数の変則勤務データ (JSON形式, text型)';

