# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/health-diary.jar app.jar

RUN mkdir -p /app/data
ENV DB_PATH=/app/data/health_diary.db
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
