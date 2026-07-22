FROM eclipse-temurin:17-jre
RUN groupadd --system voltwise \
    && useradd --system --uid 10001 --gid voltwise voltwise
WORKDIR /app
COPY target/telemetry-simulator-*.jar app.jar
USER voltwise
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
