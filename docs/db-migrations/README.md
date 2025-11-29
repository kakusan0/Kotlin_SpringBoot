# DB Migration: set_default_created_at_blacklist_events

このドキュメントは `V3__set_default_created_at_blacklist_events.sql` についての説明、実行手順、ロールバック案、テスト手順、注意点を記載します。

目的

- `blacklist_events.created_at` カラムに `DEFAULT CURRENT_TIMESTAMP` を付与し、既存の NULL 値を現在時刻で埋めることで、INSERT
  時の NOT NULL 制約エラーを回避する。

実行ファイル

- `src/main/resources/db/migration/V3__set_default_created_at_blacklist_events.sql`

実行前チェック

1. 本番で実行する場合は必ず事前にバックアップ（スナップショット/pg_dump）を取得すること。
2. テーブルの行数を確認し、大規模（数百万行）であればバッチ更新スニペットを使用すること。
    - `SELECT COUNT(*) FROM blacklist_events WHERE created_at IS NULL;`
3. 他のトランザクションが長時間ロックを保持していないか確認する（`pg_stat_activity`）。

実行（Flyway 使用時）

- 開発環境 / CI:

```bash
mvn -DskipTests=true flyway:migrate
```

- またはアプリ起動時に `spring.flyway.enabled=true` の場合、アプリケーション起動で自動的にマイグレーションが適用されます。

ロールバック案

- アプリケーション側でのロールバックは困難な場合があるため、以下を検討してください。
    - もしミスがある場合、変更前のバックアップから該当テーブルをリストアする。
    - `ALTER TABLE blacklist_events ALTER COLUMN created_at DROP DEFAULT;` を実行してデフォルトを取り消すことは可能（ただし
      NULL の埋め戻しは手動で戻す必要がある）。

ロールバック簡易コマンド例（注意: データは復元されません）

```sql
ALTER TABLE blacklist_events
    ALTER COLUMN created_at DROP DEFAULT;
```

注意点

- テーブルが大きい場合は一括 UPDATE によるロック時間に注意。バッチ更新を推奨。
- `CURRENT_TIMESTAMP` は PostgreSQL のサーバー時刻を使用するため、アプリ側でタイムゾーンを統一している場合は考慮が必要。

テスト手順（開発環境）

1. マイグレーションを適用
    - `mvn -DskipTests=true flyway:migrate`
2. アプリを起動し、ブラックリスト操作を行って `blacklist_events` にレコードが追加され、`created_at` が設定されることを確認
    - `SELECT id, created_at, ip_address, reason, source FROM blacklist_events ORDER BY id DESC LIMIT 5;`
3. 既存 NULL の数が0になっていることを確認
    - `SELECT COUNT(*) FROM blacklist_events WHERE created_at IS NULL;` → 0

運用メモ

- 本番環境での実行は、低負荷時間帯に段階的に行うこと。必要に応じてバッチスクリプトに切り替えて適用する。

問い合わせ

- 追加でバッチ更新スクリプトを自動生成することや、`created_at` をアプリ側で設定するパッチを併せて作成することが可能です。希望があれば指示ください。

