# 商用環境用 Dockerfile（マルチステージビルド）
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# pom.xml と依存関係を先にコピー（キャッシュ効率化）
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# 依存関係のダウンロード
RUN mvn dependency:go-offline -B -Pprod

# ソースコードをコピー
COPY src ./src

# 商用環境向けビルド（テストスキップ）
RUN mvn clean package -Pprod -DskipTests=true

# 実行用の軽量イメージ
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# セキュリティ: 非 root ユーザーで実行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# GeoIP データベース用のディレクトリ
RUN mkdir -p /app/geoip && chown appuser:appgroup /app/geoip

# ビルドステージから WAR ファイルをコピー
COPY --from=builder /app/target/app-0.0.1-SNAPSHOT.war /app/app.war

# オプション: GeoIP データベースをコピー（ビルド時に含める場合）
# COPY GeoLite2-Country.mmdb /app/geoip/

# 所有権を変更
RUN chown -R appuser:appgroup /app

USER appuser

# ポート公開
EXPOSE 8443

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8443/actuator/health || exit 1

# JVM オプション（メモリ制限、GC チューニング）
ENV JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"

# 商用環境プロファイルで起動
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.war --spring.profiles.active=prod"]

