# Real, seventh independently-deployable itunda product (2026-09-01) -- see
# docs/ARCHITECTURE.md's dated follow-up. Same two-stage pattern as
# services/backend/card-service/Dockerfile, but builds :vehicle-service --
# Gradle only compiles :vehicle, :vehicle-service, :core, :auth, :security and
# their external deps, not the other product modules.
# Build context is services/backend (the whole multi-module build), same as the
# canonical Dockerfile -- NOT services/backend/vehicle-service alone.
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :vehicle-service:bootJar --no-daemon -x test \
  -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=256m' \
  -Dkotlin.daemon.jvm.options='-Xmx384m'

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin itunda
COPY --from=builder /app/vehicle-service/build/libs/*.jar app.jar
USER itunda
EXPOSE 4008
ENTRYPOINT ["java", "-jar", "app.jar"]
