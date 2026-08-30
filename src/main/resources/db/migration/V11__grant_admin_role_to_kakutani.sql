-- 角谷亮洋へ管理者ロールを付与する。
-- UNIQUE(username, authority)により再適用しても重複しない。
INSERT INTO authorities (username, authority)
SELECT '角谷亮洋', 'ROLE_ADMIN'
WHERE EXISTS (
    SELECT 1
    FROM users
    WHERE username = '角谷亮洋'
)
  AND NOT EXISTS (
    SELECT 1
    FROM authorities
    WHERE username = '角谷亮洋'
      AND authority = 'ROLE_ADMIN'
);
