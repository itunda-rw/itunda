package rw.itunda.cardservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import rw.itunda.auth.AuthController
import rw.itunda.auth.AuthService
import rw.itunda.auth.DeviceService
import rw.itunda.auth.UserVerificationService
import rw.itunda.auth.VerificationTokenCleanupScheduler

// Real, first independently-deployable itunda product (2026-09-01) -- see
// docs/ARCHITECTURE.md's dated follow-up to the 2026-07-11 microservices decision.
// Narrow scanBasePackages (unlike :app's blanket "rw.itunda") so this JVM only ever
// mounts Card's own controllers plus the shared :core/:auth/:security infrastructure
// -- never accidentally picks up an unrelated product module some future :core-level
// refactor might place under "rw.itunda".
//
// Real bug found live deploying this (2026-09-01): scanning the whole `rw.itunda.auth`
// package (needed for RateLimiter/JwtService/TokenBlocklistService, all flat in that
// same package -- see AuthService.kt's own file) also picks up AuthService itself,
// whose constructor needs a real rw.itunda.core.realtime.RealtimeMessagePublisher bean
// -- an interface real-implemented ONLY in :app (`rw.itunda.app.websocket
// .MessagingWebSocketHandler`, see that interface's own doc comment on why it's
// deliberately :core-interface/:app-implementation) -- so card-service's context
// failed to start with `UnsatisfiedDependencyException` on boot. card-service has no
// real use for AuthService/AuthController (registration/login, not Card's concern)
// or DeviceService/UserVerificationService (their own separate external-integration
// dependencies), so these are excluded rather than fabricating a fake
// RealtimeMessagePublisher stub just to satisfy DI for a bean this service never
// actually calls.
// scanBasePackages is deliberately NOT set on @SpringBootApplication itself -- a
// second, separate @ComponentScan below does the real scanning with exclusions;
// setting scanBasePackages here too would run a SECOND, unfiltered scan pass over
// the same packages (Spring collects @ComponentScan from every place it's meta-/
// directly present on this class), silently re-registering the excluded beans.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.card", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class CardServiceApplication

fun main(args: Array<String>) {
    runApplication<CardServiceApplication>(*args)
}
