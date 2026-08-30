# 勤怠管理アプリケーション設計書

## 1. 文書概要

### 1.1 目的

本書は、本アプリケーションの構成、責務分担、共通処理、勤務表保存仕様および運用方法を定義する。
実装と設計の差異が発生した場合は、実装変更に合わせて本書を更新する。

### 1.2 対象範囲

- 勤怠情報の登録、更新、照会
- カレンダー休日管理
- XLSX/PDF勤務表の出力
- パスキー認証、セッションおよびアクセス制御
- IP/UAブラックリスト管理
- 共通例外処理、実行ログ、設定管理
- MavenおよびDockerによるビルド・起動

## 2. システム構成

```text
Browser
  |
  | HTTP/JSON, Server-Sent Events
  v
Spring Boot Application
  |- Controller       HTTP入出力、認証ユーザーの取得
  |- Service          業務処理、トランザクション境界
  |- Mapper           MyBatisによるDBアクセス
  |- Config/Aspect    共通例外、ログ、セキュリティ、設定
  |
  +-- PostgreSQL     業務データ、ユーザー設定、監査情報
  +-- Redis           Spring Sessionのセッション格納
  +-- Vault           秘密情報の外部管理
```

### 2.1 技術要素

| 区分 | 採用技術 |
| --- | --- |
| 言語 | Java 21 |
| Web | Spring Boot 4.0.8 / Spring MVC |
| 認証 | Spring Security / WebAuthn |
| DBアクセス | MyBatis |
| DB | PostgreSQL |
| セッション | Spring Session Redis |
| 帳票 | Apache POI / PDFBox |
| ログ | Log4j2 / SLF4J |
| AOP | Spring AOP / AspectJ Weaver |
| 配布 | Docker Compose |

## 3. レイヤー設計

### 3.1 Controller

ControllerはHTTPリクエストの受付とレスポンス変換のみを担当する。業務ルール、DBアクセスおよび個別の例外レスポンス生成はControllerに記述しない。

主なControllerは以下のとおり。

| クラス | 主な責務 |
| --- | --- |
| `TimesheetController` | 勤怠、休憩、備考、月次データ、集計 |
| `TimesheetReportController` | XLSX/PDF出力、帳票ジョブ |
| `CalendarHolidayController` | 休日の照会、登録、削除 |
| `WebAuthnController` | パスキー登録、認証、資格情報管理 |
| `ApiIpController` | IPホワイトリスト/ブラックリスト |
| `ApiUaBlacklistController` | UAブラックリスト |
| `UserSettingsController` | ユーザー設定 |

### 3.2 Service

Serviceは業務ルールとトランザクションを担当する。Controllerから呼び出されるServiceの公開メソッドは、実行ログAOPの対象となる。

### 3.3 Mapper

MapperおよびMapper XMLはデータベース入出力に限定する。業務判断はServiceで行い、SQLに業務ルールを分散させない。

## 4. 共通処理設計

### 4.1 グローバル例外処理

`GlobalExceptionHandler`を`@RestControllerAdvice`として配置し、Controllerごとの重複したtry-catchおよびエラーレスポンス生成を廃止する。

| 例外 | HTTPステータス | 用途 |
| --- | ---: | --- |
| `MethodArgumentNotValidException` | 400 | `@Valid`入力エラー |
| `ConstraintViolationException` | 400 | 制約違反 |
| `MethodArgumentTypeMismatchException` | 400 | パラメータ型不一致 |
| `IllegalArgumentException` | 400 | 不正な入力 |
| `DateTimeParseException` | 400 | 日付形式不正 |
| `TimesheetValidationException` | 400 | 勤怠業務ルール違反 |
| `TimesheetConflictException` | 409 | 勤怠更新競合 |
| `TimesheetNotFoundException` | 404 | 勤怠データ未検出 |
| WebAuthn認証処理の例外 | 400/401 | 登録/認証失敗 |
| その他の予期しない例外 | 500 | 内部エラー。詳細はレスポンスに返さない |

APIエラーは以下の形式で返す。

```json
{
  "message": "入力値に問題があります",
  "errors": {
    "fieldName": "エラーメッセージ"
  }
}
```

### 4.2 実行ログAOP

`ExecutionLoggingAspect`が`com.example.demo.controller`および`com.example.demo.service`配下のpublicメソッドを横断的に記録する。

- 開始時: クラス名、メソッド名、引数概要
- 成功時: 処理時間、戻り値概要
- 失敗時: 処理時間、例外、スタックトレース
- 大きなコレクションは件数のみ記録する
- 引数文字列は最大80文字に制限する
- パスワード、秘密鍵、認証情報などの機密値をログに出力しない

個別クラスへの`@Slf4j`は、業務上固有のログが必要な場合を除いて追加しない。例外処理や実行時間の共通ログはAOPおよびAdviceに集約する。

### 4.3 設定管理

`@Value`の散在を避け、`@ConfigurationProperties`で設定を型付き管理する。

| クラス | prefix | 内容 |
| --- | --- | --- |
| `AppProperties` | `app` | 暗号化、CSP、ログイン制限、帳票ディレクトリ |
| `GeoIpProperties` | `geoip` | MMDBパス、許可国コード |
| `WebAuthnProperties` | `webauthn.rp` | RP ID、表示名、Origin |
| `ReportProperties` | `report` | 帳票テンプレート、休日表示位置 |

## 5. 勤怠・備考保存仕様

### 5.1 備考値

備考列は選択値をそのまま保存する。選択解除や`---`選択を「未変更」と解釈しない。

| UI値 | 保存値 |
| --- | --- |
| `---` | 空文字または画面仕様で定義された空値 |
| `午前休` | `午前休` |
| `午後休` | `午後休` |
| その他の備考 | 選択された文字列 |

フロントエンドは、備考selectの現在値を常にリクエストへ含める。空文字も明示的な更新値として送信し、サーバー側で既存値を再利用しない。

### 5.2 変則勤務クリア

変則勤務を全クリアした場合、関連する備考が`午前休`または`午後休`であればクリアする。

- 祝日の場合: `祝日`を維持する
- 休日の場合: `休日`を維持する
- 上記以外: 備考を空にする
- 変則勤務以外の通常勤怠値は、クリア操作の対象範囲に含めない

### 5.3 保存フロー

```text
画面操作
  -> timesheetMonth.jsが全入力値を収集
  -> 備考値を含むJSONをAPIへ送信
  -> TimesheetController
  -> TimesheetServiceで検証・競合確認
  -> TimesheetEntryMapperで登録/更新
  -> 更新結果を画面へ反映
```

## 6. キャッシュ方針

業務データの古い値を表示・出力することを防ぐため、Spring Cacheおよび業務集計キャッシュは使用しない。

- `@EnableCaching`を使用しない
- `spring-boot-starter-cache`を依存しない
- 休日および勤怠集計は必要時にDBから取得する
- フロントエンドの休日情報も保持せず、画面更新時に最新情報を取得する
- 静的リソースおよびThymeleafのキャッシュ設定を追加しない

ただし、以下は業務データキャッシュではなく、セキュリティ制御のため維持する。

- ログイン/IP/UAのレート制限用TTLマップ
- WebAuthnチャレンジの一時保持
- UAブラックリストの短時間スナップショット

これらは有効期限、用途および削除条件を明示し、業務画面の表示データに利用しない。

## 7. セキュリティ設計

- Spring Securityで認証・認可を一元管理する
- WebAuthnチャレンジは一時値として扱い、認証完了後に削除する
- セッションはRedisへ保存する
- ログイン、IPおよびUA制御にはレート制限を適用する
- 例外レスポンスにはスタックトレースや秘密情報を含めない
- 信頼プロキシ設定は`app.trust-proxy`で明示する

## 8. フロントエンド資産

JavaScriptはMavenビルド時にminify・難読化し、`frontend/js`のソースから`src/main/resources/static/js`へ生成する。

```text
frontend/js/*.js
  -> frontend/minify.js
  -> minify -> 難読化 -> minify
  -> src/main/resources/static/js/*.js
```

本番配布資産の容量を削減し、ブラウザからのソース解析を困難にする。デバッグ時は`frontend/js`のソースを使用する。

休日情報はブラウザ内にキャッシュせず、表示対象の再構築・勤怠データ読み込み時に取得する。これにより管理画面で休日を変更した後も、古い休日情報を表示し続けない。

## 9. 帳票設計

- 帳票テンプレート名は`report.template`で管理する
- 現行テンプレートは`2026年5月度UNISS勤務表(6桁社員番号＋氏名).xlsx`
- テンプレートをJavaソースにハードコードしない
- 休日表示位置は`report.holiday-position`で設定する
- 帳票生成時の業務データはDBから最新値を取得する

## 10. ビルド・デプロイ

### 10.1 ローカルビルド

```bash
JAVA_HOME=/Users/akihirokakutani/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" ./mvnw test package
```

### 10.2 Docker起動

```bash
docker compose up -d --build
curl http://localhost:8080/actuator/health
```

アプリケーションコンテナはJava 21 JREで実行し、ActuatorのヘルスエンドポイントがHTTP 200を返すことを起動確認条件とする。

### 10.3 主要な依存関係方針

- Spring Bootは4.0系の最新パッチを適用する
- PostgreSQL JDBCは脆弱性修正版を明示指定する
- 本番実行に不要なDevToolsは依存しない
- 依存関係更新後はMavenテスト、依存関係ツリーおよびDocker起動を確認する

## 11. テスト観点

### 11.0 テスト方式

- Controllerは`@SpringBootTest`と`@AutoConfigureMockMvc`を使用する
- Controllerの依存Serviceはテスト用ConfigurationでMockitoモックへ差し替える
- Serviceは`@ExtendWith(MockitoExtension.class)`、`@Mock`および`@InjectMocks`を使用する
- DB、Redis、Vaultなどの外部サービスはService単体テストでは接続しない
- ControllerのSpring BootテストではHTTPステータス、JSONレスポンス、Advice連携を検証する

現在のテスト構成:

| テストクラス | 方式 | 主な検証内容 |
| --- | --- | --- |
| `CalendarHolidayControllerTest` | Spring Boot + MockMvc | 休日API、入力例外、削除404 |
| `TimesheetControllerTest` | Spring Boot + MockMvc | 備考空値、変則勤務クリアフラグ、日付エラー |
| `CalendarHolidayServiceTest` | Mockito | 休日検索、登録、範囲チェック |
| `TimesheetServiceTest` | Mockito | 備考および変則勤務情報のクリア保存 |
| `TimesheetSummaryServiceTest` | Mockito | 月次集計 |
| `TimesheetEvaluatorTest` | 単体テスト | 日跨ぎ勤務、休憩入力の評価 |
| `AdditionalServiceMethodsTest` | Mockito | ブラックリスト、WebAuthn一時値、GeoIP、帳票補助 |

現在のテスト実績は23件。未実装の公開メソッドについては、Aipo操作、帳票生成、WebAuthn検証、その他Controller APIの正常系・異常系を順次追加する。

### 11.1 API

- バリデーションエラーがHTTP 400になること
- 未検出がHTTP 404、競合がHTTP 409になること
- 予期しない例外で機密情報がレスポンスに含まれないこと

### 11.2 勤怠・備考

- `---`を選択して保存すると、以前の備考に戻らないこと
- `午前休`、`午後休`を保存・再表示できること
- 変則勤務の全クリア時に対象の休暇備考がクリアされること
- 祝日、休日の表示値が意図せず空にならないこと

### 11.3 運用

- AOPログにパスワード、認証情報および暗号鍵が出力されないこと
- Docker再ビルド後に全コンテナが起動すること
- ActuatorヘルスがUPになること
- JavaScriptが難読化されず、ソースと配布先が一致すること
