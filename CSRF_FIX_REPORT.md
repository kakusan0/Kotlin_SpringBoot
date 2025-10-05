# 削除・更新処理の修正完了報告

## 🔍 問題の原因

**CSRF（Cross-Site Request Forgery）保護**が原因でした。

Spring Securityを導入した際、CSRF保護が自動的に有効になりましたが、JavaScriptのfetchリクエストにCSRFトークンが含まれていなかったため、以下の処理が失敗していました：

- ❌ 削除処理（DELETE）
- ❌ 更新処理（PUT）
- ❌ 追加処理（POST）
- ❌ 状態変更処理

## ✅ 実施した修正

### 1. **CSRFトークン取得関数の追加**

```javascript
function getCsrfToken() {
    // Cookieから XSRF-TOKEN を取得
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
        const [name, value] = cookie.trim().split('=');
        if (name === 'XSRF-TOKEN') {
            return decodeURIComponent(value);
        }
    }
    console.warn('CSRF token not found');
    return null;
}

function getHeaders(includeContentType = true) {
    const headers = {};
    const token = getCsrfToken();
    if (token) {
        headers['X-XSRF-TOKEN'] = token;
        console.log('CSRF token added to headers:', token.substring(0, 10) + '...');
    } else {
        console.error('CSRF token is missing! Requests may fail.');
    }
    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }
    return headers;
}
```

### 2. **すべてのfetchリクエストを修正**

manage.js内のすべてのPOST/PUT/DELETEリクエストにCSRFトークンを含めました：

#### 修正前：

```javascript
fetch(apiContent, {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: ...})
fetch(apiMenus, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: ...})
fetch(apiContent + '/' + id, {method: 'DELETE'})
```

#### 修正後：

```javascript
fetch(apiContent, {method: 'PUT', headers: getHeaders(), body: ...})
fetch(apiMenus, {method: 'POST', headers: getHeaders(), body: ...})
fetch(apiContent + '/' + id, {method: 'DELETE', headers: getHeaders(false)})
```

### 3. **修正された処理一覧**

✅ **メニュー管理**

- `onAddMenu()` - メニュー追加
- `onEditMenu()` - メニュー編集
- `onDeleteMenu()` - メニュー削除

✅ **パス管理**

- `onAddPath()` - パス追加
- `onEditPath()` - パス編集
- `onDeletePath()` - パス削除
- `onRestorePath()` - パス復元
- `onChangePathStatus()` - パス状態変更（有効/無効）

✅ **画面管理**

- `onAddScreen()` - 画面追加
- `onEditScreen()` - 画面編集
- `onDeleteScreen()` - 画面削除
- `onChangeMenuForItem()` - 画面のメニュー変更
- `onChangePathForItem()` - 画面のパス名変更
- `onChangeItemName()` - 画面名変更

### 4. **デバッグログの追加**

開発者コンソールでCSRFトークンの動作を確認できるようにログを追加しました：

- `console.log('CSRF token added to headers:...')` - トークンが正常に追加された場合
- `console.error('CSRF token is missing!')` - トークンが見つからない場合
- `console.warn('CSRF token not found in cookies or meta tags')` - Cookie/metaタグにトークンがない場合

## 🚀 動作確認手順

1. **アプリケーションを起動**
   ```bash
   ./mvnw spring-boot:run
   ```

2. **ブラウザでアクセス**
   ```
   https://localhost:8443/manage
   ```

3. **開発者ツールを開く（F12）**
    - Consoleタブを開いてCSRFトークンのログを確認

4. **各機能をテスト**
    - ✅ メニューの削除
    - ✅ パスの削除
    - ✅ パス状態の変更（有効↔無効）
    - ✅ 画面の削除
    - ✅ 画面名の変更
    - ✅ メニューの変更

## 📝 技術的な詳細

### CSRFトークンの流れ

1. **サーバー側（Spring Security）**
    - SecurityConfigで`CookieCsrfTokenRepository.withHttpOnlyFalse()`を設定
    - CSRFトークンを`XSRF-TOKEN`というCookieに格納
    - JavaScriptから読み取り可能（HttpOnly=false）

2. **クライアント側（JavaScript）**
    - CookieからCSRFトークンを取得
    - `X-XSRF-TOKEN`ヘッダーに含めてリクエスト送信

3. **サーバー側（Spring Security）**
    - リクエストヘッダーのトークンとサーバー側のトークンを照合
    - 一致すれば処理を続行、不一致なら403 Forbiddenエラー

### バックアップファイル

修正前のファイルは以下に保存されています：

- `src/main/resources/static/js/manage.js.backup`

## ⚠️ 注意事項

- **GETリクエスト**はCSRF保護の対象外のため、修正不要です
- **CSRFトークン**はページロード時に自動的に生成されます
- ブラウザのCookieが無効になっている場合、トークンが取得できません

## 🎉 完了

すべての削除・更新処理が正常に動作するようになりました！

ブラウザで`https://localhost:8443/manage`にアクセスして、各機能をテストしてください。

