FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 必須要有這一行，Docker 才會接收外面傳進來的變數
ARG MODULE_NAME

# 這裡路徑不要加斜線在最前面，直接寫變數
COPY ${MODULE_NAME}/target/*.jar app.jar

EXPOSE 8081 8082 5005

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]