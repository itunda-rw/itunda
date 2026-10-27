package com.itunda.payment.infrastructure

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RnpPaymentGatewayTest {
    @Test
    fun `blank endpoint refuses transfer instead of reporting success`() {
        val gateway = RnpPaymentGateway("")

        val error = assertFailsWith<RnpGatewayNotConfiguredException> {
            gateway.processMobileMoneyTransfer("test-payment", 7_500L)
        }

        assertTrue(error.message.orEmpty().contains("No real mobile-money provider"))
        assertTrue(error.message.orEmpty().contains("not silently approved"))
    }

    @Test
    fun `configured endpoint still refuses transfer until real HTTP integration exists`() {
        val gateway = RnpPaymentGateway("https://provider.example.test")

        val error = assertFailsWith<RnpGatewayNotConfiguredException> {
            gateway.processMobileMoneyTransfer("test-payment", 7_500L)
        }

        assertTrue(error.message.orEmpty().contains("no real HTTP integration"))
        assertTrue(error.message.orEmpty().contains("provider.example.test"))
    }
}
