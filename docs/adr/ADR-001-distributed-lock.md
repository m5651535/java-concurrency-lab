# ADR-001：分散式鎖選型：Redisson vs 自實作 Lua Script

## 狀態
已採用

## 背景

在第六階段實驗中，需要保護熱點快取失效時的資料庫連線池，
防止大量請求同時穿透快取直接打到 DB（Cache Breakdown）。
需要一個分散式鎖確保同一時間只有一個請求去查 DB 並回填快取。

## 決策

選擇 Redisson，放棄自行用 Redis SET NX EX 實作。

## 理由

1. **Watchdog 機制**：Redisson 會自動幫持有鎖的執行緒續期，
   避免執行時間超過 TTL 導致鎖被搶走後出現兩個執行緒同時持有鎖的競態條件。
   自實作 SET NX EX 沒有這個保護。
2. **Reactive 支援**：Redisson 提供 RLockReactive，
   讓 WebFlux 的非阻塞鏈路也能以掛起而非阻塞的方式等鎖，
   不會讓 Event Loop 卡死。
3. **可重入鎖**：避免同一執行緒重複獲取同一把鎖時發生死鎖。

## Trade-off

引入 Redisson 增加了依賴複雜度，
且 Watchdog 的預設續期間隔（30s）在極端場景下可能產生較高的長尾延遲。
實驗數據中 Resilient 版的最大延遲（2.57s）高於 Simple 版（1.48s）即為此原因。

## 實驗結果

DB 活躍連線數從 O(N) 降至 O(1)，
p(95) 延遲從 704ms 降至 514ms，
效能達標率（<1s）從 99.39% 提升至 99.83%。
