# ─── Stage 1: Dependency cache ───────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17-alpine AS deps
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# ─── Stage 2: Build ──────────────────────────────────────────────────────────
FROM deps AS builder
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ─── Stage 3: Extract layered JAR ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ─── Stage 4: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security hardening
RUN apk add --no-cache tzdata curl && \
    addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

WORKDIR /app

# Copy layered JAR content (best layer caching — deps change rarely)
COPY --from=extractor --chown=spring:spring /app/dependencies/ ./
COPY --from=extractor --chown=spring:spring /app/spring-boot-loader/ ./
COPY --from=extractor --chown=spring:spring /app/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /app/application/ ./

USER spring:spring

EXPOSE 8083

# Health check using Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health/liveness || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
