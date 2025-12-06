-- V8: late_time, early_timeカラムの長さ拡張
ALTER TABLE timesheet_entries
ALTER
COLUMN late_time TYPE VARCHAR(50),
    ALTER
COLUMN early_time TYPE VARCHAR(50);

