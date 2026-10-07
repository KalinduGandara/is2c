# syntax=docker/dockerfile:1
# One image for both editions (NFR-001): the React SPA is built first and embedded in the Spring Boot jar (D8).
# Build from the repository root:  docker build -f deploy/Dockerfile -t is2c/hrm:<version> .

FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS api
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q -B dependency:go-offline -DexcludeGroupIds=io.zonky.test,io.zonky.test.postgres || true
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -q -B -DskipTests package && cp target/hrm-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S hrm && adduser -S hrm -G hrm && mkdir -p /data/files && chown -R hrm:hrm /data
USER hrm
WORKDIR /app
COPY --from=api /app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    HRM_STORAGE_PATH=/data/files \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
VOLUME /data/files
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
