FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
RUN groupadd --system voltwise \
    && useradd --system --uid 10001 --gid voltwise voltwise
WORKDIR /app
COPY --from=build /workspace/target/telemetry-simulator-*.jar app.jar
USER voltwise
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
