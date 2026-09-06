package rw.itunda.loansservice

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

// Real, fourteenth independently-deployable itunda product (2026-09-06) -- see
// this module's own build.gradle.kts doc comment for why :loans (unlike
// :account/:savings) was safe to extract the same way :card was. Narrow
// scanBasePackages (unlike :app's blanket "rw.itunda") so this JVM only ever
// mounts Loans' own controllers plus the shared :core/:auth/:security
// infrastructure -- never accidentally picks up an unrelated product module some
// future :core-level refactor might place under "rw.itunda".
//
// Same real bug class card-service's own doc comment describes (2026-09-01):
// scanning the whole rw.itunda.auth package (needed for RateLimiter -- real
// anti-spam limit on harvest-advance requests, see loans/build.gradle.kts's own
// doc comment) also picks up AuthService itself, whose constructor needs a real
// rw.itunda.core.realtime.RealtimeMessagePublisher bean implemented ONLY in :app
// -- so these are excluded rather than fabricating a fake stub for a bean this
// service never actually calls.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.loans", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class LoansServiceApplication

fun main(args: Array<String>) {
    runApplication<LoansServiceApplication>(*args)
}
