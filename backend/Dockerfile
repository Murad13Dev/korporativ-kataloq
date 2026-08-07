# =============================================================================
# Stage 1 — build
# Dependencies are resolved in their own layer so a source-only change does not
# re-download the whole Maven repository on every rebuild.
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests \
    && mv target/*.jar target/app.jar


# =============================================================================
# Stage 2 — runtime
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache wget tzdata \
    && addgroup -S app \
    && adduser -S -G app -H app

ENV TZ=Asia/Baku \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

COPY --from=build /build/target/app.jar app.jar

# Uploads live on a volume; create it up front so the non-root user owns it.
RUN mkdir -p /app/data/uploads && chown -R app:app /app

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
