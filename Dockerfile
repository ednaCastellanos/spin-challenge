# ── Stage 1: build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Capa cacheable: las dependencias sólo se re-descargan si cambia el pom.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: extracción de capas ────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /extract
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 3: runtime ────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S -g 1000 spin && adduser -S -u 1000 spin -G spin && \
    apk add --no-cache curl

# Orden por volatilidad: lo que menos cambia, primero.
COPY --from=extractor --chown=spin:spin /extract/dependencies/           ./
COPY --from=extractor --chown=spin:spin /extract/spring-boot-loader/     ./
COPY --from=extractor --chown=spin:spin /extract/snapshot-dependencies/  ./
COPY --from=extractor --chown=spin:spin /extract/application/            ./

USER spin
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError \
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fs http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]