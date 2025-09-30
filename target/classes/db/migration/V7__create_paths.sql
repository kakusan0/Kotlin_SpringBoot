-- Create paths master table with logical delete
CREATE TABLE IF NOT EXISTS paths (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_paths_name ON paths(name);
CREATE INDEX IF NOT EXISTS ix_paths_deleted ON paths(deleted);

-- trigger to update updated_at on update
CREATE OR REPLACE FUNCTION pg_trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_updated_at_paths ON paths;
CREATE TRIGGER set_updated_at_paths
BEFORE UPDATE ON paths
FOR EACH ROW
EXECUTE FUNCTION pg_trigger_set_updated_at();

