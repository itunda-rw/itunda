package rw.itunda.billsservice

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

// Same real DI pitfall CardServiceApplication.kt/InsuranceServiceApplication.kt/
// AgentsServiceApplication.kt/TransitServiceApplication.kt/
// CertificateServiceApplication.kt's own doc comments document: scanning
// "rw.itunda.auth" for RateLimiter/JwtService/TokenBlocklistService (all flat
// in that package) also picks up AuthService, whose real
// RealtimeMessagePublisher implementation lives ONLY in :app -- excluded here
// for the identical reason. No @EnableScheduling needed -- :bills owns no
// @Scheduled beans (confirmed via grep before extraction; BillAutoPayProcessor
// is a plain @Component called directly by BillsController, not scheduled).
// EventPublisher (core/.../events/EventPublisher.kt) is a real, concrete
// @Component (unlike RealtimeMessagePublisher, not an :app-only interface) --
// its KafkaTemplate autoconfigures without a live broker at boot time, same as
// :app's own local-dev behavior when Kafka is unreachable (logs warnings, only
// fails on an actual publish attempt).
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.bills", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class BillsServiceApplication

fun main(args: Array<String>) {
    runApplication<BillsServiceApplication>(*args)
}
