package rw.itunda.overviewservice

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
import rw.itunda.overview.NetWorthSnapshotScheduler

// Same real DI pitfall the other extracted services' Application classes all
// document: scanning "rw.itunda.auth" for
// RateLimiter/JwtService/TokenBlocklistService (all flat in that package) also
// picks up AuthService, whose real RealtimeMessagePublisher implementation
// lives ONLY in :app -- excluded here for the identical reason. No
// @EnableScheduling, no Kafka wiring, no LedgerService -- :overview touches
// neither EventPublisher nor LedgerService (pure read-only aggregation across
// :core repositories + linked-account CRUD). :overview DOES now own one real
// @Scheduled bean (NetWorthSnapshotScheduler, 2026-09-12) -- same "every
// @Scheduled bean in this backend still only ever runs inside :app, the sole
// @EnableScheduling class" convention every other extracted service's Application
// class already documents (see notifications-service's identical exclusion of
// its own NotificationCleanupScheduler), so it's excluded here too rather than
// left to double-register if this service ever gains its own scheduling.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.overview", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class, NetWorthSnapshotScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class OverviewServiceApplication

fun main(args: Array<String>) {
    runApplication<OverviewServiceApplication>(*args)
}
