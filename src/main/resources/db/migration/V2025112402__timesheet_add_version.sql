-- Add version column for optimistic locking
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_timesheet_user_date ON timesheet_entries(user_name, work_date);
