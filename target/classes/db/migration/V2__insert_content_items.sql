-- V2__insert_content_items.sql
-- Insert initial content items
INSERT INTO content_items (item_name, menu_name, path_name, enabled)
VALUES
  ('ホーム', 'ホーム', 'home', TRUE),
  ('パスワード生成', 'ツール', 'pwgen', TRUE),
  ('設定', '設定', 'settings', TRUE);
