-- Add missing columns for timesheet feature
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS break_minutes INTEGER;
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER;
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS working_minutes INTEGER;

-- Backfill existing rows (optional: leave nulls)
UPDATE timesheet_entries
SET
    break_minutes = COALESCE(break_minutes, 0)
WHERE
    break_minutes IS NULL;

