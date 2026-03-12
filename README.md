# 🚀 Java Concurrency Lab: Virtual Threads vs. Reactive Streams

這是一個針對 Java 21 **Virtual Threads (Project Loom)** 與 **Spring WebFlux (Reactive)** 的效能實驗室。旨在透過物理數據監控（CPU/Memory）與極限壓測，探討傳統命令式編程在 Java 21 時代如何與響應式編程抗衡。

---

## 🏗️ 專案架構 (Multi-Module)

本專案採用 Maven 多模組架構，確保實驗變因完全隔離：

* **`lab-mvc`**: 基於 Spring Boot 3.5+ 與 Java 21，開啟虛擬執行緒支援。
* **`lab-webflux`**: 基於 Spring WebFlux 與 Netty，採用非阻塞響應式編程模型。

---

## 🛠️ 技術棧 (Tech Stack)

* **Runtime**: OpenJDK 21
* **Framework**: Spring Boot 3.5.11
* **Database**: H2 In-memory Database (JPA & R2DBC)
* **Load Testing**: [k6](https://k6.io/)
* **Monitoring**: Custom PowerShell scripts for Windows Process tracking.

---

## 🧪 實驗配置

我們模擬了典型的 **IO-Bound** 任務（1 秒的網路/資料庫延遲），並對兩個模組發動了 **2,000 VUs (Virtual Users)** 的高併發加壓測試。

### 核心配置 (lab-mvc)
```yaml
spring:
  threads:
    virtual:
      enabled: true  # 開啟 Java 21 虛擬執行緒
  datasource:
    hikari:
      maximum-pool-size: 50 # 針對高併發優化的連線池
```

---

## 📊 效能實測數據 (Benchmark Results)

在 **2,000 VUs** 同時在線的 120 秒壓測中，我們得出以下物理數據：

| 指標 | MVC (Virtual Threads) | WebFlux (Reactive) |
| :--- | :--- | :--- |
| **程式碼風格** | 命令式 (Imperative) | 聲明式 (Declarative) |
| **2000 VU 成功率** | **100%** | **100%** |
| **平均延遲 (Latency)** | ~1.48s | ~1.48s |
| **CPU 使用率 (平均)** | **< 5% (極低)** | 15% ~ 30% (較高) |
| **記憶體佔用 (RSS)** | ~1300 MB | **~400 MB (極優)** |

### 💡 核心洞察 (Key Insights)
* **Virtual Threads 的高效調度**: 虛擬執行緒在處理 IO 阻塞時，CPU 調度開銷極低，且開發難度遠低於響應式編程。
* **WebFlux 的空間優勢**: 在記憶體管理上具有壓倒性優勢，佔用空間僅為 MVC 的 1/3，極適合 K8s 等資源受限環境。
* **開發與維護性**: Virtual Threads 讓 StackTrace 回歸連續性，大幅降低了除錯（Debug）難度。

---

## 🚀 如何執行實驗

### 1. 編譯與啟動
```bash
# 編譯全專案
mvn clean install -DskipTests

# 啟動 MvcApplication (8081) 與 FluxApplication (8082)
```

### 2. 開啟資源監控 (Windows PowerShell)
```powershell
.\monitor.ps1 -MvcPid <PID> -FluxPid <PID> -Duration 120
```

### 3. 發動 k6 壓測
```bash
k6 run script.js
```

---

## 📂 工具清單
* `monitor.ps1`: Windows 原生監控腳本，紀錄 `TotalProcessorTime` 與 `WorkingSet`。
* `script.js`: k6 壓測腳本，包含 VUs 階梯加壓配置。
* `perf_report.csv`: 實驗產出的原始數據。

---

## 🔗 License
Distributed under the MIT License.
