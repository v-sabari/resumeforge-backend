# ─────────────────────────────────────────────────────────────────────────────
# ResumeForge AI Backend — Production Dockerfile
# Multi-stage build: compile on JDK, run on lean JRE, non-root user
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Cache Maven dependencies layer separately from source code.
# This layer only re-downloads when pom.xml changes.
COPY pom.xml .
RUN apk add --no-cache maven \
    && mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Security: never run as root in production
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Set correct ownership
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# JVM tuning for container environments:
#   -XX:+UseContainerSupport        — respect cgroup memory/CPU limits
#   -XX:MaxRAMPercentage=75.0       — use up to 75% of container RAM for heap
#   -XX:+OptimizeStringConcat       — string performance
#   -Djava.security.egd=...         — faster SecureRandom (avoids /dev/random blocking)
#   -Dspring.profiles.active=prod   — activate production Spring profile
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+OptimizeStringConcat", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]

# Health check — Docker and orchestrators (Railway, Render, ECS) use this
# to determine when the container is ready to receive traffic.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
