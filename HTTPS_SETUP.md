# HTTPS設定ガイド（ローカル開発環境）

## 🔐 設定完了

ローカル開発環境でHTTPSが有効になりました！

## 📋 実施した内容

### 1. **自己署名証明書の生成**

- **場所**: `src/main/resources/keystore/keystore.p12`
- **形式**: PKCS12
- **有効期限**: 10年間
- **パスワード**: `changeit`
- **対象**: `localhost`

### 2. **application.properties の設定**

以下の設定が追加されました：

```properties
# HTTPS Configuration (Development)
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore/keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=springboot
# Cookie secure フラグを有効化
server.servlet.session.cookie.secure=true
```

### 3. **.gitignore への追加**

証明書ファイルがGitリポジトリにコミットされないように設定しました。

## 🚀 アプリケーションの起動

```bash
# ビルド
./mvnw clean install

# 起動
./mvnw spring-boot:run
```

## 🌐 アクセス方法

アプリケーション起動後、以下のURLにアクセスしてください：

```
https://localhost:8443
```

### ⚠️ ブラウザでの証明書警告について

自己署名証明書を使用しているため、ブラウザで以下のような警告が表示されます：

#### **Chrome / Edge**

1. 「この接続ではプライバシーが保護されません」という警告が表示
2. 「詳細設定」をクリック
3. 「localhost にアクセスする（安全ではありません）」をクリック

#### **Firefox**

1. 「警告: 潜在的なセキュリティリスクあり」という警告が表示
2. 「詳細情報」をクリック
3. 「危険性を承知で続行」をクリック

#### **Safari**

1. 「この接続はプライベートではありません」という警告が表示
2. 「詳細を表示」をクリック
3. 「このWebサイトを閲覧」をクリック

これは**開発環境では正常な動作**です。自己署名証明書は信頼された認証局によって発行されていないため、この警告が表示されます。

## 🔧 HTTPに戻したい場合

HTTPに戻す場合は、`application.properties` で以下をコメントアウトしてください：

```properties
# server.port=8443
# server.ssl.enabled=true
# server.ssl.key-store=classpath:keystore/keystore.p12
# server.ssl.key-store-password=changeit
# server.ssl.key-store-type=PKCS12
# server.ssl.key-alias=springboot
# Cookie secure フラグも無効化
server.servlet.session.cookie.secure=false
```

## 🔍 動作確認

### 1. **HTTPS接続の確認**

ブラウザのアドレスバーに鍵マーク（🔒）が表示されることを確認してください。

### 2. **セキュリティヘッダーの確認**

開発者ツール（F12）→ ネットワークタブで応答ヘッダーを確認：

- `Strict-Transport-Security`
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection: 1; mode=block`

### 3. **HTTP/2の確認**

開発者ツールのネットワークタブで「Protocol」列を確認し、`h2`（HTTP/2）が使用されていることを確認できます。

## 📝 証明書情報の確認

証明書の詳細を確認したい場合：

```bash
keytool -list -v -keystore src/main/resources/keystore/keystore.p12 -storepass changeit
```

## ⚠️ 重要な注意事項

### 開発環境専用

- ⚠️ この自己署名証明書は**開発環境専用**です
- 🚫 本番環境では絶対に使用しないでください

### 本番環境での推奨事項

本番環境では以下を使用してください：

- **Let's Encrypt** - 無料のSSL/TLS証明書
- **商用CA** - DigiCert、GlobalSignなどの信頼された認証局
- **クラウドプロバイダーの証明書サービス** - AWS Certificate Manager、Azure Key Vaultなど

### パスワードについて

- 🔑 開発環境では `changeit` を使用していますが、本番環境では強力なパスワードを設定してください
- 🔒 パスワードは環境変数で管理することを推奨します

## 🐛 トラブルシューティング

### 証明書が見つからないエラー

```
Caused by: java.io.FileNotFoundException: class path resource [keystore/keystore.p12] cannot be opened
```

**解決方法**:

```bash
# 証明書を再生成
keytool -genkeypair -alias springboot -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore src/main/resources/keystore/keystore.p12 \
  -validity 3650 -storepass changeit \
  -dname "CN=localhost, OU=Development, O=Example, L=Tokyo, ST=Tokyo, C=JP"
```

### ポート8443が既に使用されている

```
Port 8443 was already in use
```

**解決方法**:

```bash
# ポートを使用しているプロセスを確認
lsof -i :8443

# プロセスを終了
kill -9 [PID]
```

または、`application.properties` でポート番号を変更：

```properties
server.port=9443
```

## 📚 参考資料

- [Spring Boot - Configure SSL](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.configure-ssl)
- [Java Keytool Documentation](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html)
- [OWASP Transport Layer Protection Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Protection_Cheat_Sheet.html)

---

✅ **HTTPSの設定が完了しました！**

`https://localhost:8443` でアプリケーションにアクセスできます。

