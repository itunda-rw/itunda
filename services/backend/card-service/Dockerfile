# Real, first independently-deployable itunda product (2026-09-01) -- see
# docs/ARCHITECTURE.md's dated follow-up to the 2026-07-11 microservices decision.
# Same two-stage pattern as services/backend/Dockerfile, but builds :card-service
# instead of :app -- Gradle only compiles :card, :card-service, :core, :auth,
# :security and their external deps, not the other ~39 product modules, which is
# the actual fix for "must rebuild the whole monolith to ship a Card-only change."
# Build context is services/backend (the whole multi-module build), same as the
# canonical Dockerfile -- NOT services/backend/card-service alone.
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :card-service:bootJar --no-daemon -x test \
  -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=256m' \
  -Dkotlin.daemon.jvm.options='-Xmx384m'

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --shell /usr/sbin/nologin itunda
COPY --from=builder /app/card-service/build/libs/*.jar app.jar
USER itunda
EXPOSE 4002
ENTRYPOINT ["java", "-jar", "app.jar"]
