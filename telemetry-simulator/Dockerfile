FROM eclipse-temurin:17-jre
RUN groupadd --system voltflow \
    && useradd --system --uid 10001 --gid voltflow voltflow
WORKDIR /app
COPY target/telemetry-simulator-*.jar app.jar
USER voltflow
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
