# syntax=docker/dockerfile:1

# ---- build -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first: this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package \
    && cp target/nimokids-api-*.jar app.jar

# ---- runtime ---------------------------------------------------------------
FROM eclipse-temurin:21-jre

# curl is only used by the HEALTHCHECK.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home nimokids

WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER nimokids

# Port standard: project 85 -> API 8510. SERVER_PORT must be passed by the environment (compose does it) so the
# container always listens on the allocated port; the value below is only a safe default.
ENV SERVER_PORT=8510 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8510

# game-modes is public and touches the database, so "healthy" means the app AND its database connection work.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/v1/game-modes" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
