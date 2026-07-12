# Canonical Kotlin/Spring Boot backend (services/backend) -- builds the :app module,
# which is the only module in this multi-module build that actually assembles a
# runnable jar (see settings.gradle.kts / app/build.gradle.kts). The two Dockerfiles
# that previously lived under core/ and wallet/ built the wrong module entirely and
# published to port 8080, not the 4001 every k8s manifest/gateway route assumes --
# removed in favor of this one, correct build.
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :app:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin itunda
COPY --from=builder /app/app/build/libs/*.jar app.jar
USER itunda
EXPOSE 4001
ENTRYPOINT ["java", "-jar", "app.jar"]
