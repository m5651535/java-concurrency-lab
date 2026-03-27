# 🚀 Java Concurrency Lab: Performance & Resilience

這是一個針對 Java 21 **Virtual Threads (Project Loom)** 與 **Spring WebFlux (Reactive)** 的全方位效能實驗室。旨在透過物理數據監控（CPU/Memory/Latency）與極限壓測，探討現代 Java 架構在高併發與極端故障下的表現。

---

## 🌐 雲端部署 (GCP Cloud Run)

### 雲端架構
- **Compute**: GCP Cloud Run (Serverless, Scale-to-Zero, 需驗證存取)
- **Database**: GCP Cloud SQL (PostgreSQL 15, db-f1-micro)
- **Cache**: GCP Memorystore (Redis 7.0, 1GB)
- **Networking**: Serverless VPC Access Connector (私有網路通訊)
- **Registry**: GCP Artifact Registry
- **IaC**: Terraform (一鍵建立所有 GCP 資源，詳見 terraform/)
- **CI/CD**: GitHub Actions (push to main 自動觸發建置與部署)

### 健康狀態驗證
兩個服務皆已成功部署並通過健康檢查：

lab-mvc (Virtual Threads):
{"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}

lab-webflux (Reactive):
{"status":"UP","components":{"r2dbc":{"status":"UP"},"redis":{"status":"UP"}}}

### 本地開發 vs 雲端部署對照
| | 本地 (Docker Compose) | 雲端 (Cloud Run) |
|:---|:---|:---|
| **DB** | PostgreSQL container | Cloud SQL + Socket Factory |
| **Redis** | Redis container | Memorystore + VPC Connector |
| **Tracing** | Jaeger (localhost:16686) | 已停用 |
| **啟動方式** | ./run-test.sh | git push to main |
| **存取控制** | 無限制 | IAM 驗證 |

---

## 🏗️ 專案架構 (Multi-Module)

本專案採用 Maven 多模組架構，確保實驗變因完全隔離：

* **`lab-mvc`**: Java 21 Virtual Threads + Spring Data JPA (JDBC) + Resilience4j。
* **`lab-webflux`**: Spring WebFlux (Netty) + R2DBC + Resilience4j-Reactor。
* **`infrastructure`**: 包含 PostgreSQL 15、Redis 容器、Prometheus 監控與 Grafana 儀表板。

---

## 🛠️ 技術棧 (Tech Stack)

* **Runtime**: OpenJDK 21
* **Framework**: Spring Boot 3.5.11
* **Database**:
    * **Relational**: PostgreSQL 15 (JDBC for MVC / R2DBC for WebFlux)
    * **NoSQL/Cache**: Redis 7.2 (Alpine) with AOF Persistence
* **Reactive Stack**:
    * **Spring Data Redis Reactive**: 採用 Lettuce 非阻塞驅動進行高併發緩存存取
    * **Serialization**: Jackson2JsonRedisSerializer (支援跨平台 JSON 格式序列化)
* **Resilience**:
    * **Resilience4j**: 實作 Circuit Breaker 與 TimeLimiter，保護範圍涵蓋 PostgreSQL 與 Redis 鏈路
    * **Reactive Fallback**: 利用 Reactor `.onErrorResume` 實現緩存故障後的優雅降級 (Cache-Aside Pattern)
* **Observability**: Prometheus & Grafana
* **Load Testing**: [k6](https://k6.io/) (支援 2,000+ VUs 極限壓力測試)
* **Monitoring**: Windows PowerShell 客計化進程監控腳本

---

## 📊 效能實測數據 - 第一階段 (Performance Benchmarks)

我們模擬了典型的 **IO-Bound** 任務（1 秒的網路/資料庫延遲），並對兩個模組發動了 **2,000 VUs (Virtual Users)** 的高併發加壓測試。

### 核心實驗數據對比
| 指標 | MVC (Virtual Threads) | WebFlux (Reactive) |
| :--- | :--- | :--- |
| **程式碼風格** | 命令式 (Imperative) | 聲明式 (Declarative) |
| **2000 VU 成功率** | **100%** | **100%** |
| **平均延遲 (Latency)** | ~1.48s | ~1.48s |
| **CPU 使用率 (平均)** | **< 5% (極低)** | 15% ~ 30% (較高) |
| **記憶體佔用 (RSS)** | ~1300 MB | **~400 MB (極優)** |

### 💡 核心效能洞察 (Performance Insights)
* **Virtual Threads 的高效調度**: 虛擬執行緒在處理 IO 阻塞時，CPU 調度開銷極低，且開發難度遠低於響應式編程。
* **WebFlux 的空間優勢**: 在記憶體管理上具有壓倒性優勢，佔用空間僅為 MVC 的 1/3，極適合 K8s 等資源受限環境。
* **維護性**: Virtual Threads 讓 StackTrace 回歸連續性，大幅降低了除錯（Debug）難度。

---

## 🧪 實驗：進程凍結下的韌性對決 - 第二階段 (Resilience Test)

透過 `docker pause postgres-db` 模擬資料庫程序凍結（封包可達但無回應），測試系統在「殭屍連線」壓力下的容錯能力。

### 📊 韌性實驗數據對比
| 指標 | Spring MVC (8081) | Spring WebFlux (8082) |
| :--- | :--- | :--- |
| **超時機制** | 被動 (Driver-level) | 主動 (Declarative Operator) |
| **感知延遲** | **~3.0s** (1s Validation + 2s Timeout) | **~2.0s** (Strictly enforced) |
| **熔斷保護** | 成功實現 (CLOSED -> OPEN) | 成功實現 (CLOSED -> OPEN) |

### 💡 核心韌性洞察 (Resilience Insights)
* **超時累加效應**: 在阻塞模型中，實際延遲由連線池驗證與 Socket 超時串聯組成。實測發現，MVC 的回應時間會因為底層驅動的重試邏輯而產生 $1 \sim 2$ 秒的額外開銷。
* **WebFlux 的確定性**: 透過 Reactor 的 `.timeout()` 算子，WebFlux 展現了更強的邊界控制能力，能在不依賴底層驅動狀態的情況下，準時觸發降級。
* **快速失敗 (Fast-Fail)**: 當斷路器跳轉至 **OPEN** 狀態時，系統能瞬間回傳 Fallback，保護伺服器資源不被卡死的請求耗盡。

---

### 📊 效能實測數據 - 第三階段 (Redis Cache Optimization)

在 `lab-webflux` 中導入了 **Cache-Aside Pattern**，並針對 **2,000 VUs** 進行高併發加壓測試，驗證緩存層對系統 I/O 的優化能力。

| 指標 | WebFlux (純 R2DBC) | WebFlux (Redis Cache) | 改善幅度 |
| :--- | :--- | :--- | :--- |
| **平均延遲 (Avg)** | ~1,300ms | **1.45ms** | **~900x** |
| **p(95) 延遲** | ~1,500ms | **2.56ms** | **~600x** |
| **成功率 (Success Rate)** | 100% | **100%** | 極致穩定 |

**💡 核心優化洞察 (Optimization Insights)**
* **I/O 卸載**: 透過 Redis 緩存屏障，成功將 90% 以上的請求攔截在內存層，大幅降低 PostgreSQL 的連線池壓力與磁碟 I/O 開銷。
* **非阻塞鏈結**: 全程採用 `ReactiveRedisTemplate` 搭配 Lettuce 非阻塞驅動，確保高併發下 Event Loop 不會因為緩存存取而產生 Context Switch 損耗。

---

### 🧪 實驗：Redis 分散式緩存故障演練 - 第四階段 (Chaos Engineering)

透過混沌工程模擬快取層崩潰，測試系統在 **「Cache-Aside + Failover」** 機制下的韌性表現。

| 故障場景 | 模擬方式 | 系統反應 | 成功率 |
| :--- | :--- | :--- | :--- |
| **服務崩潰 (Crash)** | `docker stop redis-lab` | **快速失敗 (Fail-fast)**: 偵測到連線異常後立刻透過 `.onErrorResume` 切換回 DB，延遲微幅跳轉至 2.4ms。 | **100%** |
| **網路僵死 (Hang)** | `docker pause redis-lab` | **超時攔截 (Timeout)**: 觸發 2s Timeout 後由斷路器執行熔斷保護，防止請求堆積。 | **100%** |
| **自我修復 (Self-healing)** | `docker unpause` | 斷路器偵測到 Redis 恢復正常後，自動從 `OPEN` 轉向 `CLOSED` 並恢復緩存存取。 | **100%** |

**💡 核心韌性洞察 (Resilience Insights)**
* **優雅降級 (Graceful Degradation)**: 實作了 Service 層級的異常攔截，確保 Redis 故障屬於「效能降級」而非「服務中斷」。
* **多層防禦架構**: 利用 `onErrorResume` 處理業務層級的快速切換，並以 `Resilience4j` 作為系統層級的保險絲，雙重保障系統在高壓下的穩定性。

---

## 📊 效能實測數據 - 第五階段 (Cold Start: Virtual Threads vs. Reactive)

本階段模擬系統重啟後的「首波流量衝擊」，驗證兩大架構在 **Cache Warming (緩存預熱)** 機制下的實測表現。我們利用 Java 21 的 `VirtualThreadPerTaskExecutor` 與 Reactor 的併發運算，同步優化兩端的啟動效能。

### 核心實驗數據對比 (Target: 2,000 VUs 瞬間湧入)

| 指標 | WebFlux (Reactive) | MVC (Virtual Threads) | 觀察結果 |
| :--- | :--- | :--- | :--- |
| **平均延遲 (Avg)** | 2.24ms | **1.40ms** | **MVC 略勝**！虛擬執行緒在簡單 IO 讀取場景開銷極低。 |
| **p(95) 延遲** | 3.13ms | **2.34ms** | MVC 表現更為穩定，抖動較小。 |
| **最大延遲 (Max)** | 1.11s | **153ms** | MVC (Tomcat) 就緒狀態較 WebFlux (Netty) 更快進入穩定態。 |
| **啟動成功率** | 99.76% | **100.00%** | WebFlux 在啟動瞬間存在極短暫的連線拒絕 (EOF)。 |

**💡 核心優化洞察 (Optimization Insights)**

* **預熱與啟動解耦**: 兩者皆透過 `ApplicationRunner` 實現預熱邏輯。MVC 利用 **Java 21 虛擬執行緒** 以命令式風格實現併發填充，大幅提升代碼可讀性與維護性。
* **精準熱點填充**: 基於「二八法則」預熱 Top 100 資料，成功將 2,000 VUs 的首波衝擊攔截於內存層，將資料庫擊穿 (Cache Breakdown) 風險降至零。
* **自動化實驗鏈結**: 實作 `run-test.sh` 監控日誌關鍵字，實現「雙模組 Ready 即刻壓測」，確保實驗數據具備科學嚴謹性。

---
## 🧪 實驗：緩存擊穿與分散式鎖的防禦對決 - 第六階段 (Cache Breakdown vs. Distributed Lock)

本階段模擬最極端的 **「熱點失效 + 瞬間高併發」** 場景。我們刻意在 `lab-mvc` 注入了 $200\text{ms}$ 的資料庫查詢延遲，並在快取清空後，發動 **500 VUs 瞬間爆發 (Spike Test)**，驗證 **Redisson 分散式鎖** 對底層連線池的保護能力。

### 📊 擊穿實驗數據對比 (Target: 500 VUs 瞬間湧入，DB Latency: 200ms)

| 指標 | Simple 版 (無鎖) | Resilient 版 (Redisson 鎖) | 實驗結論 |
| :--- | :--- | :--- | :--- |
| **平均延遲 (Avg)** | 274.27ms | **239.4ms** | **鎖勝** (整體資源調度更有效) |
| **p(95) 延遲** | 704.07ms | **514.08ms** | **鎖勝** (大幅減少連線排隊時間) |
| **最大延遲 (Max)** | **1.48s** | 2.57s | **無鎖勝** (鎖的重試機制產生長尾延遲) |
| **HTTP 200 成功率** | 100.00% | 100.00% | 業務邏輯皆能正確執行 |
| **效能達標率 (<1s)** | 99.39% | **99.83%** | **鎖勝** (Resilient 版更符合 SLA 要求) |
| **DB 活躍連線數** | **5 (滿載/擊穿)** | **1 (極致保護)** | **鎖完勝** (成功守護資料庫) |

### 💡 核心架構洞察 (Architectural Insights)

* **效能達標率的差異**：在 **Simple 版** 中，由於連線池擠壓，導致 0.6% 的請求超過 1 秒。**Resilient 版** 雖有重試機制，但整體達標率提高到了 99.83%，證明鎖機制能更有效地管理併發請求。
* **資料庫連線池的崩潰觸發**：在 Simple 版中，PostgreSQL 的活躍連線數瞬間觸及 $5$ 條上限。這代表當流量湧入時，資料庫正處於「被榨乾」的邊緣，極易引發連鎖崩潰。
* **分散式鎖的「保鏢機制」**：Resilient 版透過 Redisson 確保只有「一個」請求進入資料庫，將資料庫壓力從 $O(N)$ 降至 $O(1)$。
* **Max 延遲的 Trade-off**：Resilient 版的最大延遲較高 ($2.57\text{s}$)，主因是「遞迴重試機制」。雖然產生了少數長尾延遲，但換取了對底層資料庫的絕對保護。

### 🧪 如何重現與驗證緩存擊穿 (Cache Breakdown Replay)

為了量化分散式鎖的保護力，我們設計了以下對照組實驗：

1. **環境準備 (Pre-setup)**：
  - 確保 `lab-mvc` 的資料庫連線池限制為 `maximum-pool-size: 5`。
  - 在邏輯中加入 `Thread.sleep(200)` 模擬資料庫慢查詢。
2. **重現「擊穿現象」(Reproduction)**：
  - 執行 `redis-cli FLUSHALL` 確保快取失效。
  - 對 `/simple` 接口發動壓測：`k6 run -e TARGET_PORT=8081 -e TEST_TYPE=simple stress_test.js`
  - **預期結果**：DB 連線數瞬間衝高至 $5$ 條滿載，平均延遲因連線排隊而大幅上升。
3. **驗證「鎖機制保護」(Validation)**：
  - 再次清空快取後，改對 `/resilient` 接口發動壓測。
  - **預期結果**：DB 活躍連線數穩定維持在 **$1$** 條，證實 Redisson 分散式鎖成功發揮「保鏢機制」，有效隔離瞬間併發壓力。
---
## ⚖️ 實驗：快取一致性與延遲雙刪 - 第七階段 (Cache Consistency & Delayed Double Delete)

本階段模擬高併發下的 **「讀寫競爭 (Read-Write Race Condition)」**。我們透過自研的 `app.feature.double-delete-enabled` 開關，對比在沒有與有 **「延遲雙刪」** 機制下，資料庫與快取的數據同步表現。

### 📊 一致性實驗數據對比 (Target: 500 VUs 併發讀寫, DB Write Delay: 500ms)

| 指標 | 關閉雙刪 (Dirty Data 重現) | 開啟雙刪 (Java 21 虛擬執行緒) | 實驗結論 |
| :--- | :--- | :--- | :--- |
| **Redis 數據狀態** | **Updated_ghibh (舊)** | **Updated_e6h97a (新) / nil** | **雙刪勝** (成功清理髒數據) |
| **PostgreSQL 狀態** | Updated_e6h97a (最新) | Updated_e6h97a (最新) | 資料庫皆能正確更新 |
| **最終一致性** | **❌ 永久不一致** | **✅ 達成最終一致** | **雙刪勝** (解決回填競爭問題) |
| **主執行緒阻塞時間** | 0ms (非同步) | 0ms (虛擬執行緒非同步) | 皆不影響 API 響應速度 |
| **額外資源消耗** | 無 | **極低** (Virtual Thread 輕量特性) | **Java 21 勝** (優於傳統執行緒池) |

### 💡 核心架構洞察 (Architectural Insights)

* **髒數據回填的真相**：在「關閉雙刪」實驗中，觀察到 Redis 鎖定在 `Updated_ghibh`。這是因為寫入者刪除快取後，讀取者在寫入者更新 DB 的 500ms 窗口內抓到舊資料並「熱心地」回填 Redis，導致數據永久偏離真實狀態。
* **Java 21 虛擬執行緒的優勢**：實作非同步延遲任務時，若使用傳統執行緒池，`Thread.sleep(500)` 會佔用寶貴的平台執行緒資源。本專案利用 **Virtual Threads**，讓任務在等待期間「掛起」而不佔用實體執行緒，實現了零成本的併發補償。
* **Feature Toggle 的實驗價值**：透過 `application.yml` 的功能開關，本專案可隨時切換「重現模式」與「修復模式」，這對於複雜系統的 **可觀測性 (Observability)** 與 **故障排除** 具有極大的實戰意義。
* **延遲時間的 Trade-off**：延遲時間（500ms）的設定需大於「讀取請求執行時間 + 主從同步延遲」。雖然這不是「強一致性」方案，但在大多數分散式場景中，它是平衡效能與一致性的最優解。

### 🖼️ 實驗視覺證據 (Visual Evidence)

透過 IntelliJ IDEA 的雙視窗比對（左：PostgreSQL / 右：Redis），我們完整紀錄了從數據偏離到韌性修復的過程：

| 1. 初始狀態 (Baseline) | 2. 競爭失效 (Race Condition) | 3. 韌性修復 (Success) |
| :--- | :--- | :--- |
| ![初始一致性](./docs/images/consistency-baseline.png) | ![髒數據重現](./docs/images/consistency-failure.png) | ![最終一致性](./docs/images/consistency-success.png) |
| **觀察：** 系統啟動初期，兩端數據完全同步。 | **觀察：** 偵測到數據偏差！Redis 因回填競爭鎖定在舊版次。 | **觀察：** 延遲雙刪生效，成功清理髒數據並達成一致。 |

### 🧪 如何重現快取不一致 (Race Condition Replay)

為了驗證「延遲雙刪」的必要性，本專案提供實驗開關：

1. **進入「重現模式」**：
  - 修改 `application.yml`：`app.feature.double-delete-enabled: false`
  - 重啟服務。
2. **執行併發壓測**：
  - 運行 `k6 run consistency_test.js`。
  - 觀察 DB 與 Redis 數據，將出現不一致現象（髒數據回填）。
3. **驗證「修復模式」**：
  - 將開關設為 `true` 並重啟。
  - 再次運行壓測，驗證 Redis 最終會被清理乾淨，達成最終一致性。

---
## 🧪 實驗：反應式鏈路下的並發守護 - 第八階段 (Reactive Concurrency Control)

本階段深入探討 **Spring WebFlux** 在非阻塞 I/O 環境下，面對「快取擊穿」時的行為表現。透過導入 **`RedissonReactiveClient`** 實作非阻塞式鎖，驗證響應式鏈路在高併發資源競爭下的自癒能力。

### 📊 實驗數據對比 (Target: 1,000 VUs 瞬間湧入，R2DBC Pool: 5)

| 指標 | WebFlux (無鎖版) | WebFlux (反應式鎖) | 改善幅度 |
| :--- | :--- | :--- | :--- |
| **平均延遲 (Avg)** | 215.06ms | **34.31ms** | **~6.3x 提升** |
| **p(95) 延遲** | 326.50ms | **74.38ms** | **~4.4x 提升** |
| **吞吐量 (RPS)** | ~2,336 | **~7,281** | **~3.1x 提升** |
| **快取擊穿失敗率** | 9.36% | **0.20%** | **趨近於零** |
| **DB 活躍連線數** | **5 (滿載/排隊)** | **1 (極致保護)** | **$O(N) \to O(1)$** |

### 💡 核心架構洞察 (Architectural Insights)

* **反應式羊群效應 (Thundering Herd)**: 在 WebFlux 中，非阻塞特性會放大快取失效瞬間的衝擊。由於請求不會阻塞執行緒，1,000 個並發會在微秒級別同時穿透快取層，若無鎖保護，將導致連線池瞬間乾涸，平均延遲被迫推升至資料庫延遲（200ms）以上。
* **非阻塞鎖的優勢**: 透過 `RLockReactive` 搭配 `Mono.defer()` 與 `Mono.delay()`，我們確保了沒搶到鎖的請求會在 Event Loop 中以「掛起」而非「阻塞」的方式等待。這讓系統在僅佔用 **1 條** 資料庫連線的情況下，依然能處理其他高併發請求。
* **Double Check 模式的實戰價值**: 在獲取鎖後實施第二次快取檢查，成功攔截了 99.8% 的重複資料庫查詢。這是在反應式鏈路中保護 R2DBC 等稀缺資源的核心策略，確保系統在高壓下依然具備確定性的延遲表現。

### 🖼️ 全鏈路觀測證據 (Observability Evidence)

透過 **Jaeger 分散式追蹤**，我們清楚觀測到兩者的微觀行為差異：
1.  **無鎖模式**: 呈現大規模並發的 `r2dbc query` Span，伴隨嚴重的連線獲取排隊（Connection Acquisition Wait）。
2.  **鎖強化模式**: 全域僅出現單一資料庫查詢 Span，其餘 99% 的 Trace 在 `tryLock` 成功後直接命中快取並回傳，呈現完美的「漏斗形」流量控制。
---
---

## 🧪 實驗：Circuit Breaker 精準定位與雙層快取架構 - 第九階段 (CB Scoping & L1/L2 Cache)

本階段針對 `lab-webflux` 的快取架構進行深度優化，聚焦兩個核心問題：

1. **Circuit Breaker 誤判根因排查**：原始版本將 `@CircuitBreaker` 標註於 Controller 層，導致分散式鎖的等待時間被誤算為 DB 失敗，在高併發下引發大規模誤熔斷。
2. **雙層快取 (L1 Caffeine + L2 Redis) 的效益驗證**：在修正 CB 位置後，導入 Caffeine 作為 JVM 本地快取層，量化其在真實冷熱場景下對延遲與吞吐量的影響。

---

### 🔍 問題診斷：Circuit Breaker 的誤判陷阱

透過 `/actuator/prometheus` 觀測到以下異常數據：

```
resilience4j_circuitbreaker_not_permitted_calls_total  304,983
resilience4j_circuitbreaker_state{state="half_open"}   1.0
resilience4j_circuitbreaker_state{state="closed"}      0.0
```

在總請求數僅 305,405 次的壓測中，**99.9% 的 DB 請求被 Circuit Breaker 直接拒絕**。根因分析如下：

```
高併發 → 鎖競爭 → Mono.delay(100ms) 遞迴重試
→ 累積超過 Controller 層的 timeout(2s)
→ TimeoutException 被 Circuit Breaker 記錄為 DB failure
→ 10 次滑動窗口內失敗率 > 50% → 熔斷器 OPEN
→ 後續所有請求走 Fallback，不進快取也不進 DB
```

**核心問題**：`@CircuitBreaker` 保護的範圍包含了鎖等待邏輯，使其統計的「失敗」並非真正的 DB 故障。
 
---

### 🛠️ 架構重構：職責分離

將 DB 存取抽離至獨立的 `UserDbService`，確保 Circuit Breaker 只保護真正的資料庫呼叫：

**修改前（Circuit Breaker 在 Controller 層）：**
```java
// ❌ timeout 包住整個含鎖重試的鏈路
@CircuitBreaker(name = "dbBreaker", fallbackMethod = "dbFallback")
public Mono<User> getUser(@PathVariable Long id) {
    return userService.getUserById(id).timeout(Duration.ofSeconds(2));
}
```

**修改後（Circuit Breaker 精準保護 DB 層）：**
```java
// ✅ timeout 與 CB 只包住真正的 DB 查詢
@CircuitBreaker(name = "dbBreaker", fallbackMethod = "fetchFallback")
public Mono<User> fetchAndCache(Long id, String key) {
    return userRepository.findById(id)
            .timeout(Duration.ofSeconds(1))  // 只計算 DB 延遲
            .flatMap(user -> redisTemplate.opsForValue()
                    .set(key, user, Duration.ofMinutes(10))
                    .thenReturn(user));
}
```
 
---

### 📊 效能實測數據 (隨機 userId: 1 ~ 10,000，冷啟動)

#### 短時壓測 (stages: 2s→500VU, 10s→1000VU, 5s→0)

| 指標 | 純 Redis (CB 修正前) | 純 Redis (CB 修正後) | L1+L2 (CB 修正後) |
| :--- | :--- | :--- | :--- |
| **吞吐量 (RPS)** | 2,677 | 3,950 | 2,870 |
| **平均延遲 (Avg)** | 166ms | 99ms | 154ms |
| **p(95) 延遲** | 404ms | 211ms | 404ms |
| **Cache Breakdown 失敗率** | 11% | 2% | 8% |

> CB 修正後，吞吐量提升 **+47%**，平均延遲下降 **40%**，驗證了誤熔斷是原始版本效能瓶頸的根本原因。

#### 長時壓測 (stages: 10s→500VU, 30s→1000VU, 10s→0，含 Cache Warm-up)

| 指標 | 純 Redis (CB 修正後) | L1+L2 (CB 修正後) | 改善幅度 |
| :--- | :--- | :--- | :--- |
| **吞吐量 (RPS)** | 3,872 | **4,335** | **+12%** |
| **平均延遲 (Avg)** | 93ms | **79ms** | **-15%** |
| **p(95) 延遲** | 222ms | **201ms** | **-9%** |
| **Cache Breakdown 失敗率** | 1.4% | **1.1%** | 略優 |
 
---

### 💡 核心架構洞察 (Architectural Insights)

* **Circuit Breaker 的保護邊界決定統計品質**：CB 的滑動窗口統計的是「被保護範圍內的失敗率」。若保護範圍過大，鎖競爭、網路抖動等非 DB 因素都會污染失敗率，導致在系統完全健康時觸發誤熔斷。精準定位是其發揮價值的前提。

* **L1 快取的效益依賴資料集大小**：在 userId 範圍僅 1~100 時，Redis 命中率已接近 100%，Caffeine 省掉的網路 RTT 無法抵銷其帶來的額外 pipeline 複雜度，反而導致效能下降。**唯有在資料集足夠大（本實驗為 10,000 筆）且 cache 充分預熱後，L1 的優勢才能顯現**。

* **冷熱啟動的差異**：冷啟動時（cache 全空），L1+L2 與純 Redis 差距不顯著，因為大多數請求都需要穿透到 DB。隨著 cache 逐漸熱起來，Caffeine 開始大量攔截熱點 key，省掉 Redis 的網路 RTT（實測約 14ms），此時雙層架構的優勢才充分體現。

* **AOP 的作用範圍限制**：`@CircuitBreaker` 依賴 Spring AOP Proxy，**無法攔截同一 Bean 內部的 private method 呼叫**。必須將需要被保護的邏輯抽離至獨立的 Spring Bean，才能確保 AOP 正確生效。

---

### 🏗️ 最終架構：三層防禦的請求流

```
請求進入
   │
   ▼
[L1] Caffeine (JVM 本地，~0ms)
   │ miss
   ▼
[L2] Redis (網路快取，~1-5ms)
   │ miss
   ▼
[分散式鎖] Redisson RLockReactive
   │ 搶到鎖 → Double Check L1/L2
   │ 未搶到 → Jitter Retry (100~150ms)
   ▼
[DB] UserDbService.fetchAndCache()
   └─ @CircuitBreaker (dbBreaker)
   └─ .timeout(1s)
   └─ 寫入 L2 Redis + 回填 L1 Caffeine
 ```
---
## 🚀 快速開始與自動化測試

本專案提供一鍵式自動化腳本，可自動完成編譯、部署、預熱監控與兩大架構的效能對比。

### 1. 執行全自動對比測試

```bash
chmod +x run-test.sh
./run-test.sh
```

此腳本將自動執行：
* **Maven 打包**：執行 `mvn clean package` 重新編譯所有模組，確保代碼變動生效。
* **環境重置**：執行 `docker-compose down -v` 徹底清空快取與資料庫 Volume，確保實驗數據不受舊緩存干擾。
* **預熱監控**：持續追蹤容器日誌，直到偵測到 **「緩存預熱完成」** 訊號，確保系統進入穩定態。
* **自動壓測**：依序對 Port 8082 (Flux) 與 8081 (MVC) 發動 k6 壓測並輸出對比結果。

### 2. 手動測試指南

若需單獨測試特定模組，可透過環境變數指定目標埠號：
* **WebFlux (8082)**: `k6 run -e TARGET_PORT=8082 stress_test.js`
* **MVC (8081)**: `k6 run -e TARGET_PORT=8081 stress_test.js`

---

## 📂 工具與監控配置清單

### 🛠️ 輔助工具
* **`run-test.sh`**: 核心自動化測試腳本，支援雙模組日誌追蹤與壓測觸發。
* **`monitor.ps1`**: Windows 進程監控腳本，紀錄 `TotalProcessorTime` 與 `WorkingSet`。

### 📊 監控端點
* **Grafana (Port 3000)**: 預載 Resilience4j Dashboard，實時監控熔斷器狀態。
* **Prometheus (Port 9090)**: 採集全系統度量衡數據。

---

## 🏆 總結：架構選擇矩陣 (Decision Matrix)

經過八個階段的極限壓測與韌性實驗，我們針對 **Java 21 Virtual Threads (MVC)** 與 **Spring WebFlux** 整理出以下決策矩陣，作為未來系統設計的選型參考：

| 維度 | MVC (Virtual Threads) | WebFlux (Reactive) | 實驗結論 |
| :--- | :--- | :--- | :--- |
| **開發效率** | **極高** (命令式編程) | 中 (聲明式/函數式) | **MVC 勝**：代碼直觀，學習曲線平緩。 |
| **記憶體效率** | 一般 (約 1.3GB / 2k VU) | **極優 (約 400MB / 2k VU)** | **WebFlux 勝**：適合資源受限的 K8s 環境。 |
| **CPU 開銷** | **極低 (< 5%)** | 較高 (15% ~ 30%) | **MVC 勝**：VT 調度開銷極小。 |
| **除錯難度** | **低 (Stacktrace 連續)** | 高 (異步鏈路斷裂) | **MVC 勝**：支援傳統 Debug 工具與 ThreadDump。 |
| **併發保護** | 依賴傳統鎖/執行緒池 | **必須配合反應式鎖/背壓** | **持平**：WebFlux 更依賴精確的併發控制。 |
| **I/O 吞吐極限** | 優 (受限於連線池阻塞) | **極優 (全鏈路非阻塞)** | **WebFlux 略勝**：在高密度 I/O 場景上限更高。 |

### 💡 最終選型建議

#### 1. 優先選擇 Spring MVC (Virtual Threads) 的場景：
* **既有系統遷移**：希望在不改動大量業務邏輯的情況下提升併發能力。
* **開發維護成本優先**：團隊熟悉 Imperative 風格，且希望降低 Debug 與追蹤難度。
* **CPU 密集型與 I/O 混合**：虛擬執行緒在處理簡單阻塞時表現極其穩定。

#### 2. 優先選擇 Spring WebFlux 的場景：
* **極致資源節約**：在 K8s 中追求極高的 Pod 密度，需要嚴格控制 Memory Footprint。
* **全鏈路響應式需求**：系統下游包含大量支持非阻塞協議的組件（如 R2DBC, Redis, MongoDB）。
* **高頻率小數據交換**：如 Gateway、Proxy 或需要維持大量長連線（WebSocket）的場景。

### 🏁 結語
本實驗證明，**Java 21 的虛擬執行緒已經讓 MVC 重新回到了高併發競爭的賽道上**。然而，不論選擇哪種架構，**「分散式鎖」與「全鏈路監控（Observability）」** 依然是守護高併發系統穩定性的兩大核心支柱。