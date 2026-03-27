FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG MODULE_NAME

# 複製指定模組的 JAR 檔案到容器中
# 這裡使用通配符來匹配 Maven 產出的 fat jar，並確保排除 plain jar
COPY ${MODULE_NAME}/target/${MODULE_NAME}-*.jar app.jar

EXPOSE 8081 8082 5005

# 使用 shell 形式，這樣才能正確解析環境變數
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]