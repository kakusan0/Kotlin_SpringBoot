-- Flyway migration: convert TIMESTAMPTZ columns to TIMESTAMP WITHOUT TIME ZONE

ALTER TABLE content_items
  ALTER COLUMN created_at TYPE timestamp USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE timestamp USING updated_at AT TIME ZONE 'UTC';

-- Ensure default functions still set the timestamp without time zone
ALTER TABLE content_items
  ALTER COLUMN created_at SET DEFAULT (now() AT TIME ZONE 'UTC'),
  ALTER COLUMN updated_at SET DEFAULT (now() AT TIME ZONE 'UTC');
