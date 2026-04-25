# cloudflared 運用手順

このドキュメントは、`infoapp.org` を公開している Cloudflare Tunnel (`home`) の運用手順メモです。

## 前提

この環境では `cloudflared` を **Homebrew service ではなく custom LaunchAgent** で管理します。

- Tunnel 名: `home`
- Tunnel ID: `2b851dcb-9362-4e4c-9929-ed99f128b6a3`
- Config: `/Users/akihirokakutani/.cloudflared/config.yml`
- Credentials: `/Users/akihirokakutani/.cloudflared/2b851dcb-9362-4e4c-9929-ed99f128b6a3.json`
- LaunchAgent: `/Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist`
- stdout log: `/Users/akihirokakutani/Library/Logs/cloudflared-home.out.log`
- stderr log: `/Users/akihirokakutani/Library/Logs/cloudflared-home.err.log`

公開先:

- Public URL: `https://infoapp.org`
- Origin: `http://127.0.0.1:8080`

## 重要な注意

`brew services start cloudflared` は使わないでください。

この環境では Homebrew が生成した `homebrew.mxcl.cloudflared.plist` が
`tunnel run home` を含まない不正な起動定義になっており、以下のような状態を起こします。

- `cloudflared error 1`
- ログに `Use cloudflared tunnel run to start tunnel home`
- Cloudflare 側で active connector が 0 になる
- `infoapp.org` で `Error 1033` が出る

**cloudflared の管理は `launchctl` + `com.akihiro.cloudflared-home.plist` に統一すること。**

---

## 1. 状態確認コマンド

### LaunchAgent が動いているか確認

```zsh
launchctl list | grep 'com.akihiro.cloudflared-home'
```

### Tunnel の接続状態を確認

```zsh
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
```

### 公開 URL の疎通確認

```zsh
curl -I https://infoapp.org
```

### ローカル origin の疎通確認

```zsh
curl -I http://127.0.0.1:8080
```

### ログ確認

```zsh
tail -f /Users/akihirokakutani/Library/Logs/cloudflared-home.err.log
```

```zsh
tail -f /Users/akihirokakutani/Library/Logs/cloudflared-home.out.log
```

---

## 2. バージョンアップ後の手順

`cloudflared` を更新した後は、以下の順で確認します。

### 2-1. バージョンアップ

```zsh
brew update
brew upgrade cloudflared
```

### 2-2. バージョン確認

```zsh
/opt/homebrew/opt/cloudflared/bin/cloudflared --version
```

### 2-3. LaunchAgent を再読み込み

```zsh
launchctl unload /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
launchctl load -w /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
```

### 2-4. 起動確認

```zsh
launchctl list | grep 'com.akihiro.cloudflared-home'
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
```

### 2-5. 外部疎通確認

```zsh
curl -I https://infoapp.org
```

正常なら以下のどちらかになります。

- `200`
- `302`（この環境では `/tools` へリダイレクトされることがある）

### 2-6. origin 側確認

トンネルは生きていても、アプリが落ちていると公開先は失敗します。

```zsh
curl -I http://127.0.0.1:8080
```

---

## 3. バージョンアップ後に失敗したときの復旧手順

### 症状A: `Error 1033` が出る

まずトンネル接続が 0 でないか確認します。

```zsh
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
```

active connector が出ない場合は、LaunchAgent を再読み込みします。

```zsh
launchctl unload /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
launchctl load -w /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
```

その後に再確認:

```zsh
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
curl -I https://infoapp.org
```

### 症状B: `launchctl` 上はいるが公開できない

ログを確認します。

```zsh
tail -n 100 /Users/akihirokakutani/Library/Logs/cloudflared-home.err.log
```

よく見る確認点:

- 設定ファイルが読めているか
- `credentials-file` が存在するか
- `Registered tunnel connection` が出ているか
- origin `127.0.0.1:8080` が応答しているか

### 症状C: Homebrew service を触って壊した

`brew services` は使わず、custom LaunchAgent 側を使います。

```zsh
brew services stop cloudflared
launchctl unload /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
launchctl load -w /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
```

---

## 4. 停止後の手順

### 4-1. 意図的に停止する

```zsh
launchctl unload /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
```

停止確認:

```zsh
launchctl list | grep 'com.akihiro.cloudflared-home'
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
```

### 4-2. 停止後に再開する

```zsh
launchctl load -w /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
```

再開確認:

```zsh
launchctl list | grep 'com.akihiro.cloudflared-home'
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
curl -I https://infoapp.org
```

### 4-3. 異常停止していたときの再開

`infoapp.org` が 1033 になった、または `launchctl list` に出ない場合は以下。

```zsh
launchctl unload /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist >/dev/null 2>&1 || true
launchctl load -w /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
```

続けて確認:

```zsh
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
curl -I https://infoapp.org
```

---

## 5. 手動で切り分けしたいとき

常駐を使わず、前面で直接起動してログを見る場合:

```zsh
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel --config /Users/akihirokakutani/.cloudflared/config.yml run home
```

この状態で別ターミナルから確認:

```zsh
/opt/homebrew/opt/cloudflared/bin/cloudflared tunnel info home
curl -I https://infoapp.org
```

確認が終わったら `Ctrl+C` で停止し、常駐へ戻します。

```zsh
launchctl load -w /Users/akihirokakutani/Library/LaunchAgents/com.akihiro.cloudflared-home.plist
```

---

## 6. トラブル時のチェックリスト

- `cloudflared --version` は期待したバージョンか
- `launchctl list | grep com.akihiro.cloudflared-home` に出るか
- `cloudflared tunnel info home` に connector があるか
- `curl -I https://infoapp.org` が 200 / 302 か
- `curl -I http://127.0.0.1:8080` が応答するか
- `cloudflared-home.err.log` に `Registered tunnel connection` が出ているか
- `brew services start cloudflared` を実行していないか

