#!/bin/bash

# 1. 在根目錄編譯所有模組
echo "📦 正在編譯 Java 專案..."
mvn clean package -DskipTests

# 2. 指向 docker 資料夾內的 compose 檔案進行部署
echo "🚀 正在重啟環境 (含清空數據)..."
# 使用 -f 指定檔案路徑
docker-compose -f docker/docker-compose.yml down -v
docker-compose -f docker/docker-compose.yml up -d --build

# 3. 監控 WebFlux 容器日誌
echo "⏳ 等待緩存預熱完成訊號..."
# 注意：容器名稱需與 docker-compose.yml 定義的一致
(docker logs -f lab-flux-container &) | grep -q "✅ 緩存預熱完成"

# 4. 偵測到訊號後立刻執行壓測 (假設 k6 腳本在根目錄)
echo "🔥 偵測到預熱完成！立刻發動 k6 衝擊測試..."
k6 run stress_test.js

echo "✅ 測試流程結束。"