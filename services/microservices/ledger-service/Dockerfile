# services/microservices is one Gradle root (settings.gradle.kts includes both
# ledger-service:* and payment-service:* modules plus core-libs), so this image's
# build context is the microservices root, not this directory:
#   docker build -f services/microservices/ledger-service/Dockerfile -t itunda/ledger-service services/microservices
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :ledger-service:ledger-api:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin itunda
COPY --from=builder /app/ledger-service/ledger-api/build/libs/*.jar app.jar
USER itunda
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
