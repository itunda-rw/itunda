package rw.itunda.vehicleservice

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
// CertificateServiceApplication.kt/BillsServiceApplication.kt's own doc
// comments document: scanning "rw.itunda.auth" for
// RateLimiter/JwtService/TokenBlocklistService (all flat in that package) also
// picks up AuthService, whose real RealtimeMessagePublisher implementation
// lives ONLY in :app -- excluded here for the identical reason. No
// @EnableScheduling needed -- :vehicle owns no @Scheduled beans (confirmed via
// grep before extraction). No Kafka wiring needed either -- VehicleValuationService
// touches no EventPublisher/LedgerService.
@SpringBootApplication
@ComponentScan(
    basePackages = ["rw.itunda.vehicle", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [AuthController::class, AuthService::class, DeviceService::class, UserVerificationService::class, VerificationTokenCleanupScheduler::class],
        ),
    ],
)
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class VehicleServiceApplication

fun main(args: Array<String>) {
    runApplication<VehicleServiceApplication>(*args)
}
