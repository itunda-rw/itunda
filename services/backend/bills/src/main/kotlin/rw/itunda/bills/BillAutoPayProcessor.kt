package rw.itunda.bills

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BillAutoPaySettingRepository
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Real Kakao Pay 자동납부 poll -- exposed as a manually-callable endpoint (same "expose
 * scheduler logic as a real POST" convention as `WeeklySavingsController.processDue`) so
 * this can be live-verified without waiting real wall-clock time.
 *
 * Deliberately NOT part of [BillsService] and NOT itself `@Transactional`. The loop used
 * to live on `BillsService.processAutoPayments()` and call `payBill()` on `this` --
 * self-invocation bypasses Spring's proxy, so `payBill`'s own `@Transactional` had no
 * effect and the loop's own outer `@Transactional` made the whole sweep one physical
 * transaction. Worse than the "one bad row rolls back the others" pitfall this was meant
 * to fix: `LedgerService.postLedgerTransaction` (a real, separately-proxied bean call) marks
 * that *shared* transaction rollback-only the instant one row throws, regardless of whether
 * the exception is caught afterward -- confirmed live as a real
 * `UnexpectedRollbackException` 500 on the whole endpoint. Calling `billsService.payBill()`
 * from a *different* bean here goes through `BillsService`'s real proxy, so each row gets
 * its own independent physical transaction (same effect as `Propagation.REQUIRES_NEW`,
 * without needing it) -- same "loop in a separate class, call the transactional method on
 * a different bean" structure `OrderAcceptanceExpiryScheduler`/`StockPriceAlertScheduler`/
 * `SavingsMaturityReminderScheduler` already establish for the identical class of problem.
 */
@Component
class BillAutoPayProcessor(
    private val billAutoPaySettingRepository: BillAutoPaySettingRepository,
    private val billsService: BillsService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(BillAutoPayProcessor::class.java)

    fun process(): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        for (setting in billAutoPaySettingRepository.findByActiveTrue()) {
            val provider = BillsCatalog.providers.find { it.id == setting.providerId } ?: continue
            val pendingBill = BillsCatalog.pendingBills.find {
                it.provider == provider.name && it.accountNumber == setting.accountNumber
            } ?: continue
            if (pendingBill.id == setting.lastPaidBillId) continue
            if (BigDecimal(pendingBill.amount) > setting.maxAmount) continue

            try {
                val payment = billsService.payBill(setting.userId, pendingBill.id, BigDecimal(pendingBill.amount), setting.accountNumber, null)
                setting.lastPaidBillId = pendingBill.id
                billAutoPaySettingRepository.save(setting)
                results.add(payment + mapOf("providerId" to provider.id, "billId" to pendingBill.id))
            } catch (e: Exception) {
                log.error("Auto-pay failed for user {} provider {}: {}", setting.userId, provider.id, e.message)
                notifyAutoPayFailed(setting.userId, provider.name, e.message)
            }
        }
        return results
    }

    // Real Toss Payments billing-failure alert -- same real, sourced convention
    // ProductSubscriptionService.notifyDeliveryFailed already establishes (see that
    // method's own doc comment): itunda's recurring-charge failure paths previously
    // only ever recorded the failure silently, never told the customer. Purely a
    // best-effort side effect wrapped in its own try/catch -- never allowed to affect
    // the real sweep. Safe by construction: this call lives in the loop bean, not
    // inside billsService.payBill's own @Transactional method, so a failing
    // notification save can never poison the real per-row transaction.
    private fun notifyAutoPayFailed(userId: String, providerName: String, reason: String?) {
        try {
            val title = "Auto bill-pay failed"
            val body = "We couldn't auto-pay your $providerName bill: ${reason ?: "please check your balance"}. We'll try again next time."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = userId, type = "BILL_AUTOPAY_FAILED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"providerName\":\"$providerName\"}",
                ),
            )
            pushNotificationService.sendToUser(userId, title, body, mapOf("providerName" to providerName))
        } catch (e: Exception) {
            log.warn("Could not send bill-autopay-failure notification for user {}", userId, e)
        }
    }
}
