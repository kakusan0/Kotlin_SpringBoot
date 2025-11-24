-- Report jobs table for async report generation
CREATE TABLE IF NOT EXISTS report_jobs
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    username
    VARCHAR
(
    128
) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    format VARCHAR
(
    16
) NOT NULL,
    status VARCHAR
(
    32
) NOT NULL DEFAULT 'PENDING',
    file_path TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW
(
)
    );
CREATE INDEX IF NOT EXISTS idx_report_jobs_username ON report_jobs(username);
CREATE INDEX IF NOT EXISTS idx_report_jobs_status ON report_jobs(status);

