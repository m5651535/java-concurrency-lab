# ADR-003：Circuit Breaker 保護範圍的精準定位

## 狀態
已採用

## 背景

第九階段初始版本將 @CircuitBreaker 標註於 Controller 層，
涵蓋了分散式鎖的等待時間。
壓測發現 99.9% 的請求被 Circuit Breaker 直接拒絕。

## 根因

鎖競爭導致的 Mono.delay() 重試時間累積超過 Controller 層的 timeout(2s)，
TimeoutException 被 CB 統計為 DB 失敗，
10 次滑動窗口內失敗率超過 50% 觸發熔斷，
所有後續請求走 Fallback 不進快取也不進 DB。

## 決策

將 DB 存取抽離至獨立的 UserDbService，
@CircuitBreaker 只保護真正的資料庫呼叫，不包含鎖等待邏輯。

## 理由

CB 的滑動窗口統計的是「被保護範圍內的失敗率」。
若保護範圍過大，鎖競爭、網路抖動等非 DB 因素都會污染失敗率，
導致系統完全健康時觸發誤熔斷。

## 注意

@CircuitBreaker 依賴 Spring AOP Proxy，
無法攔截同一 Bean 內部的 private method 呼叫。
必須將需要被保護的邏輯抽離至獨立的 Spring Bean。

## 實驗結果

修正後吞吐量提升 47%，平均延遲下降 40%。
