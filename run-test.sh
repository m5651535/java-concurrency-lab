#!/bin/bash

echo "📦 正在編譯 Java 專案..."
mvn clean package -DskipTests

echo "🚀 正在重啟環境 (含清空數據)..."
docker-compose -f docker/docker-compose.yml down -v
docker-compose -f docker/docker-compose.yml up -d --build

# 核心優化：同時監控兩個容器的日誌
echo "⏳ 等待雙模組緩存預熱完成 (MVC: 8081 & Flux: 8082)..."

# 建立一個臨時日誌串流，同時讀取兩個容器
(docker compose -f docker/docker-compose.yml logs -f lab-flux-container lab-mvc-container &) | grep -q "緩存預熱完成"

echo "🔥 雙模組偵測到預熱訊號！準備執行對比測試..."
sleep 1 # 給 Tomcat/Netty 最後的緩衝時間

# 執行 WebFlux 壓測
echo "📊 [Step 1] 壓測 WebFlux (8082)..."
k6 run -e TARGET_PORT=8082 stress_test.js

# 執行 MVC 壓測
echo "📊 [Step 2] 壓測 MVC (8081)..."
k6 run -e TARGET_PORT=8081 stress_test.js

echo "✅ 所有對比測試流程結束。"