-- Spring Security JDBC（JdbcUserDetailsManager）用スキーマ
-- パスワードは DelegatingPasswordEncoder 形式（{bcrypt}...）

CREATE TABLE users
(
    username VARCHAR(128) NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled  BOOLEAN     NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE users IS 'フォームログイン用ユーザー（Spring Security）';
COMMENT ON COLUMN users.username IS 'ログインID';
COMMENT ON COLUMN users.password IS 'エンコード済みパスワード（例: {bcrypt}$2a$...）';
COMMENT ON COLUMN users.enabled IS '無効化すると認証不可';

CREATE TABLE authorities
(
    username  VARCHAR(128) NOT NULL,
    authority VARCHAR(128) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users (username) ON DELETE CASCADE
);

COMMENT ON TABLE authorities IS 'ユーザーに付与するロール（ROLE_ 接頭辞）';
COMMENT ON COLUMN authorities.username IS 'users.username への参照';
COMMENT ON COLUMN authorities.authority IS 'GrantedAuthority 文字列（例: ROLE_USER）';

CREATE UNIQUE INDEX ix_authorities_username_authority ON authorities (username, authority);

-- 従来 InMemory と同等の初期アカウント（初回マイグレーション時のみ投入）
-- 角谷亮洋 / 角谷亮洋 ・ admin / Admin@2026!Secure
INSERT INTO users (username, password, enabled)
VALUES ('角谷亮洋', '{bcrypt}$2b$10$fDeFIALW.IRupBg6xbi6eOMcwZEIcROf.D3TAbzc96Cv2XsFs9sty', TRUE),
       ('admin', '{bcrypt}$2b$10$6Xug4pgkDE7ptZsLocTc3.A2ne8.3KUM71qKuz4blLhEwGVlCkwLy', TRUE);

INSERT INTO authorities (username, authority)
VALUES ('角谷亮洋', 'ROLE_USER'),
       ('角谷亮洋', 'ROLE_UNISS'),
       ('admin', 'ROLE_ADMIN');
