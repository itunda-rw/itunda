package rw.itunda.identityservice

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
// @EnableScheduling, no Kafka wiring, no LedgerService -- :identity owns no
// @Scheduled beans and touches neither EventPublisher nor LedgerService (real
// NIDA/KYB verification + compliance reporting, no money movement).
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.identity", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class IdentityServiceApplication

fun main(args: Array<String>) {
    runApplication<IdentityServiceApplication>(*args)
}
