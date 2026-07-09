package com.itunda.payment.infrastructure

import com.itunda.payment.application.PaymentGatewayPort
import org.springframework.stereotype.Component

@Component
class RnpPaymentGateway : PaymentGatewayPort {

    override fun processMobileMoneyTransfer(paymentKey: String, amount: Long): Boolean {
        // Simulate RNP 2.0 (Rwanda National Digital Payment System) integration
        println("[RNP Gateway] Processing real-time transfer...")
        println("[RNP Gateway] PaymentKey: $paymentKey, Amount: $amount RWF")
        
        // Simulating Toss-style instant transfer success
        Thread.sleep(200) // Mock latency
        
        println("[RNP Gateway] Transfer successful via RNP infrastructure.")
        return true
    }
}
