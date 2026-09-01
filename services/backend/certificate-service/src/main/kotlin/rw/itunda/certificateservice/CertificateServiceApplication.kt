package rw.itunda.certificateservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import rw.itunda.auth.AuthController
import rw.itunda.auth.AuthService
import rw.itunda.auth.DeviceService
import rw.itunda.auth.UserVerificationService
import rw.itunda.auth.VerificationTokenCleanupScheduler

// Same real DI pitfall CardServiceApplication.kt/InsuranceServiceApplication.kt/
// AgentsServiceApplication.kt/TransitServiceApplication.kt's own doc comments
// document: scanning "rw.itunda.auth" for
// RateLimiter/JwtService/TokenBlocklistService (all flat in that package) also
// picks up AuthService, whose real RealtimeMessagePublisher implementation
// lives ONLY in :app -- excluded here for the identical reason.
//
// @EnableScheduling is required here (like insurance-service, unlike
// agents-service/transit-service) -- CertificateRenewalReminderScheduler is a
// real @Component/@Scheduled bean that must keep running once :certificate is
// removed from the :app monolith's own component scan. Note also:
// /api/v1/certificate/verify and /api/v1/certificate/status/** are permitAll
// in the shared :security SecurityConfig (third-party certificate
// verification needs no auth) -- unchanged by this extraction, since
// SecurityConfig is shared code, not duplicated.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.certificate", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
@EnableScheduling
class CertificateServiceApplication

fun main(args: Array<String>) {
    runApplication<CertificateServiceApplication>(*args)
}
