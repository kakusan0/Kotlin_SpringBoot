-- Prevent duplicate inserts into spring_session_attributes by silently ignoring
-- when an attribute with same (session_primary_id, attribute_name) already exists.
-- This avoids duplicate-key exceptions caused by concurrent session saves.

-- Drop existing trigger/function if present (idempotent)
DROP TRIGGER IF EXISTS trg_ignore_duplicate_session_attribute ON spring_session_attributes;
DROP FUNCTION IF EXISTS fn_ignore_duplicate_session_attribute();

CREATE FUNCTION fn_ignore_duplicate_session_attribute()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    -- If the attribute row already exists, skip insert to avoid duplicate key error
    IF
EXISTS(
        SELECT 1 FROM spring_session_attributes
        WHERE session_primary_id = NEW.session_primary_id
          AND attribute_name = NEW.attribute_name
    ) THEN
        -- Do nothing: returning NULL in BEFORE trigger skips the INSERT
        RETURN NULL;
END IF;
RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ignore_duplicate_session_attribute
    BEFORE INSERT
    ON spring_session_attributes
    FOR EACH ROW
    EXECUTE FUNCTION fn_ignore_duplicate_session_attribute();

