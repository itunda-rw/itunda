package rw.itunda.ledger.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

// scanBasePackages widens regular @Component scanning to cover ledger-domain/ledger-db,
// but Spring Data JPA's repository- and entity-scanning are separate auto-configurations
// that otherwise default to only this class's own package (rw.itunda.ledger.api) --
// missing ledger-db's rw.itunda.ledger.db.repository/entity packages entirely. Found via
// this session's first-ever live boot: "Found 0 JPA repository interfaces" at startup,
// then a NoSuchBeanDefinitionException for LedgerAccountJpaRepository.
@SpringBootApplication(scanBasePackages = ["rw.itunda.ledger"])
@EnableJpaRepositories(basePackages = ["rw.itunda.ledger.db.repository"])
@EntityScan(basePackages = ["rw.itunda.ledger.db.entity"])
class LedgerApplication

fun main(args: Array<String>) {
    runApplication<LedgerApplication>(*args)
}
