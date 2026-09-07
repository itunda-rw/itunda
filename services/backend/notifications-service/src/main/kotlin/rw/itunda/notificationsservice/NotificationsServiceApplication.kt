package rw.itunda.notificationsservice

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
import rw.itunda.core.notifications.NotificationCleanupScheduler

// Same real DI pitfall the other extracted services' Application classes all
// document: scanning "rw.itunda.auth" for
// RateLimiter/JwtService/TokenBlocklistService (all flat in that package) also
// picks up AuthService, whose real RealtimeMessagePublisher implementation
// lives ONLY in :app -- excluded here for the identical reason. No Kafka
// wiring, no LedgerService -- :notifications touches neither EventPublisher
// nor LedgerService. Scheduling IS enabled now (2026-09-07): every @Scheduled
// bean in this backend still only ever runs inside :app (the sole
// @EnableScheduling class), so NotificationCleanupScheduler -- like every
// other extracted service's own :core/:auth scheduler -- is excluded here too,
// alongside VerificationTokenCleanupScheduler.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.notifications", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class, NotificationCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class NotificationsServiceApplication

fun main(args: Array<String>) {
    runApplication<NotificationsServiceApplication>(*args)
}
