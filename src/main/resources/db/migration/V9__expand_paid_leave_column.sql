-- V9: paid_leaveカラムの長さ拡張
ALTER TABLE timesheet_entries
ALTER
COLUMN paid_leave TYPE VARCHAR(50);

