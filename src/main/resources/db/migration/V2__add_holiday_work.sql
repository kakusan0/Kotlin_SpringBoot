-- Add holiday_work flag to timesheet_entries
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS holiday_work BOOLEAN NOT NULL DEFAULT FALSE;

-- Ensure start_time / end_time allow NULL (they already do in V1, but keep safe ALTER for DBs that may have NOT NULL)
-- PostgreSQL syntax shown; adjust if using MySQL.
-- ALTER TABLE timesheet_entries ALTER COLUMN start_time DROP NOT NULL;
-- ALTER TABLE timesheet_entries ALTER COLUMN end_time DROP NOT NULL;

