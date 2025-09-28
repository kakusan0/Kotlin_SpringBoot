-- Create menus table
CREATE TABLE IF NOT EXISTS menus (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_menu_name_unique ON menus(name);

-- trigger to keep updated_at in sync
-- Use a safe DO block to avoid failing when the trigger already exists
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger t
    JOIN pg_class c ON t.tgrelid = c.oid
    WHERE t.tgname = 'set_updated_at_menus' AND c.relname = 'menus'
  ) THEN
    EXECUTE $trg$CREATE TRIGGER set_updated_at_menus
      BEFORE UPDATE ON menus
      FOR EACH ROW
      EXECUTE FUNCTION pg_trigger_set_updated_at();$trg$;
  END IF;
END
$$;
