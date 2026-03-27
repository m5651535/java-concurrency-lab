# Architecture Decision Records

本目錄記錄 Java Concurrency Lab 中的關鍵架構決策。
每份 ADR 說明「為什麼選這個，放棄了什麼，trade-off 是什麼」。

| 編號 | 標題 | 狀態 |
|:---|:---|:---|
| ADR-001 | 分散式鎖選型：Redisson vs 自實作 | 已採用 |
| ADR-002 | 雙層快取：Caffeine (L1) + Redis (L2) | 已採用 |
| ADR-003 | Circuit Breaker 保護範圍的精準定位 | 已採用 |
| ADR-004 | Virtual Threads vs WebFlux 選型建議 | 已採用 |
