# API リファレンス

## 基本情報

### ベースURL

```
http://localhost:8080
```

### 認証

- **方式**: Session-based Authentication (Spring Security)
- **必須**: ほぼすべてのエンドポイント（/login 除く）
- **ログイン**: POST /login
- **ログアウト**: POST /logout

### レスポンス形式

```json
成功時:
{
"id": 1,
"workDate": "2026-01-09",
"userName": "user@example.com",
...
}

エラー時: {
"code": "TIMESHEET_CONFLICT",
"message": "更新衝突が発生しました",
"statusCode": 409
}
```

---

## 勤務管理API

### 1. 出勤記録

```http
POST /timesheet/api/clock-in
Content-Type: application/json
```

**説明**: 出勤時刻を記録します。既存レコードがあれば更新、なければ新規作成。

**リクエスト**:

```
本文: なし
```

**レスポンス** (200 OK):

```json
{
  "id": 1,
  "workDate": "2026-01-09",
  "userName": "akihiro.kakutani",
  "startTime": "09:00:00",
  "endTime": null,
  "note": null,
  "freeNote": null,
  "breakMinutes": null,
  "durationMinutes": null,
  "workingMinutes": null,
  "version": 1,
  "holidayWork": false,
  "workLocation": "出社",
  "irregularWorkType": null,
  "createdAt": "2026-01-09T09:00:00+09:00",
  "updatedAt": "2026-01-09T09:00:00+09:00"
}
```

**エラーレスポンス** (409 Conflict):

```json
{
  "code": "TIMESHEET_CONFLICT",
  "message": "既に勤務中です: clock-out が必要",
  "statusCode": 409
}
```

**使用例**:

```bash
curl -X POST http://localhost:8080/timesheet/api/clock-in \
  -H "Content-Type: application/json" \
  --cookie "JSESSIONID=xxx"
```

---

### 2. 退勤記録

```http
POST /timesheet/api/clock-out
Content-Type: application/json
```

**説明**: 退勤時刻を記録し、稼働時間・実働時間を自動計算します。

**リクエスト**:

```
本文: なし
```

**レスポンス** (200 OK):

```json
{
  "id": 1,
  "workDate": "2026-01-09",
  "userName": "akihiro.kakutani",
  "startTime": "09:00:00",
  "endTime": "18:00:00",
  "breakMinutes": 60,
  "durationMinutes": 540,
  "workingMinutes": 480,
  "version": 2,
  "updatedAt": "2026-01-09T18:00:00+09:00"
}
```

---

### 3. 本日の勤務実績取得

```http
GET /timesheet/api/today
```

**説明**: 現在のユーザーの本日の勤務実績を取得します。

**パラメータ**: なし

**レスポンス** (200 OK):

```json
{
  "id": 1,
  "workDate": "2026-01-09",
  "userName": "akihiro.kakutani",
  "startTime": "09:00:00",
  "endTime": "18:00:00",
  "breakMinutes": 60,
  "durationMinutes": 540,
  "workingMinutes": 480
}
```

**レスポンス** (200 OK, データなし):

```json
null
```

**使用例**:

```bash
curl http://localhost:8080/timesheet/api/today \
  --cookie "JSESSIONID=xxx"
```

---

### 4. 期間の勤務実績取得

```http
GET /timesheet/api?from=2026-01-01&to=2026-01-31
```

**説明**: 指定期間のユーザーの勤務実績を取得します。

**パラメータ**:
| 名前 | 型 | 必須 | 説明 |
|------|-----|------|------|
| from | string (YYYY-MM-DD) | ○ | 開始日 |
| to | string (YYYY-MM-DD) | ✗ | 終了日（省略時は from と同じ） |

**レスポンス** (200 OK):

```json
[
  {
    "id": 1,
    "workDate": "2026-01-01",
    "userName": "akihiro.kakutani",
    "startTime": "09:00:00",
    "endTime": "18:00:00",
    "breakMinutes": 60,
    "durationMinutes": 540,
    "workingMinutes": 480
  },
  {
    "id": 2,
    "workDate": "2026-01-02",
    "startTime": null,
    "endTime": null,
    "workingMinutes": null
  }
]
```

**使用例**:

```bash
curl "http://localhost:8080/timesheet/api?from=2026-01-01&to=2026-01-31" \
  --cookie "JSESSIONID=xxx"
```

---

### 5. 備考の更新

```http
POST /timesheet/api/note
Content-Type: application/json
```

**説明**: 当日の備考（note フィールド）を更新します。

**リクエスト**:

```json
{
  "note": "重要な会議に参加"
}
```

**レスポンス** (200 OK):

```json
{
  "id": 1,
  "workDate": "2026-01-09",
  "userName": "akihiro.kakutani",
  "note": "重要な会議に参加",
  "updatedAt": "2026-01-09T15:30:00+09:00"
}
```

---

### 6. 自由備考の更新

```http
POST /timesheet/api/free-note
Content-Type: application/json
```

**説明**: 当日の自由備考（freeNote フィールド）を更新します。

**リクエスト**:

```json
{
  "freeNote": "プロジェクトA: 仕様書レビュー完了"
}
```

**レスポンス** (200 OK):

```json
{
  "id": 1,
  "workDate": "2026-01-09",
  "userName": "akihiro.kakutani",
  "freeNote": "プロジェクトA: 仕様書レビュー完了",
  "updatedAt": "2026-01-09T15:35:00+09:00"
}
```

---

### 7. 休憩時間の更新

```http
POST /timesheet/api/break
Content-Type: application/json
```

**説明**: 当日の休憩時間を更新します。

**リクエスト**:

```json
{
  "minutes": 90
}
```

**レスポンス** (200 OK):

```json
{
  "id": 1,
  "breakMinutes": 90,
  "workingMinutes": 450,
  "updatedAt": "2026-01-09T15:40:00+09:00"
}
```

---

### 8. リアルタイム更新のSSE接続

```http
GET /timesheet/api/events/subscribe
```

**説明**: Server-Sent Events (SSE) でリアルタイムの勤務実績更新を受け取ります。

**接続例** (JavaScript):

```javascript
const eventSource = new EventSource('/timesheet/api/events/subscribe');

eventSource.addEventListener('clock-in', (event) => {
    const data = JSON.parse(event.data);
    console.log('出勤:', data);
});

eventSource.addEventListener('clock-out', (event) => {
    const data = JSON.parse(event.data);
    console.log('退勤:', data);
});

eventSource.addEventListener('note', (event) => {
    const data = JSON.parse(event.data);
    console.log('備考更新:', data);
});

eventSource.onerror = () => {
    console.error('SSE 接続エラー');
    eventSource.close();
};
```

---

## レポート生成API

### 1. Excel生成（非同期）

```http
POST /timesheet/api/report/excel?from=2026-01-01&to=2026-01-31
```

**説明**: 指定期間のExcel形式の勤務表を生成します。非同期実行。

**パラメータ**:
| 名前 | 型 | 必須 | 説明 |
|------|-----|------|------|
| from | string (YYYY-MM-DD) | ○ | 開始日 |
| to | string (YYYY-MM-DD) | ○ | 終了日 |
| download | boolean | ✗ | true: ファイルダウンロード, false: ジョブID返却 |

**レスポンス** (200 OK):

```json
{
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "PENDING",
  "format": "EXCEL",
  "username": "akihiro.kakutani",
  "fromDate": "2026-01-01",
  "toDate": "2026-01-31",
  "createdAt": "2026-01-09T15:00:00+09:00"
}
```

**使用例**:

```bash
curl -X POST "http://localhost:8080/timesheet/api/report/excel?from=2026-01-01&to=2026-01-31" \
  --cookie "JSESSIONID=xxx"
```

---

### 2. PDF生成（非同期）

```http
POST /timesheet/api/report/pdf?from=2026-01-01&to=2026-01-31
```

**説明**: 指定期間のPDF形式の勤務表を生成します。非同期実行。

**パラメータ**: Excel と同じ

**レスポンス**: Excel と同じ（format: "PDF"）

---

### 3. ジョブステータス確認

```http
GET /timesheet/api/report/job/{jobId}
```

**説明**: レポート生成ジョブのステータスを確認します。

**パラメータ**:
| 名前 | 型 | 説明 |
|------|-----|------|
| jobId | string | ジョブID (UUID) |

**レスポンス** (200 OK):

```json
{
  "id": 123,
  "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "akihiro.kakutani",
  "format": "EXCEL",
  "status": "PROCESSING",
  "filePath": null,
  "errorMessage": null,
  "createdAt": "2026-01-09T15:00:00+09:00",
  "updatedAt": "2026-01-09T15:01:00+09:00"
}
```

**ステータス値**:
| ステータス | 説明 |
|----------|------|
| PENDING | 待機中 |
| PROCESSING | 処理中 |
| COMPLETED | 完了（filePath を含む） |
| FAILED | 失敗（errorMessage を含む） |

**完了時のレスポンス** (200 OK):

```json
{
  "status": "COMPLETED",
  "filePath": "/files/reports/a1b2c3d4_2026-01-01_2026-01-31.xlsx",
  "updatedAt": "2026-01-09T15:05:00+09:00"
}
```

**使用例**:

```bash
curl "http://localhost:8080/timesheet/api/report/job/a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
  --cookie "JSESSIONID=xxx"
```

---

## 休日管理API

### 1. 休日一覧取得

```http
GET /calendar/holidays?year=2026
```

**説明**: 指定年の休日一覧を取得します。

**パラメータ**:
| 名前 | 型 | 必須 | 説明 |
|------|-----|------|------|
| year | int | ○ | 年（YYYY形式） |

**レスポンス** (200 OK):

```json
[
  {
    "id": 1,
    "holidayDate": "2026-01-01",
    "holidayName": "元日",
    "isCompanyHoliday": false
  },
  {
    "id": 2,
    "holidayDate": "2026-01-12",
    "holidayName": "成人の日",
    "isCompanyHoliday": false
  },
  {
    "id": 100,
    "holidayDate": "2026-08-15",
    "holidayName": "夏季休暇",
    "isCompanyHoliday": true
  }
]
```

---

### 2. 休日追加

```http
POST /calendar/holidays
Content-Type: application/json
```

**説明**: 新しい休日を追加します。（管理者権限が必要）

**リクエスト**:

```json
{
  "holidayDate": "2026-12-25",
  "holidayName": "クリスマス（会社休暇）",
  "isCompanyHoliday": true
}
```

**レスポンス** (201 Created):

```json
{
  "id": 101,
  "holidayDate": "2026-12-25",
  "holidayName": "クリスマス（会社休暇）",
  "isCompanyHoliday": true,
  "createdAt": "2026-01-09T16:00:00+09:00"
}
```

---

## 認証API

### 1. ログイン

```http
POST /login
Content-Type: application/x-www-form-urlencoded
```

**説明**: ユーザーをログインさせます。

**リクエスト**:

```
username=user@example.com&password=password123
```

**成功時** (302 Redirect to /home):

```
Set-Cookie: JSESSIONID=xxx; Path=/; HttpOnly
Location: /home
```

**失敗時** (302 Redirect to /login?error):

```
Location: /login?error=1
```

---

### 2. ログアウト

```http
POST /logout
```

**説明**: 現在のセッションを終了します。

**レスポンス** (302 Redirect to /login):

```
Set-Cookie: JSESSIONID=deleted; Path=/; MaxAge=0
Location: /login
```

---

## エラーコード一覧

| コード                 | HTTPステータス | 説明            |
|---------------------|-----------|---------------|
| TIMESHEET_CONFLICT  | 409       | 同時更新による衝突     |
| TIMESHEET_NOT_FOUND | 404       | 勤務記録が見つからない   |
| VALIDATION_ERROR    | 400       | 入力値のバリデーション失敗 |
| UNAUTHORIZED        | 401       | 認証が必要         |
| FORBIDDEN           | 403       | 権限がない         |
| INTERNAL_ERROR      | 500       | サーバー内部エラー     |
| RATE_LIMIT_EXCEEDED | 429       | リクエストレート制限超過  |
| IP_BLOCKED          | 403       | IPがブロックされている  |

---

## 使用例

### 1. 出勤・退勤の記録フロー

```bash
# 出勤
curl -X POST http://localhost:8080/timesheet/api/clock-in \
  -H "Content-Type: application/json" \
  --cookie "JSESSIONID=xxx"

# レスポンス: { "id": 1, "startTime": "09:00:00", ... }

# (数時間後)

# 退勤
curl -X POST http://localhost:8080/timesheet/api/clock-out \
  -H "Content-Type: application/json" \
  --cookie "JSESSIONID=xxx"

# レスポンス: { "id": 1, "startTime": "09:00:00", "endTime": "18:00:00", ... }
```

### 2. 勤務表の取得とレポート生成

```bash
# 1. 1月の勤務記録を取得
curl "http://localhost:8080/timesheet/api?from=2026-01-01&to=2026-01-31" \
  --cookie "JSESSIONID=xxx" > entries.json

# 2. Excel レポート生成開始
curl -X POST "http://localhost:8080/timesheet/api/report/excel?from=2026-01-01&to=2026-01-31" \
  --cookie "JSESSIONID=xxx" > job.json

# jobId を抽出
JOB_ID=$(cat job.json | jq -r '.jobId')

# 3. ジョブ完了を確認（ポーリング）
for i in {1..30}; do
  STATUS=$(curl "http://localhost:8080/timesheet/api/report/job/$JOB_ID" \
    --cookie "JSESSIONID=xxx" | jq -r '.status')
  
  if [ "$STATUS" = "COMPLETED" ]; then
    FILE_PATH=$(curl "http://localhost:8080/timesheet/api/report/job/$JOB_ID" \
      --cookie "JSESSIONID=xxx" | jq -r '.filePath')
    echo "完了: $FILE_PATH"
    break
  fi
  
  echo "ステータス: $STATUS"
  sleep 1
done
```

---

## 最終更新日

2026年1月9日

