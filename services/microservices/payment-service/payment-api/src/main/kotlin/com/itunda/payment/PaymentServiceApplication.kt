package com.itunda.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(basePackages = ["com.itunda", "rw.itunda"])
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}

@org.springframework.context.annotation.Configuration
class PaymentDomainConfig {
    @org.springframework.context.annotation.Bean
    fun confirmPaymentUseCase(
        paymentRepositoryPort: com.itunda.payment.application.PaymentRepositoryPort,
        paymentGatewayPort: com.itunda.payment.application.PaymentGatewayPort
    ): com.itunda.payment.application.ConfirmPaymentUseCase {
        return com.itunda.payment.application.ConfirmPaymentService(paymentRepositoryPort, paymentGatewayPort)
    }
}
