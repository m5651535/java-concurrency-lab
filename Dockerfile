FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG MODULE_NAME

# 只抓取大於 10MB 的 JAR (排除掉幾 KB 的 plain jar)
# 或者更精確地指定排除 plain
COPY ${MODULE_NAME}/target/*-SNAPSHOT.jar app.jar

EXPOSE 8081 8082 5005

# 使用 shell 形式，這樣才能正確解析環境變數
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]