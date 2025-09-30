-- Add path_name column to content_items
ALTER TABLE content_items ADD COLUMN IF NOT EXISTS path_name VARCHAR(255) DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_path_name ON content_items(path_name);

