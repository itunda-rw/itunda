package com.itunda.payment.infrastructure

import com.itunda.payment.application.PaymentGatewayPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Port for talking to RNP 2.0 (Rwanda National Digital Payment System) / MTN MoMo /
 * Airtel Money. No real integration exists yet -- that needs real provider
 * credentials and certification, which is outside what a code change alone can
 * deliver (see docs/TOSS_PARITY_MATRIX.md's "Non-Negotiable Gates Before Real Money").
 *
 * Fixed (2026-07-11): this previously always returned `true` after a fake 200ms
 * sleep, unconditionally claiming every transfer succeeded -- the same fake-success
 * pattern already found and fixed in services/backend's wallet/insurance modules
 * this session. Now fails loudly instead of lying: with no real endpoint configured
 * (the default in every environment today), it throws rather than claiming success.
 * ConfirmPaymentService's domain logic already correctly handles a real `false`
 * return (marks the payment ABORTED) -- that path simply was never reachable
 * because this adapter never returned it.
 */
@Component
class RnpPaymentGateway(
    @Value("\${rnp.gateway.base-url:}") private val baseUrl: String,
) : PaymentGatewayPort {
    private val log = LoggerFactory.getLogger(RnpPaymentGateway::class.java)

    override fun processMobileMoneyTransfer(paymentKey: String, amount: Long): Boolean {
        if (baseUrl.isBlank()) {
            log.warn(
                "No real MTN MoMo/Airtel Money/RNP integration configured " +
                    "(rnp.gateway.base-url unset) -- refusing to fake a successful " +
                    "transfer of {} RWF for paymentKey={}.", amount, paymentKey,
            )
            throw RnpGatewayNotConfiguredException(
                "No real mobile-money provider is configured for this environment. " +
                    "This transfer was not attempted, not silently approved.",
            )
        }

        // A real endpoint is configured but no actual HTTP client call is implemented
        // here yet -- that requires real provider credentials and API contracts this
        // repo does not have. Fail rather than silently proceeding as if it worked.
        throw RnpGatewayNotConfiguredException(
            "rnp.gateway.base-url is set to '$baseUrl' but no real HTTP integration " +
                "is implemented yet.",
        )
    }
}

class RnpGatewayNotConfiguredException(message: String) : RuntimeException(message)
