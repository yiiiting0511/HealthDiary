# 智慧健康日誌與風險評估系統 (Smart Health Diary & Risk Assessment System)

以決策樹（C4.5 概念）+ 資訊增益（Information Gain）自動找出睡眠時數 / 步數 / 心情分數中，
對健康風險等級分類最重要的特徵與切分門檻，取代寫死的 if/else 規則。

## 技術棧

- Backend：Java 21、Spring Boot 3、Maven、RESTful API
- Database：SQLite + Spring Data JPA（`org.hibernate.community.dialect.SQLiteDialect`）
- Frontend：純 HTML / CSS / JavaScript（`fetch` API），由 Spring Boot 以靜態資源提供
- AI：Google Gemini API（`gemini-1.5-flash`，用於將風險等級轉換成一句健康建議）
- 部署：Railway（內附 `Dockerfile` / `railway.json`）

## 本機執行

需要 JDK 21 與 Maven（本機需自行安裝；本沙箱環境僅有 JDK 11 且無 root 權限，
無法在此直接執行 `mvn package` 驗證編譯，請在你的開發機器上跑一次完整編譯）。

```bash
mvn spring-boot:run
```

預設使用 SQLite 檔案資料庫 `./data/health_diary.db`（會自動建立 `data/` 目錄）。
首次啟動會自動寫入 24 筆種子訓練資料並訓練一次決策樹。

開啟 http://localhost:8080 即可看到儀表板。

### 環境變數

| 變數 | 說明 | 預設值 |
|---|---|---|
| `PORT` | HTTP 連接埠 | 8080 |
| `DB_PATH` | SQLite 檔案路徑 | `./data/health_diary.db` |
| `GEMINI_API_KEY` | Google Gemini API 金鑰；未設定時 AI 建議改用內建規則式訊息，不會噴錯 | 空 |

## 部署到 Railway

1. 將此專案推到 Git repository，於 Railway 建立新專案並連結該 repo。
2. Railway 會偵測到 `Dockerfile` 並用它建置（`railway.json` 已指定 `DOCKERFILE` builder）。
3. 在 Railway 的 Variables 設定 `GEMINI_API_KEY`（選填）。
4. 建議替 SQLite 資料庫掛載一個 Volume 到 `/app/data`，並設定 `DB_PATH=/app/data/health_diary.db`，
   避免每次重新部署資料被清空。

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

內建的 24 筆種子資料刻意讓 `steps` 與 `mood_score` 帶有一些雜訊（例如睡得好但當天走得少），
讓 `sleep_hours` 的 Information Gain 明顯最高（≈0.918，相對 steps≈0.517、mood_score≈0.378），
作為根節點的示範會比三個特徵完全打平更貼近真實情境。
