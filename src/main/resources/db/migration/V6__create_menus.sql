-- Create menus table
CREATE TABLE IF NOT EXISTS menus (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_menu_name_unique ON menus(name);

-- trigger to keep updated_at in sync
CREATE TRIGGER set_updated_at_menus
BEFORE UPDATE ON menus
FOR EACH ROW
EXECUTE FUNCTION pg_trigger_set_updated_at();

