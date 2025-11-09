# Kotlin Spring Boot Demo Application

Kotlin、Spring Boot、MyBatis、PostgreSQLを使用したWebアプリケーションです。コンテンツ管理システム（CMS）の機能と、高度なセキュリティ機能（IP制限、User-Agent制限、GeoIPフィルタリング）を備えています。

## 目次

- [概要](#概要)
- [主な機能](#主な機能)
- [技術スタック](#技術スタック)
- [必要な環境](#必要な環境)
- [セットアップ手順](#セットアップ手順)
- [アプリケーションの起動](#アプリケーションの起動)
- [設定](#設定)
- [データベース](#データベース)
- [API エンドポイント](#api-エンドポイント)
- [セキュリティ機能](#セキュリティ機能)
- [開発](#開発)

## 概要

このアプリケーションは、Kotlinで開発されたSpring BootベースのWebアプリケーションです。コンテンツアイテムとメニューを管理し、動的にページを表示する機能を提供します。また、IPアドレスベースのアクセス制御、User-Agentブラックリスト、GeoIPによる国別フィルタリングなど、高度なセキュリティ機能を搭載しています。

## 主な機能

### コンテンツ管理
- **コンテンツアイテム管理**: 動的なコンテンツの作成、編集、削除
- **メニュー管理**: カスタマイズ可能なナビゲーションメニュー
- **パス管理**: URLパスとコンテンツの紐付け

### セキュリティ機能
- **IPホワイトリスト**: 許可されたIPアドレスからのみアクセスを許可
- **IPブラックリスト**: 特定のIPアドレスをブロック
- **User-Agentブラックリスト**: 悪意のあるボットやクローラーをブロック
- **GeoIPフィルタリング**: MaxMind GeoLite2を使用した国別アクセス制限
- **レート制限**: Bucket4jを使用したリクエストレート制限
- **アクセスログ**: すべてのアクセスを記録

### その他の機能
- **MyDNS連携**: 定期的なIPアドレス更新（Quartzスケジューラー）
- **セッション管理**: JDBCベースのセッション管理
- **キャッシング**: Spring Cacheによるパフォーマンス最適化
- **管理画面**: コンテンツとセキュリティ設定の管理UI

## 技術スタック

- **言語**: Kotlin 2.0.21
- **フレームワーク**: Spring Boot 3.5.6
- **ビルドツール**: Maven
- **データベース**: PostgreSQL 15
- **ORM**: MyBatis 3.0.5
- **データベースマイグレーション**: Flyway
- **テンプレートエンジン**: Thymeleaf
- **Webサーバー**: Undertow
- **セキュリティ**: Spring Security
- **キャッシュ**: Spring Cache
- **スケジューラー**: Quartz
- **GeoIP**: MaxMind GeoIP2
- **レート制限**: Bucket4j
- **フロントエンド**: Bootstrap 5.3.8, Bootstrap Icons 1.13.1

## 必要な環境

- **Java**: JDK 21以上
- **Maven**: 3.6以上（またはプロジェクトに含まれるMaven Wrapper使用）
- **Docker**: Docker Compose（PostgreSQL用）
- **PostgreSQL**: 15（Dockerで起動可能）

## セットアップ手順

### 1. リポジトリのクローン

```bash
git clone https://github.com/kakusan0/Kotlin_SpringBoot.git
cd Kotlin_SpringBoot
```

### 2. データベースの起動

Docker Composeを使用してPostgreSQLを起動します：

```bash
docker-compose up -d
```

これにより、以下の設定でPostgreSQLが起動します：
- **ホスト**: localhost:5432
- **データベース名**: demo_db
- **ユーザー名**: demo_user
- **パスワード**: demo_pass

### 3. アプリケーションのビルド

```bash
./mvnw clean package
```

テストをスキップしてビルドする場合：

```bash
./mvnw clean package -DskipTests=true
```

## アプリケーションの起動

### 開発モード

```bash
./mvnw spring-boot:run
```

### 本番モード

```bash
java -jar target/app-0.0.1-SNAPSHOT.war
```

アプリケーションは http://localhost:8080 で起動します。

## 設定

主な設定は `src/main/resources/application.properties` で管理されています。

### データベース設定

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/demo_db
spring.datasource.username=demo_user
spring.datasource.password=demo_pass
```

### GeoIP設定

MaxMindのGeoLite2データベースを使用する場合：

```properties
# GeoLite2-Country.mmdbファイルへのパス（空の場合はGeoIP機能無効）
geoip.mmdb-path=/path/to/GeoLite2-Country.mmdb
# 許可する国コード（カンマ区切り）
geoip.allowed-country-codes=JP
```

### MyDNS設定

動的DNS更新を使用する場合：

```properties
mydns.username=your_username
mydns.password=your_password
mydns.ipv4.url=https://ipv4.mydns.jp/login.html
mydns.ipv6.url=https://ipv6.mydns.jp/login.html
```

**注意**: 本番環境では、パスワードなどの機密情報は環境変数で設定することを推奨します。

### プロキシ設定

ロードバランサーやリバースプロキシの背後で動作する場合：

```properties
app.trust-proxy=true
```

## データベース

### マイグレーション

Flywayを使用してデータベーススキーマを自動管理します。マイグレーションファイルは `src/main/resources/db/migration/` にあります。

アプリケーション起動時に自動的にマイグレーションが実行されます。

### 主なテーブル

- **content_items**: コンテンツアイテム
- **menus**: メニュー
- **paths**: URLパス
- **whitelist_ips**: IPホワイトリスト
- **blacklist_ips**: IPブラックリスト
- **ua_blacklist_rules**: User-Agentブラックリスト
- **access_logs**: アクセスログ
- **blacklist_events**: ブラックリストイベント
- **mydns_logs**: MyDNS更新ログ
- **SPRING_SESSION**: セッション管理
- **QRTZ_***: Quartzスケジューラーテーブル

## API エンドポイント

### メインページ

- `GET /` - トップページ
- `GET /content?screenName={name}` - 特定コンテンツの表示

### 管理画面

- `GET /manage` - 管理ページ（メインダッシュボード）
- `GET /manage/ip` - IP管理ページ
- `GET /manage/ua` - User-Agent管理ページ

### API エンドポイント

#### コンテンツ管理
- `GET /api/content` - コンテンツ一覧取得
- `POST /api/content` - コンテンツ作成
- `PUT /api/content` - コンテンツ更新
- `DELETE /api/content/{id}` - コンテンツ削除

#### メニュー管理
- `GET /api/menus` - メニュー一覧取得
- `POST /api/menus` - メニュー作成
- `PUT /api/menus/{id}` - メニュー更新
- `DELETE /api/menus/{id}` - メニュー削除

#### パス管理
- `GET /api/paths` - パス一覧取得
- `POST /api/paths` - パス作成
- `PUT /api/paths/{id}` - パス更新
- `DELETE /api/paths/{id}` - パス削除

#### IP管理
- `GET /api/whitelist` - ホワイトリスト取得
- `POST /api/whitelist` - ホワイトリスト追加
- `DELETE /api/whitelist/{id}` - ホワイトリスト削除
- `GET /api/blacklist` - ブラックリスト取得
- `POST /api/blacklist` - ブラックリスト追加
- `DELETE /api/blacklist/{id}` - ブラックリスト削除

#### User-Agent管理
- `GET /api/ua-blacklist` - UA ブラックリスト取得
- `POST /api/ua-blacklist` - UAブラックリスト追加
- `DELETE /api/ua-blacklist/{id}` - UAブラックリスト削除

### Actuatorエンドポイント

- `GET /actuator/health` - ヘルスチェック
- `GET /actuator/info` - アプリケーション情報

## セキュリティ機能

### 1. IPホワイトリスト

特定のIPアドレスのみアクセスを許可します。管理画面からホワイトリストにIPを追加できます。

### 2. IPブラックリスト

悪意のあるIPアドレスを自動的にブロックします。以下の条件でブラックリストに追加されます：
- 短時間に多数のリクエスト
- 不正なアクセスパターン
- GeoIPフィルタで許可されていない国からのアクセス

### 3. User-Agentブラックリスト

特定のUser-Agentパターン（ボット、クローラー等）をブロックします。

### 4. GeoIPフィルタリング

MaxMind GeoLite2データベースを使用して、特定の国からのアクセスのみを許可できます。

### 5. レート制限

Bucket4jを使用して、IPアドレスごとのリクエストレートを制限します。

### 6. アクセスログ

すべてのアクセスを記録し、セキュリティ分析に利用できます。

## 開発

### プロジェクト構造

```
src/main/kotlin/com/example/demo/
├── config/              # 設定クラス
│   ├── SecurityConfig.kt
│   ├── RateLimitFilter.kt
│   ├── SecurityAuditFilter.kt
│   └── ...
├── controller/          # コントローラー
│   ├── AdminController.kt
│   └── GeoIpDebugController.kt
├── service/             # サービスレイヤー
│   ├── ContentItemService.kt
│   ├── MenuService.kt
│   ├── PathService.kt
│   └── ...
├── mapper/              # MyBatis マッパー
│   ├── ContentItemMapper.kt
│   ├── MenuMapper.kt
│   └── ...
├── model/               # データモデル
│   ├── ContentItem.kt
│   ├── Menu.kt
│   └── ...
├── util/                # ユーティリティ
│   ├── IpUtils.kt
│   └── SecurityUtils.kt
├── constants/           # 定数
│   └── ApplicationConstants.kt
├── job/                 # スケジュールジョブ
│   └── MyDnsUpdateJob.kt
├── MainController.kt    # メインコントローラー
├── AdminController.kt   # 管理画面コントローラー
├── Api*.kt             # API コントローラー
└── DemoApplication.kt   # アプリケーションエントリーポイント
```

### テストの実行

```bash
./mvnw test
```

### コードフォーマット

このプロジェクトはKotlinのコーディング規約に従っています。

### 依存関係の更新

```bash
./mvnw versions:display-dependency-updates
```

## トラブルシューティング

### データベース接続エラー

PostgreSQLが起動していることを確認してください：

```bash
docker-compose ps
```

起動していない場合は：

```bash
docker-compose up -d
```

### ポート競合

ポート8080が既に使用されている場合、`application.properties` で変更できます：

```properties
server.port=8081
```

### Flywayマイグレーションエラー

データベースをリセットする場合：

```bash
docker-compose down -v
docker-compose up -d
```

### GeoIP機能が動作しない

1. MaxMindからGeoLite2-Country.mmdbをダウンロード
2. `application.properties` で正しいパスを設定
3. パスを空にするとGeoIP機能は無効化されます

### メモリ不足エラー

JVMのヒープサイズを増やす：

```bash
java -Xmx2g -jar target/app-0.0.1-SNAPSHOT.war
```

## ライセンス

このプロジェクトは個人使用向けのデモアプリケーションです。

## 貢献

プルリクエストやイシューの報告を歓迎します。

## 連絡先

問題や質問がある場合は、GitHubのイシューを作成してください。
