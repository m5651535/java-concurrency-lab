# 使用輕量級 JRE 21
FROM eclipse-temurin:21-jre-alpine

# 設定工作目錄
WORKDIR /app

# 複製編譯好的 jar (請確認檔名是否正確)
COPY target/*.jar app.jar

# 預留 8082 (服務) 與 5005 (Debug) 埠號
EXPOSE 8082 5005

# 啟動指令：加入 JDWP 參數支援遠端除錯
ENTRYPOINT ["java", \
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
            "-jar", "app.jar"]