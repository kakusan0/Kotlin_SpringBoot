# Vault 設定ガイド

## 概要

このアプリケーションはHashiCorp Vaultから機密情報（データベース認証情報、APIキーなど）を取得できます。

## Vault設定

### 1. 環境変数

```bash
# Vault有効化
export VAULT_ENABLED=true

# Vault接続情報
export VAULT_URI=https://vault.example.com:8200
export VAULT_SCHEME=https

# 認証方法（TOKEN, KUBERNETES, APPROLE のいずれか）
export VAULT_AUTH_METHOD=TOKEN

# Token認証の場合
export VAULT_TOKEN=your-vault-token

# Kubernetes認証の場合
export VAULT_AUTH_METHOD=KUBERNETES
export VAULT_K8S_ROLE=your-k8s-role

# AppRole認証の場合
export VAULT_AUTH_METHOD=APPROLE
export VAULT_APPROLE_ROLE_ID=your-role-id
export VAULT_APPROLE_SECRET_ID=your-secret-id

# KVエンジン設定
export VAULT_KV_BACKEND=secret
export VAULT_KV_CONTEXT=application
```

### 2. Vaultにシークレットを保存

```bash
# 本番環境用シークレット
vault kv put secret/application/prod \
  db.url="jdbc:postgresql://db-host:5432/demo_db" \
  db.username="demo_user" \
  db.password="your-secure-password" \
  redis.host="redis-host" \
  redis.port="6379" \
  redis.password="redis-password"

# WAR環境用シークレット
vault kv put secret/application/war \
  db.url="jdbc:postgresql://db-host:5432/demo_db" \
  db.username="demo_user" \
  db.password="your-secure-password" \
  redis.host="redis-host" \
  redis.port="6379" \
  redis.password="redis-password"
```

### 3. プロファイル別設定

| プロファイル | Vault有効 | 説明                   |
|--------|---------|----------------------|
| dev    | false   | ローカル開発用（Vault不使用）    |
| prod   | true    | 本番環境（Vault必須）        |
| war    | true    | Tomcatデプロイ用（Vault推奨） |

## 認証方式

### Token認証（開発・テスト向け）

```yaml
spring:
  cloud:
    vault:
      authentication: TOKEN
      token: ${VAULT_TOKEN}
```

### Kubernetes認証（K8s環境向け）

```yaml
spring:
  cloud:
    vault:
      authentication: KUBERNETES
      kubernetes:
        role: your-app-role
        kubernetes-path: kubernetes
```

### AppRole認証（CI/CD向け）

```yaml
spring:
  cloud:
    vault:
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_APPROLE_ROLE_ID}
        secret-id: ${VAULT_APPROLE_SECRET_ID}
```

## Vaultポリシー例

```hcl
# application-policy.hcl
path "secret/data/application/*" {
  capabilities = ["read", "list"]
}

path "secret/metadata/application/*" {
  capabilities = ["read", "list"]
}
```

ポリシーの適用:

```bash
vault policy write application application-policy.hcl
```

## トラブルシューティング

### Vaultに接続できない場合

1. `VAULT_URI`が正しいか確認
2. ネットワーク接続を確認
3. 証明書を確認（HTTPS使用時）

### 認証エラーの場合

1. トークンが有効か確認
2. ポリシーが正しく設定されているか確認
3. シークレットパスが正しいか確認

### シークレットが取得できない場合

1. KVエンジンのバージョンを確認（v1/v2）
2. シークレットパスを確認
3. `vault kv get secret/application/prod` で手動確認

