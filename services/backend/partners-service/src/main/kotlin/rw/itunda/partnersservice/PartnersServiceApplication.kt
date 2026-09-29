package rw.itunda.partnersservice

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
// @EnableScheduling and no Kafka wiring needed -- :partners owns no
// @Scheduled beans and touches neither EventPublisher nor LedgerService.
//
// Note on auth: /api/v1/partners/** is real, deliberate permitAll in the
// shared :security SecurityConfig -- partner-facing endpoints authenticate
// with a real X-Api-Key header checked inside PartnerService.resolvePartner,
// not a itunda-user JWT (a partner has no itunda user account). This is
// unchanged by the extraction since SecurityConfig is shared code; every
// endpoint but /register still real-401s without a valid key, just via
// PartnerService's own check rather than JwtAuthenticationFilter.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.partners", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class PartnersServiceApplication

fun main(args: Array<String>) {
    runApplication<PartnersServiceApplication>(*args)
}
