FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
RUN groupadd --system voltwise \
    && useradd --system --uid 10001 --gid voltwise voltwise
WORKDIR /app
COPY --from=build /src/target/telemetry-simulator-*.jar app.jar
USER voltwise
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
