# ユーザー別ホーム表示設定

本プロジェクトでは Flyway によるデータベースマイグレーション管理を導入しています。

## ユーザー別ホーム表示の仕組み

`/home` エンドポイントでは、以下のロジックで表示内容を決定します：

### 表示ロジック

1. **管理者（ROLE_ADMIN）**: 常に全体設定（`content_items` テーブルの全データ）を表示
2. **一般ユーザー（認証済み）**: `user_home_items` テーブルに登録された項目を優先表示
3. **フォールバック**: ユーザー個別設定が無い場合や未ログインの場合は全体設定を表示

### データベース構造

#### user_home_items テーブル（V19 マイグレーションで作成）

```sql
CREATE TABLE user_home_items
(
    username        VARCHAR(190)             NOT NULL,
    content_item_id BIGINT                   NOT NULL,
    sort_order      INTEGER                  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username, content_item_id),
    CONSTRAINT fk_uhi_content_item
        FOREIGN KEY (content_item_id)
            REFERENCES content_items (id)
            ON DELETE CASCADE
);
```

### サンプルデータ投入

```sql
-- ユーザー 'user' に対して ID=1,2 の ContentItem を表示
INSERT INTO
    user_home_items (username, content_item_id, sort_order)
VALUES
    ('user', 1, 10)
    , ('user', 2, 20) ON CONFLICT (username, content_item_id) DO NOTHING;
```

### API エンドポイント

- `GET /api/content/mine-for-home`: ログインユーザーに応じたホーム表示項目
    - 未ログイン → 全体設定
    - 管理者 → 全体設定
    - 一般ユーザー → ユーザー個別設定（空なら全体設定）

- `GET /api/content/all-for-home`: 全体設定（従来互換）

### Flyway マイグレーション

マイグレーションファイルは `src/main/resources/db/migration/` に配置されています。

- **命名規則**: `V{バージョン}__{説明}.sql`
    - 例: `V19__create_user_home_items.sql`

- **自動実行**: アプリケーション起動時に未適用のマイグレーションが自動実行されます

- **設定**: `application.properties` で制御
  ```properties
  spring.flyway.enabled=true
  spring.flyway.baseline-on-migrate=true
  spring.flyway.locations=classpath:db/migration
  ```

### テストユーザー

開発環境では以下のユーザーが利用可能です（InMemoryUserDetailsManager）：

- **一般ユーザー**: username=`user`, password=`user`, role=`USER`
- **管理者**: username=`admin`, password=`admin`, role=`ADMIN`

### 動作確認手順

1. アプリケーション起動（Flyway が自動的にマイグレーション実行）
2. 未ログイン状態で `/home` にアクセス → 全体設定が表示される
3. `user/user` でログイン → `user_home_items` に登録があればそれが表示、無ければ全体設定
4. `admin/admin` でログイン → 常に全体設定が表示される

### 今後の拡張

- ユーザー個別設定の管理UI追加
- sort_order による表示順のカスタマイズ
- ユーザーグループによる一括設定

