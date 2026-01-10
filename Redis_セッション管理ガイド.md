# Redis セッション管理 - 実装ガイド

## 概要

このアプリケーションは Spring Session Data Redis を使用して、セッション情報を Redis
キャッシュに保存します。これにより、複数のアプリケーションインスタンス間でセッション情報を共有でき、スケーラビリティが向上します。

---

## 利点

### 1. **分散環境対応**

- 複数のアプリケーションサーバーでセッション情報を共有可能
- ロードバランサーの背後で複数インスタンスを実行可能

### 2. **高速アクセス**

- メモリベースのキャッシュで高速な読み書き
- DB アクセスより大幅に高速

### 3. **永続化オプション**

- Redis の永続化機能で、再起動時もセッション保持可能
- AOF (Append Only File) または RDB (Redis Database File) で設定可能

### 4. **簡易スケーリング**

- アプリケーションサーバーをスケールアップしても、Redis の接続プーリングで対応可能
- 新しいインスタンスも同じセッションストアを使用

---

## アーキテクチャ

```
┌─────────────────────────────────────────┐
│     Webアプリケーション                   │
│  (Spring Boot + Spring Security)        │
└────────────────┬────────────────────────┘
                 │
                 │ セッション読み書き
                 ▼
┌─────────────────────────────────────────┐
│    Spring Session Data Redis            │
│  (セッション管理フレームワーク)           │
└────────────────┬────────────────────────┘
                 │
                 │ Redis プロトコル
                 ▼
┌─────────────────────────────────────────┐
│         Redis サーバー                    │
│  (localhost:6379)                       │
│                                         │
│  キー: spring:session:{sessionId}       │
│  値: セッション属性 (JSON 形式)          │
└─────────────────────────────────────────┘
```

---

## 設定詳細

### 1. pom.xml - 依存関係

既に以下の依存関係が含まれています：

```xml
<!-- Redis for session store -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
```

### 2. application.yml - グローバル設定

```yaml
spring:
  session:
    store-type: redis              # セッションストアを Redis に設定
    redis:
      namespace: spring:session    # Redis キーのプレフィックス
      flush-mode: on_save          # セッション変更時に即座に Redis に保存
      save-mode: on_set_attribute  # 属性設定時に保存
  redis:
    host: ${REDIS_HOST:localhost}  # Redis ホスト (環境変数で上書き可能)
    port: ${REDIS_PORT:6379}       # Redis ポート
    password: ${REDIS_PASSWORD:}   # Redis パスワード（オプション）
    timeout: 2000                  # 接続タイムアウト (ms)
    jedis:
      pool:
        max-active: 20             # コネクションプール最大数
        max-idle: 10               # 最大アイドル接続数
        min-idle: 5                # 最小アイドル接続数
        max-wait: -1ms             # 接続待機タイムアウト
  servlet:
    session:
      cookie:
        same-site: strict          # SameSite 属性で CSRF 対策
        http-only: true            # HttpOnly で XSS 対策
      timeout: 30m                 # セッションタイムアウト時間
```

### 3. RedisConfig.kt - Redis 設定クラス

```kotlin
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30分
class RedisConfig {
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        // JSON シリアライゼーション設定
        // キーは String、値は JSON 形式
    }
}
```

**設定内容**:

- `@EnableRedisHttpSession`: Redis セッション有効化
- `maxInactiveIntervalInSeconds = 1800`: 30分のタイムアウト
- `StringRedisSerializer`: キーは String で保存
- `GenericJackson2JsonRedisSerializer`: 値は JSON で保存

### 4. SecurityConfig.kt - セキュリティ設定との統合

```kotlin
@Bean
fun sessionRegistry(repoProvider: ObjectProvider<FindByIndexNameSessionRepository<out Session>>? = null): SessionRegistry {
    val repo = repoProvider?.ifAvailable
    if (repo != null) {
        // Redis ベースのセッションレジストリ
        return SpringSessionBackedSessionRegistry(repo)
    }
    return SessionRegistryImpl()
}
```

---

## 開発環境でのセットアップ

### 1. Docker Compose で Redis を起動

```bash
docker-compose up -d redis
```

確認:

```bash
docker ps | grep redis
docker-compose logs redis
```

### 2. Redis との接続確認

```bash
# Redis CLI で接続（Docker コンテナ内）
docker exec -it demo-redis-dev redis-cli

# Redis CLI コマンド
> PING
PONG

> INFO server
# Redis サーバー情報表示

> KEYS spring:session:*
# セッション情報確認

> GET spring:session:sessions:{sessionId}
# 特定のセッション情報表示
```

### 3. アプリケーション起動

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### 4. セッション動作確認

```bash
# ログイン
curl -c cookies.txt \
  -d "username=角谷亮洋&password=角谷亮洋" \
  http://localhost:8080/login

# セッション Cookie を含めてアクセス
curl -b cookies.txt http://localhost:8080/home

# Redis でセッション確認
docker exec -it demo-redis-dev redis-cli
> KEYS spring:session:*
> TTL spring:session:sessions:{sessionId}  # TTL 確認
```

---

## 本番環境への展開

### 1. 外部 Redis サーバーの設定

```bash
# Vault に Redis 設定を保存
vault kv put secret/application/prod \
  redis.host=redis.example.com \
  redis.port=6379 \
  redis.password=secure_password
```

### 2. 環境変数設定

```bash
export REDIS_HOST=redis.example.com
export REDIS_PORT=6379
export REDIS_PASSWORD=secure_password
```

### 3. Docker コンテナでの実行

**Dockerfile**:

```dockerfile
FROM openjdk:21-slim
COPY target/app-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml (本番)**:

```yaml
services:
  app:
    image: my-app:latest
    environment:
      REDIS_HOST: redis-host
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    depends_on:
      - redis-node

  redis-node:
    image: redis:7.2-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes
```

### 4. Redis 永続化設定

```bash
# Redis 永続化を有効化（AOF モード）
redis-cli CONFIG SET appendonly yes
redis-cli CONFIG REWRITE

# または Redis 起動コマンドで指定
redis-server --appendonly yes
```

---

## セッション情報の確認・管理

### Redis での確認

```bash
# Redis CLI 接続
redis-cli

# セッション一覧
KEYS spring:session:*

# セッション情報表示
GET spring:session:sessions:abc123

# セッション有効期限確認
TTL spring:session:sessions:abc123

# セッション削除
DEL spring:session:sessions:abc123

# セッション統計
DBSIZE     # 全キー数
INFO stats # Redis 統計情報
```

### アプリケーション側での確認

```kotlin
// SessionRegistry を注入
@Autowired
private lateinit var sessionRegistry: SessionRegistry

@GetMapping("/api/sessions")
fun listSessions(principal: Authentication): Map<String, Any> {
    val userName = principal.name
    val sessions = sessionRegistry.allPrincipals
        .filter { it == userName }
        .flatMap { sessionRegistry.getAllSessions(it, false) }
    
    return mapOf(
        "username" to userName,
        "sessionCount" to sessions.size,
        "sessions" to sessions.map {
            mapOf(
                "sessionId" to it.id,
                "creationTime" to it.creationTime,
                "lastAccessedTime" to it.lastAccessedTime
            )
        }
    )
}
```

---

## トラブルシューティング

### 1. Redis に接続できない

```
ERR: Redis connection error
```

**対策**:

```bash
# Redis が起動しているか確認
docker ps | grep redis

# Redis ログ確認
docker-compose logs redis

# 起動
docker-compose up -d redis
```

### 2. セッションが保持されない

**原因**: Redis タイムアウト値が短すぎる可能性

**対策**:

```yaml
spring:
  servlet:
    session:
      timeout: 60m  # タイムアウト値を増加
```

### 3. メモリ不足エラー

```
OOM: command not allowed when used memory > 'maxmemory'
```

**対策**:

```bash
# Redis メモリ使用状況確認
redis-cli INFO memory

# メモリ上限設定（例：1GB）
redis-cli CONFIG SET maxmemory 1gb
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

### 4. セッション遅延/タイムアウト

**原因**: Redis パフォーマンス低下

**対策**:

```bash
# Redis スローログ確認
redis-cli SLOWLOG GET 10

# コネクションプール設定調整
spring:
  redis:
    jedis:
      pool:
        max-active: 50  # 増加
```

---

## パフォーマンス最適化

### 1. コネクションプーリング

```yaml
spring:
  redis:
    jedis:
      pool:
        max-active: 20      # 同時接続数
        max-idle: 10        # 遊休接続保持数
        min-idle: 5         # 最小遊休接続数
        max-wait: -1ms      # 無制限に待機
```

### 2. キー有効期限（TTL）

```bash
# セッション自動削除タイムアウト (Redis 側での期限切れ)
# application.yml で設定された timeout より長めに設定推奨

redis-cli CONFIG GET timeout
redis-cli CONFIG SET timeout 600  # 10分
```

### 3. Redis メモリ最適化

```yaml
spring:
  data:
    redis:
      # Jedis の代わりに Lettuce を使用可能
      # より効率的なメモリ管理が可能
      client-type: lettuce
```

---

## セキュリティベストプラクティス

### 1. Redis パスワード保護

```yaml
spring:
  redis:
    password: ${REDIS_PASSWORD}  # 環境変数から取得
```

### 2. TLS/SSL 接続

```yaml
spring:
  redis:
    ssl: true
    port: 6380  # SSL ポート
```

### 3. ACL（アクセス制御）

```bash
# Redis 6.0+ での ACL 設定
redis-cli ACL SETUSER appuser \
  >password123 \
  ~spring:session:* \
  +@all
```

### 4. ネットワーク分離

```yaml
# 開発環境: localhost のみ
redis:
  host: localhost
  bind: 127.0.0.1

# 本番環境: VPC 内の通信のみ
redis:
  host: redis.internal.example.com
```

---

## 監視・ロギング

### 1. Redis Monitor コマンド

```bash
# リアルタイムでコマンド監視
redis-cli MONITOR
```

### 2. スローログ

```bash
# スローログ確認
redis-cli SLOWLOG GET 10

# スローログ設定
redis-cli CONFIG SET slowlog-log-slower-than 10000  # 10ms以上
```

### 3. Spring Actuator での監視

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  endpoint:
    health:
      show-details: always
```

```bash
curl http://localhost:8080/actuator/health
```

---

## マイグレーション（DB セッションから Redis セッションへ）

既存のアプリケーションで JDBC セッションを使用している場合:

### 1. 既存セッションのクリア（オプション）

```sql
-- SPRING_SESSION テーブルのクリア
TRUNCATE TABLE SPRING_SESSION;
TRUNCATE TABLE SPRING_SESSION_ATTRIBUTES;
```

### 2. アプリケーション再起動

```bash
# 新しいセッション設定（Redis）で起動
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### 3. 動作確認

```bash
# ログイン後、Redis に セッションが保存されていることを確認
redis-cli KEYS spring:session:*
```

---

## 最終更新日

2026年1月10日

## 参考リンク

- [Spring Session Documentation](https://spring.io/projects/spring-session)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Redis Documentation](https://redis.io/docs/)
- [Redis 日本語ドキュメント](https://redis.io/docs/stack/)

