# Flyway マイグレーション追加完了

## 実施内容

### 1. 依存関係の追加（pom.xml）

以下の Flyway 関連の依存関係を追加しました：

```xml

<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
<groupId>org.flywaydb</groupId>
<artifactId>flyway-database-postgresql</artifactId>
<scope>runtime</scope>
</dependency>
```

### 2. Flyway 設定（application.properties）

```properties
# Flyway Migration
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.locations=classpath:db/migration
spring.flyway.validate-on-migrate=true
spring.flyway.out-of-order=false
```

### 3. 新規マイグレーションファイル

**V19__create_user_home_items.sql** を作成しました：

- ユーザー別のホーム表示項目を管理する `user_home_items` テーブル
- username と content_item_id の複合主キー
- 外部キー制約で content_items と連携
- sort_order によるカスタム並び順対応

### 4. ドキュメント

**README-user-home.md** を作成しました：

- ユーザー別ホーム表示の仕組み
- データベース構造
- API エンドポイント
- Flyway の使い方
- 動作確認手順

## 既存のマイグレーション

プロジェクトには既に V1〜V18 のマイグレーションファイルが存在していました：

- V1: 基本テーブル（content_items, menus, paths）
- V9: path_name を NULL 許可に変更
- V10: menus に deleted カラム追加
- V11: IP ホワイトリスト/ブラックリスト
- V12: IP フラグとカウンター
- V14: アクセスログ
- V15: IP インデックスとキャッシュ
- V16: ブラックリストイベント
- V17: UA ブラックリストルール
- V18: ユーザーメニュー

## 動作確認

✅ ビルド成功（`mvnw clean package` 完了）
✅ コンパイルエラーなし
✅ Flyway 設定が有効化されています

## 次回起動時の動作

アプリケーション起動時に：

1. Flyway が自動的に flyway_schema_history テーブルを作成
2. 未適用のマイグレーション（V19）が自動実行される
3. user_home_items テーブルが作成される

## マイグレーションファイルの追加方法

新しいマイグレーションを追加する場合：

1. `src/main/resources/db/migration/` に新規 SQL ファイルを作成
2. 命名規則: `V{次のバージョン番号}__{説明}.sql`
    - 例: `V20__add_user_preferences.sql`
3. アプリケーション再起動で自動適用

## 注意事項

- マイグレーションファイルは**一度適用されたら変更しない**こと
- バージョン番号は連番でなくても OK（V19 の次が V21 でも可）
- 本番環境では必ず事前にバックアップを取ること
- `baseline-on-migrate=true` により既存 DB でも安全に適用可能

## トラブルシューティング

### マイグレーション失敗時

```bash
# Flyway の状態確認
mvn flyway:info

# 失敗したマイグレーションを修正
mvn flyway:repair

# 手動でマイグレーション実行
mvn flyway:migrate
```

### ベースラインの設定

既存の本番環境に適用する場合：

```properties
spring.flyway.baseline-version=18
spring.flyway.baseline-description=Existing schema
```

これで既存テーブルを V18 として認識し、V19 以降のみ適用されます。

