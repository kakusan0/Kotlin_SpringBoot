# 環境別ビルド・実行ガイド

## 概要
このプロジェクトは Maven プロファイルを使用して、開発環境（dev）と商用環境（prod）を分離しています。

## Maven プロファイル

### 開発環境 (dev) - デフォルト
- Spring Profile: `dev`
- テスト実行: **有効**
- データベース: localhost:5432
- サーバーポート: 8080 (HTTP)
- ログレベル: DEBUG
- Thymeleaf キャッシュ: 無効
- GeoIP フィルタ: 無効
- DevTools: 有効

### 商用環境 (prod)
- Spring Profile: `prod`
- テスト実行: **スキップ**
- データベース: 環境変数から取得
- サーバーポート: 8443 (HTTPS)
- ログレベル: WARN/INFO
- Thymeleaf キャッシュ: 有効
- GeoIP フィルタ: 有効（JP のみ許可）
- DevTools: 無効

## ビルドコマンド

### 開発環境でビルド（デフォルト）
```cmd
:: テスト実行してビルド
mvnw.cmd clean package

:: または明示的に dev プロファイルを指定
mvnw.cmd clean package -Pdev
```

### 商用環境でビルド（テストスキップ）
```cmd
:: prod プロファイルを使用（テストは自動的にスキップされる）
mvnw.cmd clean package -Pprod

:: または手動でテストをスキップ
mvnw.cmd clean package -DskipTests=true
```

## 実行コマンド

### 開発環境で実行
```cmd
:: Maven から直接実行
mvnw.cmd spring-boot:run

:: または明示的に dev プロファイルを指定
mvnw.cmd spring-boot:run -Pdev

:: JAR/WAR ファイルから実行
java -jar target/app-0.0.1-SNAPSHOT.war
```

### 商用環境で実行
```cmd
:: Maven から実行（prod プロファイル）
mvnw.cmd spring-boot:run -Pprod

:: JAR/WAR ファイルから実行（Spring Profile を指定）
java -jar target/app-0.0.1-SNAPSHOT.war --spring.profiles.active=prod

:: Windows Server 環境変数を使用（推奨）
:: 事前に setup-windows-env.bat で環境変数を設定
setup-windows-env.bat

:: その後、アプリケーション起動
java -jar target/app-0.0.1-SNAPSHOT.war --spring.profiles.active=prod

:: Windows Service として起動（推奨）
nssm start DemoApp
```

**注意**: 商用環境では Windows Server のシステム環境変数から設定を読み込みます。
詳細は [WINDOWS_ENV_SETUP.md](WINDOWS_ENV_SETUP.md) を参照してください。

## Docker での実行

### 開発環境
```cmd
docker-compose up -d
mvnw.cmd spring-boot:run -Pdev
```

### 商用環境（Windows Server）
商用環境では Docker ではなく、Windows Service として実行することを推奨します。

1. **環境変数設定**
   ```cmd
   setup-windows-env.bat
   ```

2. **アプリケーションビルド**
   ```cmd
   mvnw.cmd clean package -Pprod
   ```

3. **Windows Service 登録**
   ```cmd
   nssm install DemoApp "C:\Program Files\Java\jdk-21\bin\java.exe" ^
     -jar "C:\apps\demo\app-0.0.1-SNAPSHOT.war" ^
     --spring.profiles.active=prod
   ```

4. **サービス起動**
   ```cmd
   nssm start DemoApp
   ```

詳細は以下のドキュメントを参照:
- [WINDOWS_ENV_SETUP.md](WINDOWS_ENV_SETUP.md) - 環境変数設定
- [WINDOWS_SERVICE_SETUP.md](WINDOWS_SERVICE_SETUP.md) - Windows Service 登録

## 設定ファイルの優先順位

1. `application.properties` - 共通設定（ベース）
2. `application-dev.properties` - 開発環境固有の設定
3. `application-prod.properties` - 商用環境固有の設定
4. 環境変数 - 実行時にオーバーライド
5. コマンドライン引数 - 最優先

## テストの制御

### テスト実行
```cmd
:: 開発環境（テスト実行）
mvnw.cmd test

:: または
mvnw.cmd clean package -Pdev
```

### テストスキップ
```cmd
:: 方法1: prod プロファイル使用
mvnw.cmd clean package -Pprod

:: 方法2: skipTests フラグ使用
mvnw.cmd clean package -DskipTests=true

:: 方法3: テストのコンパイルもスキップ
mvnw.cmd clean package -Dmaven.test.skip=true
```

## IDE での実行

### IntelliJ IDEA
1. Run/Debug Configurations を開く
2. Spring Boot アプリケーション設定を作成
3. Active profiles に `dev` または `prod` を指定
4. 実行

### Eclipse
1. Run Configurations を開く
2. Spring Boot App 設定を作成
3. Profile タブで `dev` または `prod` を指定
4. 実行

## トラブルシューティング

### プロファイルが適用されない場合
```cmd
:: ビルド時に -X フラグで詳細ログを確認
mvnw.cmd clean package -Pprod -X

:: アクティブプロファイルを確認
mvnw.cmd help:active-profiles
```

### 環境変数が読み込まれない場合
- `application-prod.properties` で `${ENV_VAR:default_value}` 形式を使用
- 実行時に `-D` オプションで直接指定: `java -jar app.war -Dserver.port=9090`

## セキュリティ注意事項

### 商用環境では必ず以下を設定してください:
- Windows Server のシステム環境変数で機密情報を管理
- データベース認証情報を環境変数で管理（setup-windows-env.ps1 を使用）
- SSL/TLS 証明書の設定
- GeoIP フィルタリングの有効化
- ログレベルを INFO 以下に設定
- セッションクッキーの secure フラグを有効化
- Windows Service として専用ユーザーで実行

詳細は [WINDOWS_ENV_SETUP.md](WINDOWS_ENV_SETUP.md) を参照してください。

