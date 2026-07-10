package rw.itunda.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(scanBasePackages = ["rw.itunda"])
@EntityScan("rw.itunda.core")
@EnableJpaRepositories("rw.itunda.core")
class ItundaApplication

fun main(args: Array<String>) {
    runApplication<ItundaApplication>(*args)
}

/** Kotlin-backend equivalent of Express's GET /health. */
@RestController
class HealthController {
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok", "message" to "itunda Kotlin API is running")
}
