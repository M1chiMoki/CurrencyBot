# Используем образ с Java 11 и Maven
FROM maven:3.8.6-openjdk-11 AS build
WORKDIR /app
# Копируем файлы проекта
COPY pom.xml .
COPY src ./src
# Собираем проект
RUN mvn clean package -DskipTests

# Финальный образ для запуска
FROM openjdk:11-jre-slim
WORKDIR /app
# Копируем собранный JAR
COPY --from=build /app/target/DiscordBot-1.0.jar /app/DiscordBot-1.0.jar
# Запускаем бот
CMD ["java", "-jar", "/app/DiscordBot-1.0.jar"]
