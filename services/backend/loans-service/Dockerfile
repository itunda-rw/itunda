# Real, fourteenth independently-deployable itunda product (2026-09-06) -- see
# docs/ARCHITECTURE.md's dated follow-up. Same two-stage pattern as
# services/backend/card-service/Dockerfile, but builds :loans-service instead --
# Gradle only compiles :loans, :loans-service, :core, :auth, :security and their
# external deps, not the other product modules. Build context is services/backend
# (the whole multi-module build), same as the canonical Dockerfile -- NOT
# services/backend/loans-service alone.
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :loans-service:bootJar --no-daemon -x test \
  -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=256m' \
  -Dkotlin.daemon.jvm.options='-Xmx384m'

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin itunda
COPY --from=builder /app/loans-service/build/libs/*.jar app.jar
USER itunda
EXPOSE 4015
ENTRYPOINT ["java", "-jar", "app.jar"]
