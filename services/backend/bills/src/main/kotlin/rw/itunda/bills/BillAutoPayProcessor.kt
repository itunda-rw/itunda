package rw.itunda.bills

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import rw.itunda.core.domain.BillAutoPaySetting
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.WalletType
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BillAutoPaySettingRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.WalletRepository
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
 *
 * **Proactive low-balance warning (Section 178, real, sourced Kakao Bank feature)**:
 * before this, every path through this class was purely reactive -- a user only ever
 * found out their auto-pay was in trouble via [notifyAutoPayFailed], *after* a real
 * attempt had already been made and had already failed against the ledger. Kakao Bank's
 * own real, currently-shipped "카드 청구금액 알림" (card billing-amount notification)
 * service does the opposite: it compares the linked account's real balance against the
 * real upcoming card-billing amount *before* the payment is attempted and proactively
 * warns "결제계좌 잔액이 부족해요" ("Your payment account balance is insufficient") so the
 * customer can top up in time (see event.kakaobank.com/p/openbankingcard and
 * kakaobank.com/products/openbankingCard for the product itself, corroborated by
 * biz.heraldcorp.com/article/3086484 covering the launch). `checkLowBalance` below is
 * itunda's honest port of that same real idea onto its own already-real auto-pay:
 * whenever a real due, uncapped bill is found for an active setting, the real current
 * wallet balance is checked *before* `billsService.payBill` is ever called -- if it's
 * already known to be insufficient, the doomed attempt is skipped entirely (no point
 * hitting the ledger just to generate the exact same "failed" outcome
 * [notifyAutoPayFailed] already covers) and a distinct proactive warning is sent
 * instead, once per real bill id (`lastLowBalanceWarnedBillId`, the identical
 * once-per-bill dedup guard `lastPaidBillId` already establishes -- otherwise every
 * 60-second poll would re-send the identical push for as long as the real balance
 * stayed insufficient). The existing reactive [notifyAutoPayFailed] path is
 * deliberately left untouched as the real backstop for the cases this proactive check
 * can't cover (no wallet at all, or a balance that changes for an unrelated reason in
 * the narrow window between this check and the real ledger attempt).
 */
@Component
class BillAutoPayProcessor(
    private val billAutoPaySettingRepository: BillAutoPaySettingRepository,
    private val billsService: BillsService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val walletRepository: WalletRepository,
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

            if (checkLowBalance(setting, pendingBill, provider.name)) continue

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

    // Returns true when the real attempt below should be skipped this poll because the
    // real current wallet balance is already known to be insufficient -- see this
    // class's own doc comment for the sourced Kakao Bank feature this ports. A missing
    // wallet is deliberately NOT treated as a low-balance case here -- that's a
    // different, rarer real failure mode already fully covered by the existing
    // NoWalletException -> notifyAutoPayFailed path below.
    private fun checkLowBalance(setting: BillAutoPaySetting, pendingBill: PendingBill, providerName: String): Boolean {
        val wallet = walletRepository.findByUserIdAndType(setting.userId, WalletType.MAIN) ?: return false
        if (wallet.availableBalance >= BigDecimal(pendingBill.amount)) return false
        if (setting.lastLowBalanceWarnedBillId == pendingBill.id) return true

        setting.lastLowBalanceWarnedBillId = pendingBill.id
        billAutoPaySettingRepository.save(setting)
        notifyLowBalance(setting.userId, providerName, BigDecimal(pendingBill.amount), wallet.availableBalance)
        return true
    }

    // Real proactive warning -- same per-user best-effort resilience
    // notifyAutoPayFailed already establishes: a notification failure here must never
    // affect the real sweep for any other setting.
    private fun notifyLowBalance(userId: String, providerName: String, billAmount: BigDecimal, availableBalance: BigDecimal) {
        try {
            val title = "Low balance for upcoming auto-pay"
            val body = "Your $providerName bill (${billAmount.toPlainString()} RWF) is due soon but your wallet only has " +
                "${availableBalance.toPlainString()} RWF. Top up before we try to auto-pay to avoid a failed payment."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = userId, type = "BILL_AUTOPAY_LOW_BALANCE",
                    title = title, body = body, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"providerName\":\"$providerName\"}",
                ),
            )
            pushNotificationService.sendToUser(userId, title, body, mapOf("providerName" to providerName))
        } catch (e: Exception) {
            log.warn("Could not send bill-autopay-low-balance notification for user {}", userId, e)
        }
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
