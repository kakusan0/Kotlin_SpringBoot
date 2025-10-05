# セキュリティ修正 - クイックスタートガイド

## 🚀 すぐに始める

### 1. 環境変数の設定（本番環境）

`.env.example` をコピーして `.env` ファイルを作成してください：

```bash
cp .env.example .env
```

`.env` ファイルを編集して、本番環境の認証情報を設定：

```bash
DB_URL=jdbc:postgresql://your-prod-server:5432/prod_db
DB_USERNAME=your_prod_user
DB_PASSWORD=your_secure_password
```

### 2. ビルドと起動

```bash
# ビルド
./mvnw clean install

# 起動
./mvnw spring-boot:run
```

### 3. HTTPS環境での追加設定

HTTPS環境にデプロイする場合は、`application.properties` で以下を有効化してください：

```properties
server.servlet.session.cookie.secure=true
```

## 🔒 実施されたセキュリティ修正

### ✅ 修正済み項目

1. **パスワード生成の強化**
    - `Math.random()` → `crypto.getRandomValues()` に変更
    - 暗号学的に安全な乱数生成

2. **CSRF保護の追加**
    - Spring Security導入
    - CookieベースのCSRFトークン

3. **セキュリティヘッダーの設定**
    - X-Frame-Options: DENY
    - X-Content-Type-Options: nosniff
    - X-XSS-Protection: 1; mode=block
    - Strict-Transport-Security

4. **機密情報の保護**
    - データベース認証情報を環境変数化
    - `.gitignore` に `.env` を追加

5. **セッションセキュリティ**
    - HttpOnly Cookie
    - SameSite=strict
    - 30分のタイムアウト

6. **入力値バリデーション**
    - すべてのモデルに適切なバリデーション
    - グローバルエラーハンドラー

7. **Actuatorエンドポイントの制限**
    - 必要最小限のエンドポイントのみ公開

## 📝 詳細情報

詳細なセキュリティレポートは `SECURITY_REPORT.md` を参照してください。

## ⚠️ 注意事項

- `.env` ファイルは絶対にGitにコミットしないでください
- 本番環境では必ずHTTPSを使用してください
- 定期的なセキュリティアップデートを実施してください

## 📞 問題が発生した場合

セキュリティに関する問題や質問がある場合は、開発チームに連絡してください。

