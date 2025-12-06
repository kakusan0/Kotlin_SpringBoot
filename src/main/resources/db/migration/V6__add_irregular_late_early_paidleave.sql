-- Add columns for irregular work, late arrival, early departure, and paid leave
ALTER TABLE timesheet_entries
    ADD COLUMN IF NOT EXISTS irregular_work_type VARCHAR (20) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS irregular_work_desc VARCHAR (255) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS late_time VARCHAR (10) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS late_desc VARCHAR (255) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS early_time VARCHAR (10) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS early_desc VARCHAR (255) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS paid_leave VARCHAR (10) DEFAULT NULL;

