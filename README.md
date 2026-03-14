# 🚀 Java Concurrency Lab: Performance & Resilience

這是一個針對 Java 21 **Virtual Threads (Project Loom)** 與 **Spring WebFlux (Reactive)** 的全方位效能實驗室。旨在透過物理數據監控（CPU/Memory/Latency）與極限壓測，探討現代 Java 架構在高併發與極端故障下的表現。

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
* **Monitoring**: Windows PowerShell 客製化進程監控腳本

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
* 
---

## 📊 效能實測數據 - 第五階段 (Cold Start & Cache Warming)

本階段模擬系統重啟後的「首波流量衝擊」，驗證 **Reactive Cache Warmer** 在消除冷啟動延遲（Latency Spike）與保護資料庫擊穿（Cache Breakdown）的實測表現。

### 核心實驗數據對比 (Target: 2,000 VUs 瞬間湧入)
| 指標 | 無預熱 (No Warming) | 有預熱 (Reactive Warming) | 改善幅度 |
| :--- | :--- | :--- | :--- |
| **首波衝擊最大延遲 (Max)** | **1.31s** | **293ms** | **~4.5x 穩定度提升** |
| **平均延遲 (Avg)** | 3.38ms | **1.42ms** | **~58% 效能優化** |
| **p(95) 延遲** | 5.07ms | **2.21ms** | **~56% 響應優化** |
| **啟動初期成功率** | 98.2% (存在連線飽和風險) | **100% (啟動即滿速)** | **消除服務空窗期** |

**💡 核心優化洞察 (Optimization Insights)**
* **預熱與啟動解耦**: 利用 `ApplicationRunner` 搭配非阻塞 `flatMap` 併發填充，確保預熱過程在背景執行而不阻塞 Spring Boot 主啟動鏈路，兼顧啟動速度與系統穩定度。
* **精準熱點填充**: 基於「二八法則」僅預熱 Top 100 熱點資料，成功將 2,000 VUs 的首波衝擊攔截於內存層，有效避免資料庫連線池（Connection Pool）在啟動瞬間因擊穿而爆滿。
* **自動化實驗鏈結**: 實作 `run-test.sh` 監控日誌關鍵字，實現「預熱完成即刻壓測」的精準自動化驗證，確保實驗數據的可重複性與嚴謹性。

---

### 🛠️ Redis 實驗執行指南

1. **環境啟動**:
    * `docker-compose up -d --build` (自動載入 Redis AOF 持久化配置)。
2. **日誌監控**:
    * 使用 `docker logs -f lab-flux-container` 觀察 `WARN: Redis 故障，改由 DB 取得資料` 之跳轉日誌。
3. **資料驗證**:
    * 進入容器檢查 Key 前綴規範：`docker exec -it redis-lab redis-cli KEYS "lab:user:*"`
   
## 🚀 如何執行實驗

### 1. 編譯與啟動
執行以下指令編譯專案並啟動 Docker 基礎設施：
* `mvn clean install -DskipTests`
* `docker-compose up -d --build`

### 2. 啟動資源監控 (Windows PowerShell)
使用專案內附的監控腳本紀錄物理數據：
* `.\monitor.ps1 -MvcPid <PID> -FluxPid <PID> -Duration 120`

### 3. 執行壓測與故障模擬
* **k6 壓測**: 執行 `k6 run script.js`。
* **資料庫故障**: 執行 `docker pause postgres-db`。
* **恢復與自癒**: 執行 `docker unpause postgres-db` 並連續請求以集滿 `HALF_OPEN` 樣本。

---

## 📂 工具與監控配置清單

### 🛠️ 輔助腳本
* **`monitor.ps1`**: Windows 原生監控腳本，紀錄 `TotalProcessorTime` 與 `WorkingSet`。
* **`script.js`**: k6 壓測腳本，包含 VUs 階梯加壓配置。

### 📊 監控端點
* **Grafana (Port 3000)**: 預載 Resilience4j Dashboard，實時監控熔斷器狀態。
* **Prometheus (Port 9090)**: 採集全系統度量衡數據。

---

## 🔗 License
Distributed under the MIT License.