package rw.itunda.insuranceservice

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

// Same real DI pitfall CardServiceApplication.kt's own doc comment documents:
// scanning "rw.itunda.auth" for RateLimiter/JwtService/TokenBlocklistService (all
// flat in that package) also picks up AuthService, whose real
// RealtimeMessagePublisher implementation lives ONLY in :app -- excluded here for
// the identical reason. scanBasePackages is deliberately NOT set on
// @SpringBootApplication itself, since that would run a second, unfiltered scan
// pass over the same packages.
//
// @EnableScheduling is required here (unlike CardServiceApplication, which has no
// scheduled jobs) -- InsurancePolicyRenewalReminderScheduler and
// InsurancePremiumScheduler are real @Component/@Scheduled beans that must keep
// running once :insurance is removed from the :app monolith's own component scan.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.insurance", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
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
class InsuranceServiceApplication

fun main(args: Array<String>) {
    runApplication<InsuranceServiceApplication>(*args)
}
