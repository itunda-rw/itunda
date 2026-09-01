package rw.itunda.analyticsservice

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

// Same real DI pitfall the other extracted services' Application classes all
// document: scanning "rw.itunda.auth" for
// RateLimiter/JwtService/TokenBlocklistService (all flat in that package) also
// picks up AuthService, whose real RealtimeMessagePublisher implementation
// lives ONLY in :app -- excluded here for the identical reason. No
// @EnableScheduling, no Kafka wiring, no LedgerService -- :analytics owns no
// @Scheduled beans and touches neither EventPublisher nor LedgerService. Note
// on auth: GET /api/v1/analytics/summary is real, deliberate
// hasRole("ADMIN") in the shared :security SecurityConfig -- unchanged by
// this extraction since that config is shared code; verify removal of this
// specific route with a real admin JWT, not just any authenticated one (a
// non-admin 403 doesn't prove the route is gone -- see
// IdentityServiceApplication's own doc comment for the full account of this
// lesson).
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.analytics", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class AnalyticsServiceApplication

fun main(args: Array<String>) {
    runApplication<AnalyticsServiceApplication>(*args)
}
