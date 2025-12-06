-- Add work_location column to timesheet_entries (出社区分: '出社', '在宅', NULL)
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS work_location VARCHAR (10) DEFAULT NULL;

