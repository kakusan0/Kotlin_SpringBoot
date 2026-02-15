# --- ステージ 1: ネイティブバイナリのビルド ---
FROM ghcr.io/graalvm/native-image-community:21 AS builder

WORKDIR /build
# 依存関係のキャッシュを利用するため pom.xml を先にコピー
COPY . .
# ネイティブイメージのビルド (非常に時間がかかります: 5分〜10分程度)
RUN ./mvnw --no-transfer-progress -Pnative native:compile -DskipTests

# --- ステージ 2: OpenFaaS Watchdog の取得 ---
FROM openfaas/of-watchdog:0.9.11 AS watchdog

# --- ステージ 3: 実行用軽量イメージ ---
# 実行環境は glibc があれば良いため、Distroless や Ubuntu などの軽量版を使用
FROM ubuntu:22.04

# 実行に必要な最小限のライブラリ（zlibなど）をインストール
RUN apt-get update && apt-get install -y zlib1g && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Watchdog をコピー
COPY --from=watchdog /fwatchdog /usr/bin/fwatchdog
RUN chmod +x /usr/bin/fwatchdog

# ビルドしたネイティブバイナリをコピー (名前は pom.xml の artifactId に依存)
COPY --from=builder /build/target/demo-app /app/demo-app

# --- OpenFaaS 設定 ---
ENV fprocess="/app/demo-app --server.port=8081"
ENV upstream_url="http://127.0.0.1:8081"
ENV mode="http"
ENV port="8080"

HEALTHCHECK --interval=3s CMD [ -e /tmp/.lock ] || exit 1

ENTRYPOINT ["fwatchdog"]