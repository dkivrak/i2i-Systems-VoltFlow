FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install --no-install-recommends -y wget \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 voltwise
COPY --from=build /workspace/target/voltwise-core-*.jar app.jar
USER voltwise
EXPOSE 8080
ENTRYPOINT ["java", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-jar", "/app/app.jar"]
