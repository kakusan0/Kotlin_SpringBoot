-- V9: path_name カラムのNOT NULL制約を削除してnullを許容
ALTER TABLE content_items
    ALTER COLUMN path_name DROP NOT NULL;

