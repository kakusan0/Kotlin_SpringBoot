-- Add menu_name column to content_items
ALTER TABLE content_items ADD COLUMN IF NOT EXISTS menu_name VARCHAR(255) DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_menu_name ON content_items(menu_name);

