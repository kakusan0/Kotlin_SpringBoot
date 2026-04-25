# Flyway 実行コマンド

このプロジェクトで実際に使う Flyway コマンドをまとめたメモです。

## 前提

- 実行ディレクトリ: プロジェクトルート
- DB 接続設定: `src/main/resources/flyway.conf`
- DB 接続先:
  - URL: `jdbc:postgresql://localhost:5432/demo_db`
  - User: `demo_user`
  - Password: `demo_pass`
- マイグレーション配置: `src/main/resources/db/migration`

`flyway.conf` を使うため、Spring Boot の `application-dev.yml` / Vault 設定に依存せず実行できます。

## 事前準備

Docker の DB が起動していない場合は先に起動します。

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
docker compose up -d db
```

DB の状態確認:

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
docker compose ps
```

## 基本コマンド

### 1. 現在の状態確認

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
./mvnw flyway:info -Dflyway.configFiles=src/main/resources/flyway.conf
```

用途:
- 現在の schema version を見る
- 未適用の migration があるか確認する
- `Success` / `Pending` / `Missing` を確認する

### 2. マイグレーション適用

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
./mvnw flyway:migrate -Dflyway.configFiles=src/main/resources/flyway.conf
```

用途:
- `src/main/resources/db/migration` の未適用 SQL を順番に適用する

### 3. 検証

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
./mvnw flyway:validate -Dflyway.configFiles=src/main/resources/flyway.conf
```

用途:
- DB の `flyway_schema_history` とローカルの migration ファイルの整合性を確認する

### 4. Repair

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
./mvnw flyway:repair -Dflyway.configFiles=src/main/resources/flyway.conf
```

用途:
- `Validate failed` のときに Flyway 履歴を修復する
- チェックサムずれや `Missing migration` の解消時に使う

> 注意: `repair` は履歴テーブルを書き換えるため、原因を把握してから実行してください。

## よく使う実行順

### 通常運用

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
./mvnw flyway:info -Dflyway.configFiles=src/main/resources/flyway.conf
./mvnw flyway:migrate -Dflyway.configFiles=src/main/resources/flyway.conf
./mvnw flyway:info -Dflyway.configFiles=src/main/resources/flyway.conf
```

### Validate failed のとき

```zsh
cd /Users/akihirokakutani/IdeaProjects/Kotlin_SpringBoot
./mvnw flyway:validate -Dflyway.configFiles=src/main/resources/flyway.conf
./mvnw flyway:repair -Dflyway.configFiles=src/main/resources/flyway.conf
./mvnw flyway:info -Dflyway.configFiles=src/main/resources/flyway.conf
```

## 補足

### Docker アプリ起動時との違い

- Docker の `app` コンテナは `SPRING_PROFILES_ACTIVE=prod` で起動する
- アプリ起動時の Flyway は Spring Boot 経由で動く
- このドキュメントのコマンドは Maven から Flyway を直接実行する手順

### ローカル起動時の注意

`application-dev.yml` では `spring.datasource.url=${db.url}` になっており、
IDE からアプリを直接起動すると Vault や環境変数の設定が必要になる場合があります。

一方、以下の Flyway コマンドは `flyway.conf` を使うため、DB が起動していれば単独で実行できます。

```zsh
./mvnw flyway:info -Dflyway.configFiles=src/main/resources/flyway.conf
./mvnw flyway:migrate -Dflyway.configFiles=src/main/resources/flyway.conf
./mvnw flyway:validate -Dflyway.configFiles=src/main/resources/flyway.conf
./mvnw flyway:repair -Dflyway.configFiles=src/main/resources/flyway.conf
```

## 参考

- Flyway 設定: `src/main/resources/flyway.conf`
- Migration SQL: `src/main/resources/db/migration`
- Docker DB 設定: `docker-compose.yml`

