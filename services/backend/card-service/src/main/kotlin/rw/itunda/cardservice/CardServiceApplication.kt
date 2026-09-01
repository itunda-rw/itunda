package rw.itunda.cardservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

// Real, first independently-deployable itunda product (2026-09-01) -- see
// docs/ARCHITECTURE.md's dated follow-up to the 2026-07-11 microservices decision.
// Narrow scanBasePackages (unlike :app's blanket "rw.itunda") so this JVM only ever
// mounts Card's own controllers plus the shared :core/:auth/:security infrastructure
// -- never accidentally picks up an unrelated product module some future :core-level
// refactor might place under "rw.itunda".
@SpringBootApplication(scanBasePackages = ["rw.itunda.card", "rw.itunda.core", "rw.itunda.auth", "rw.itunda.security"])
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class CardServiceApplication

fun main(args: Array<String>) {
    runApplication<CardServiceApplication>(*args)
}
