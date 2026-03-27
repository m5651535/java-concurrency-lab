# ADR-004：架構選型建議：Virtual Threads vs Spring WebFlux

## 狀態
已採用

## 背景

本專案透過 9 個階段的對照實驗，
在相同硬體與負載條件下對比兩種架構的效能、韌性與維護性。

## 決策

新專案優先選擇 Spring MVC + Virtual Threads，
除非有明確的記憶體限制或全鏈路響應式需求才選 WebFlux。

## 理由

1. **效能相近**：2,000 VUs IO-Bound 場景下，
   兩者平均延遲相同（~1.48s），CPU 使用率 MVC 更低（<5% vs 15~30%）。
2. **開發維護成本**：Virtual Threads 保留命令式編程風格，
   StackTrace 連續，Debug 工具完全相容，學習曲線平緩。
3. **記憶體**：WebFlux 佔用記憶體僅為 MVC 的 1/3（400MB vs 1300MB），
   在 K8s 資源受限環境有明顯優勢。
4. **併發控制複雜度**：WebFlux 的非阻塞特性會放大快取失效瞬間的衝擊
   （Thundering Herd），必須配合 RLockReactive 等精確控制，
   開發複雜度高於 MVC。

## Trade-off

選擇 Virtual Threads 犧牲了記憶體效率，
在 K8s 高密度部署場景下 Pod 數量會受限。
選擇 WebFlux 犧牲了開發維護性，
非同步鏈路的 Debug 與追蹤難度顯著提高。

## 實驗結論

Java 21 的虛擬執行緒已讓 MVC 重回高併發競爭賽道，
在大多數業務場景下是更務實的選擇。
