# Same shared-root build as ledger-service/Dockerfile -- build context is the
# microservices root:
#   docker build -f services/microservices/payment-service/Dockerfile -t itunda/payment-service services/microservices
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :payment-service:payment-api:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin itunda
COPY --from=builder /app/payment-service/payment-api/build/libs/*.jar app.jar
USER itunda
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
