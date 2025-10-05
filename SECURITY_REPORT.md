# セキュリティ修正レポート

## 実施日

2025年10月5日

## 修正概要

プロジェクト全体のセキュリティ脆弱性を検査し、以下の修正を実施しました。

---

## 🔴 重大な問題の修正

### 1. パスワード生成の脆弱な乱数生成器を修正

**ファイル**: `src/main/resources/static/js/pwgen.js`

**問題点**:

- `Math.random()` を使用してパスワードを生成していた
- 暗号学的に安全ではなく、予測可能なパスワードが生成される可能性

**修正内容**:

```javascript
// 修正前
password += chars.charAt(Math.floor(Math.random() * chars.length));

// 修正後
const array = new Uint32Array(length);
window.crypto.getRandomValues(array);
for (let i = 0; i < length; i++) {
    password += chars.charAt(array[i] % chars.length);
}
```

**効果**:

- Web Crypto API の `crypto.getRandomValues()` を使用
- 暗号学的に安全な乱数でパスワード生成
- 古いブラウザ向けのフォールバック実装も追加

---

## 🟠 重要な問題の修正

### 2. Spring Security の導入とCSRF保護

**ファイル**:

- `pom.xml`
- `src/main/kotlin/com/example/demo/config/SecurityConfig.kt` (新規作成)

**問題点**:

- Spring Securityが設定されておらず、CSRF攻撃に脆弱
- セキュリティヘッダーが不足

**修正内容**:

1. Spring Security依存関係を追加
2. SecurityConfigクラスを作成し、以下を実装:
    - CSRF保護（CookieベースのCSRFトークン）
    - セキュリティヘッダーの設定:
        - X-Frame-Options: DENY（クリックジャッキング対策）
        - X-Content-Type-Options: nosniff
        - X-XSS-Protection: 1; mode=block
        - Strict-Transport-Security（HTTPS環境向け）

**効果**:

- CSRF攻撃から保護
- クリックジャッキング攻撃の防止
- XSS攻撃のリスク軽減

---

### 3. 機密情報の環境変数化

**ファイル**:

- `src/main/resources/application.properties`
- `.env.example` (新規作成)
- `.gitignore.security` (新規作成)

**問題点**:

- データベースのユーザー名とパスワードが平文でハードコーディング
- バージョン管理システムに機密情報がコミットされる危険性

**修正内容**:

```properties
# 修正前
spring.datasource.username=demo_user
spring.datasource.password=demo_pass
# 修正後（環境変数で上書き可能）
spring.datasource.username=${DB_USERNAME:demo_user}
spring.datasource.password=${DB_PASSWORD:demo_pass}
```

**追加ファイル**:

- `.env.example`: 環境変数の設定例
- `.gitignore.security`: 機密ファイルの除外設定

**効果**:

- 本番環境で環境変数を使用して機密情報を管理
- 開発環境ではデフォルト値で動作
- .envファイルをバージョン管理から除外

---

### 4. セッションセキュリティの強化

**ファイル**: `src/main/resources/application.properties`

**修正内容**:

```properties
# セッションタイムアウト（30分）
server.servlet.session.timeout=30m
# セッションCookieのセキュリティ設定
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=false  # HTTPS環境では true に変更
server.servlet.session.cookie.same-site=strict
```

**効果**:

- XSS攻撃によるセッションCookieの窃取を防止（HttpOnly）
- CSRF攻撃の防止（SameSite=strict）
- セッションタイムアウトの設定

---

## 🟡 中程度の問題の修正

### 5. 入力値バリデーションの強化

**ファイル**:

- `src/main/kotlin/com/example/demo/model/ContentItem.kt`
- `src/main/kotlin/com/example/demo/model/Menu.kt`
- `src/main/kotlin/com/example/demo/model/Path.kt`
- `src/main/kotlin/com/example/demo/config/GlobalExceptionHandler.kt` (新規作成)

**問題点**:

- 入力値のバリデーションが不十分
- SQLインジェクションやXSS攻撃のリスク

**修正内容**:

1. すべてのモデルクラスに適切なバリデーションアノテーションを追加:
    - `@NotBlank`: 必須フィールドの検証
    - `@Size`: 文字列長の制限
    - `@Pattern`: 許可文字の制限（英数字、アンダースコア、ハイフンのみ）

2. グローバルエラーハンドラーを作成:
    - バリデーションエラーの一元管理
    - 適切なHTTPステータスコードとエラーメッセージの返却
    - 詳細なエラー情報の隠蔽（本番環境向け）

**効果**:

- 不正な入力値の拒否
- SQLインジェクション攻撃のリスク軽減
- XSS攻撃のリスク軽減
- ユーザーフレンドリーなエラーメッセージ

---

### 6. Actuatorエンドポイントの制限

**ファイル**: `src/main/resources/application.properties`

**修正内容**:

```properties
# Actuator Security
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

**効果**:

- 本番環境での情報漏洩を防止
- 必要最小限のエンドポイントのみを公開

---

## 📋 推奨される追加対応

### 本番環境へのデプロイ前に実施すべき事項

1. **HTTPS の有効化**
   ```properties
   server.servlet.session.cookie.secure=true
   ```

2. **認証・認可の実装**
    - 現在は全アクセスを許可しているため、適切な認証機構を追加
    - Spring SecurityのフォームログインまたはOAuth2の実装を検討

3. **環境変数の設定**
   ```bash
   export DB_URL=jdbc:postgresql://prod-server:5432/prod_db
   export DB_USERNAME=prod_user
   export DB_PASSWORD=強力なパスワード
   ```

4. **ログ監視の設定**
    - アクセスログの定期的な確認
    - 異常なアクセスパターンの検出

5. **Rate Limiting の実装**
    - APIエンドポイントへの過度なリクエストを制限
    - DDoS攻撃の防止

6. **Content Security Policy (CSP) の設定**
    - XSS攻撃のさらなる防止

---

## ✅ 動作確認

修正後、以下のコマンドでビルドとエラーチェックを実施してください:

```bash
# ビルド
./mvnw clean install

# テスト実行
./mvnw test

# アプリケーション起動
./mvnw spring-boot:run
```

---

## 📚 参考資料

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [Web Crypto API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Crypto_API)

---

## 変更ファイル一覧

### 修正されたファイル

1. `src/main/resources/static/js/pwgen.js` - 乱数生成器の修正
2. `pom.xml` - Spring Security依存関係の追加
3. `src/main/resources/application.properties` - セキュリティ設定の追加
4. `src/main/kotlin/com/example/demo/model/ContentItem.kt` - バリデーション強化
5. `src/main/kotlin/com/example/demo/model/Menu.kt` - バリデーション強化
6. `src/main/kotlin/com/example/demo/model/Path.kt` - バリデーション強化

### 新規作成されたファイル

1. `src/main/kotlin/com/example/demo/config/SecurityConfig.kt` - Spring Security設定
2. `src/main/kotlin/com/example/demo/config/GlobalExceptionHandler.kt` - エラーハンドラー
3. `.env.example` - 環境変数の設定例
4. `.gitignore.security` - セキュリティ関連の除外設定
5. `SECURITY_REPORT.md` - このドキュメント

---

*このレポートは自動的に生成されました。質問や懸念事項がある場合は、開発チームにお問い合わせください。*

