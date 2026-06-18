# 智慧健康日誌與風險評估系統 (Smart Health Diary & Risk Assessment System)

以決策樹（C4.5 概念）+ 資訊增益（Information Gain）自動找出睡眠時數 / 步數 / 心情分數中，
對健康風險等級分類最重要的特徵與切分門檻，取代寫死的 if/else 規則。

## 技術棧

- Backend：Java 21、Spring Boot 3、Maven、RESTful API
- Database：SQLite + Spring Data JPA（`org.hibernate.community.dialect.SQLiteDialect`）
- Frontend：純 HTML / CSS / JavaScript（`fetch` API），由 Spring Boot 以靜態資源提供
- AI：Google Gemini API（`gemini-1.5-flash`，用於將風險等級轉換成一句健康建議）
- 部署：Railway（內附 `Dockerfile` / `railway.json`）

## 系統架構與資料流

整體分四層：**前端（靜態頁面）→ 後端 API（Spring Boot Controller/Service）→ 資料庫（SQLite）**，
決策樹 **ML 模組**則是內嵌在後端 Service 層裡的一個記憶體中模型，不是獨立服務，
靠 `health_logs` 表的 `is_seed_data` 欄位跟資料庫串接，訓練資料與預測目標共用同一張表。

```
┌──────────────────────┐        fetch（JSON）        ┌───────────────────────────────────────┐
│        前端           │ ───────────────────────────▶ │              後端（Spring Boot）         │
│ HTML + CSS + JS       │                              │                                         │
│ entry/history/trends/ │ ◀─────────────────────────── │  Controller                            │
│ model/settings.html   │        JSON 回應              │  ├─ HealthLogController  /api/logs     │
└──────────────────────┘                              │  ├─ ModelController      /api/model    │
                                                        │  └─ AiController          /api/ai       │
                                                        │        │                                │
                                                        │        ▼                                │
                                                        │  Service                               │
                                                        │  ├─ HealthLogService                    │
                                                        │  │     │ 寫入/查詢紀錄、呼叫決策樹預測       │
                                                        │  │     ▼                                │
                                                        │  ├─ DecisionTreeService  ← ML 模組       │
                                                        │  │     entropy / Information Gain        │
                                                        │  │     在記憶體中保存目前訓練好的樹         │
                                                        │  └─ AiInsightService                    │
                                                        │        │ 依 risk_level 組 prompt          │
                                                        └────────┼────────────────────────────────┘
                                                                 │ JPA (Spring Data)
                                                                 ▼
                                                        ┌───────────────────┐        ┌──────────────────┐
                                                        │  SQLite            │        │  Google Gemini API │
                                                        │  health_logs 表    │        │ （risk_level→建議文字）│
                                                        └───────────────────┘        └──────────────────┘
```

**主要資料流：**

1. **新增健康紀錄**：使用者在「健康資料輸入」填表 → `entry.js` 呼叫 `POST /api/logs` → `HealthLogService` 先呼叫 `DecisionTreeService.predict(sleepHours, steps, moodScore)`，用目前記憶體中的樹即時算出 `risk_level` → 連同原始數值一起存進 `health_logs`（`is_seed_data=false`）→ 回傳含 `risk_level` 的紀錄給前端顯示。
2. **模型訓練**：App 啟動時（`SeedDataLoader`）或使用者呼叫 `POST /api/model/train` → `HealthLogService.retrain()` 從 `health_logs` 撈出所有 `is_seed_data=true` 的列 → 交給 `DecisionTreeService.train()` 計算 entropy / Information Gain、遞迴建樹 → 新樹存在 `DecisionTreeService` 內部記憶體變數，取代舊樹，之後所有 `predict()` 呼叫都用這棵新樹。
3. **查看決策樹**：「決策樹分析」頁面呼叫 `GET /api/model/tree`，直接把目前記憶體中的樹結構（含每層 entropy、特徵、門檻、樣本數）序列化成 JSON 回傳，給前端畫出樹狀圖。
4. **趨勢圖**：`trends.js` 呼叫 `GET /api/logs?sort=asc`（只取使用者手動輸入、排除種子資料）或 `GET /api/logs/trends`（含種子資料），把睡眠/步數/心情依日期排序後丟給 Chart.js 畫線圖。
5. **AI 健康建議**：使用者點某筆紀錄的建議按鈕 → `GET /api/ai/insight/{logId}` → `AiInsightService` 依該筆的 `risk_level` 組 prompt 呼叫 Gemini API（`generateContent`）→ 解析回應文字回傳；沒設定 `GEMINI_API_KEY` 或呼叫失敗時，自動退回內建規則式建議文字，不會中斷流程。

簡言之：前端只認 REST JSON，不直接碰資料庫；ML 模組（決策樹）是後端 Service 層裡的一個「訓練好就常駐在記憶體」的元件，靠同一張 `health_logs` 表跟資料庫互動——種子資料負責訓練、使用者資料負責被預測並寫入。

## 專案結構

```
.
├─ pom.xml
├─ Dockerfile
├─ railway.json
├─ src/main/java/com/healthdiary/
│   ├─ HealthDiaryApplication.java
│   ├─ entity/HealthLog.java
│   ├─ repository/HealthLogRepository.java
│   ├─ service/
│   │   ├─ DecisionTreeService.java   ← Entropy / Information Gain / 決策樹建構與預測
│   │   ├─ HealthLogService.java
│   │   ├─ SeedDataLoader.java        ← 啟動時寫入種子訓練資料
│   │   └─ AiInsightService.java      ← 呼叫 Gemini API
│   ├─ controller/
│   │   ├─ HealthLogController.java   /api/logs
│   │   ├─ ModelController.java       /api/model
│   │   ├─ AiController.java          /api/ai
│   │   └─ ApiExceptionHandler.java
│   ├─ dto/HealthLogRequest.java, SeedDataRequest.java
│   └─ config/WebConfig.java
└─ src/main/resources/
    ├─ application.properties
    └─ static/
        ├─ index.html        ← 儀表板
        ├─ pages/entry.html, history.html, trends.html, model.html, settings.html
        ├─ css/style.css
        └─ js/common.js, dashboard.js, entry.js, history.js, trends.js, model.js, settings.js
```

## 資料表設計

`health_logs`

| 欄位 | 型態 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 每筆紀錄唯一識別碼 |
| log_date | DATE | NOT NULL | 紀錄日期 |
| sleep_hours | REAL | NOT NULL | 睡眠時數 |
| steps | INTEGER | NOT NULL | 當日步數 |
| mood_score | INTEGER | NOT NULL | 心情分數（1~10） |
| risk_level | TEXT | 可為空 | 風險等級（由決策樹運算後產生） |
| is_seed_data | BOOLEAN | NOT NULL，預設 FALSE | 標記是否為決策樹訓練用的種子資料（設計時新增，與使用者填寫的歷史紀錄分開） |
| created_at | TIMESTAMP | 系統自動填入 | 建立時間（輔助排序，非原始規格欄位） |

只有一張表：種子訓練資料與使用者實際紀錄都存在 `health_logs`，用 `is_seed_data` 區分，
不另開資料表，符合題目「資料表設計：health_logs」的範圍。

## API 一覽

| Method | Path | 說明 |
|---|---|---|
| POST | /api/logs | 新增一筆健康紀錄，即時用目前決策樹預測 risk_level 並寫入 |
| GET | /api/logs?sort=asc\|desc | 歷史紀錄查詢（不含種子資料） |
| GET | /api/logs/{id} | 取得單筆紀錄 |
| PUT | /api/logs/{id} | 編輯紀錄（重新預測風險等級） |
| DELETE | /api/logs/{id} | 刪除紀錄 |
| GET | /api/logs/trends?from=&to= | 趨勢圖用資料（可選日期區間） |
| GET | /api/model/tree | 目前決策樹結構、Entropy、各特徵 Information Gain |
| POST | /api/model/train | 用目前種子資料重新訓練決策樹 |
| GET | /api/model/seed | 列出種子訓練資料 |
| POST | /api/model/seed | 新增一筆手動標記的種子資料（會自動重新訓練） |
| DELETE | /api/model/seed/{id} | 刪除種子資料（會自動重新訓練） |
| GET | /api/ai/insight/{logId} | 呼叫 Gemini API，依風險等級產生一句健康建議 |

## 決策樹演算法說明

`DecisionTreeService` 對 `sleep_hours`、`steps`、`mood_score` 三個連續特徵，
在排序後相鄰數值的中點上逐一嘗試切分門檻（C4.5 處理數值型屬性的標準做法），
計算每個門檻切分後的 Information Gain，取每個特徵的最佳門檻與最大增益，
再從三個特徵中選出增益最大者作為節點切分依據，遞迴建樹，直到節點純粹、
樣本數過少、深度達上限、或已無正增益可切分為止。

內建的 114 筆種子資料刻意讓 `steps` 與 `mood_score` 帶有一些雜訊（例如睡得好但當天走得少），
讓 `sleep_hours` 的 Information Gain 明顯最高（≈0.918，相對 steps≈0.517、mood_score≈0.378），
作為根節點的示範會比三個特徵完全打平更貼近真實情境。
