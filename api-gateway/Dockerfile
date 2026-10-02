# Build context = backend/ (see docker/docker-compose.apps.yml), same layout as the other services

# Stage 1: BUILD
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

COPY mvnw .
COPY .mvn/ .mvn/
RUN chmod +x mvnw

COPY pom.xml .
COPY common/ common/
COPY api-gateway/ api-gateway/
COPY identity-service/ identity-service/
COPY user-service/ user-service/
COPY course-service/ course-service/
COPY enrollment-service/ enrollment-service/
COPY payment-service/ payment-service/
COPY notification-service/ notification-service/
COPY assessment-service/ assessment-service/

RUN ./mvnw dependency:go-offline -B

# Install root POM into local Maven cache ("N" = just install POM, not build source/test)
RUN ./mvnw install -N -DskipTests -B

# Install common (shared module)
RUN ./mvnw clean install -pl common -DskipTests -B

# Package api-gateway
RUN ./mvnw clean package -pl api-gateway -DskipTests -B

# Stage 2: RUNTIME
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S learnhub && adduser -S learnhub -G learnhub
USER learnhub

COPY --from=builder /app/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
