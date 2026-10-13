package com.itunda.payment.infrastructure

import com.itunda.payment.application.PaymentGatewayPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * RNP 2.0 / MTN MoMo / Airtel Money adapter boundary.
 *
 * No real provider integration exists yet. Credentials, contract-specific request
 * signing, provider idempotency support, callback verification and certification are
 * required before this adapter can move money.
 *
 * This implementation intentionally fails closed even when a base URL is supplied:
 * a configured URL is not proof that a working provider integration exists.
 */
@Component
class RnpPaymentGateway(
    @Value("\${rnp.gateway.base-url:}") private val baseUrl: String,
) : PaymentGatewayPort {
    private val log = LoggerFactory.getLogger(RnpPaymentGateway::class.java)

    override fun processMobileMoneyTransfer(
        paymentKey: String,
        amount: Long,
        idempotencyKey: String
    ): Boolean {
        log.warn(
            "Mobile-money provider integration is unavailable; refusing payment. " +
                "configuredBaseUrl={}, amount={}, paymentKey={}, idempotencyKey={}",
            baseUrl.isNotBlank(), amount, paymentKey, idempotencyKey,
        )
        throw RnpGatewayNotConfiguredException(
            "No certified mobile-money provider integration is implemented. " +
                "This transfer was not attempted or approved."
        )
    }
}

class RnpGatewayNotConfiguredException(message: String) : RuntimeException(message)
