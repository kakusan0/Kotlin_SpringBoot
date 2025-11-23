-- Optimization & monthly view (requires columns added in previous migration)
DO
$$
BEGIN
    IF
NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_timesheet_user_date'
    ) THEN
ALTER TABLE timesheet_entries
    ADD CONSTRAINT uq_timesheet_user_date UNIQUE (user_name, work_date);
END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_timesheet_user_date ON timesheet_entries(user_name, work_date);
CREATE INDEX IF NOT EXISTS idx_timesheet_work_date ON timesheet_entries(work_date);

-- Create monthly aggregation view after new columns exist
CREATE
OR REPLACE VIEW timesheet_monthly_view AS
SELECT
    user_name
    , date_trunc('month', work_date)::date AS month_first, SUM(COALESCE(working_minutes, 0)) total_working_minutes
    , SUM(COALESCE(break_minutes, 0)) total_break_minutes
    , COUNT(*)                        total_days
FROM
    timesheet_entries
GROUP BY
    user_name, date_trunc('month', work_date);
