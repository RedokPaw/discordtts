# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app

# Кэшируем зависимости отдельно от исходников
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B


# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/*-jar-with-dependencies.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]