-- 暗号化済み文字列を格納できるよう user_settings の列長を拡張
ALTER TABLE IF EXISTS user_settings
    ALTER COLUMN employee_number TYPE TEXT,
    ALTER COLUMN display_name TYPE TEXT;

COMMENT ON COLUMN user_settings.employee_number IS '社員番号（暗号化して保存）';
COMMENT ON COLUMN user_settings.display_name IS '氏名（暗号化して保存）';

