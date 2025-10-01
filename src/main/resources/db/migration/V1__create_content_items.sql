-- Flyway migration: create content_items table for PostgreSQL

DROP TABLE IF EXISTS content_items;

CREATE TABLE content_items (
  id BIGSERIAL PRIMARY KEY,
  item_name VARCHAR(255) NOT NULL,
  menu_name VARCHAR(255) NOT NULL,
  path_name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_item_name ON content_items(item_name);

-- Create trigger function to set updated_at on row modification
CREATE OR REPLACE FUNCTION pg_trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at
BEFORE UPDATE ON content_items
FOR EACH ROW
EXECUTE FUNCTION pg_trigger_set_updated_at();