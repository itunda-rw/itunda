package rw.itunda.insurance

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.WeatherIndexPolicy
import rw.itunda.core.domain.WeatherIndexPolicyStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.WeatherIndexPolicyRepository
import java.time.Instant
import java.util.UUID

/**
 * Real bug found in this feature's own build-time review (2026-08-02):
 * `WeatherIndexInsuranceService.publishSeasonIndex`'s first version wrapped its whole
 * per-district+season payout batch loop in ONE `@Transactional` method, with a
 * try/catch around each policy's payout to isolate one farmer's failure from the
 * rest. That isolation is fake: a `RuntimeException` escaping any nested
 * `@Transactional`-participating call (e.g. a genuine `ObjectOptimisticLockingFailureException`
 * from `weatherIndexPolicyRepository.save(policy)` -- entirely plausible given
 * `WeatherIndexPolicy.version` exists precisely because a farmer's own `cancel()` call
 * can race the admin's `publishSeasonIndex` batch update of that same row) marks the
 * WHOLE ambient Spring transaction rollback-only the instant it's thrown, at the
 * innermost `@Transactional` boundary -- before the surrounding try/catch even runs.
 * Catching and logging it doesn't undo that: when the outer method returns, Spring
 * still fails to commit and throws `UnexpectedRollbackException`, silently reverting
 * EVERY OTHER farmer's already-"successful" payout in the same batch, plus the newly-
 * published `SeasonRainfallIndex` row itself. This is the exact same self-invocation-
 * inside-one-@Transactional-method gotcha `P2pService.sendDirect`'s own doc comment
 * documents having to solve for `RoundUpService.processRoundUp` -- Spring's
 * transactional AOP proxy only applies across a call to a genuinely SEPARATE bean, so
 * the fix here is identical: move the per-policy work to its own real `@Service`,
 * called through its own proxy, so one policy's failure is truly isolated in its own
 * physical transaction and can never poison a sibling policy's already-committed
 * payout or the index row's own creation.
 */
@Service
class WeatherIndexPayoutExecutor(
    private val weatherIndexPolicyRepository: WeatherIndexPolicyRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(WeatherIndexPayoutExecutor::class.java)

    /**
     * Settles exactly one policy against an already-published season index, in its
     * own real, independent transaction. Returns true if a real payout was posted,
     * false if the season simply closed with no payout (not an error either way).
     */
    @Transactional
    fun settleOnePolicy(policy: WeatherIndexPolicy, droughtTriggered: Boolean): Boolean {
        if (!droughtTriggered) {
            policy.status = WeatherIndexPolicyStatus.SEASON_ENDED_NO_PAYOUT
            weatherIndexPolicyRepository.save(policy)
            // Real sibling-asymmetry fix (2026-09-13) -- InsuranceService.decideClaim
            // notifies on BOTH outcomes ("every real insurer notifies on both
            // outcomes"); this sibling payout path notified on neither.
            notifySeasonEnded(policy)
            return false
        }

        val account = accountRepository.findByUserIdAndType(policy.userId, AccountType.MAIN)
        if (account == null) {
            log.warn("Skipping weather-index payout for policy {}: user {} has no MAIN account", policy.id, policy.userId)
            return false
        }

        val memo = "Crop weather-index payout - ${policy.cropType}, ${policy.district} ${policy.season}"
        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg("insurance_claims_expense", LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, LedgerDirection.DEBIT, policy.insuredAmount, memo),
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, policy.insuredAmount, memo),
            ),
        )
        policy.status = WeatherIndexPolicyStatus.PAYOUT_TRIGGERED
        policy.payoutAt = Instant.now()
        weatherIndexPolicyRepository.save(policy)
        notifyPayoutTriggered(policy)
        return true
    }

    private fun notifyPayoutTriggered(policy: WeatherIndexPolicy) {
        val title = "Drought payout received"
        val body = "Your ${policy.cropType} crop insurance (${policy.district}, ${policy.season}) triggered a drought payout of ${policy.insuredAmount.toPlainString()} RWF, credited to your account."
        notifyPolicyholder(policy, title, body, "WEATHER_INDEX_PAYOUT_TRIGGERED")
    }

    private fun notifySeasonEnded(policy: WeatherIndexPolicy) {
        val title = "Crop insurance season ended"
        val body = "Your ${policy.cropType} crop insurance (${policy.district}, ${policy.season}) season ended with no drought trigger -- no payout was due."
        notifyPolicyholder(policy, title, body, "WEATHER_INDEX_SEASON_ENDED_NO_PAYOUT")
    }

    private fun notifyPolicyholder(policy: WeatherIndexPolicy, title: String, body: String, type: String) {
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = policy.userId, type = type,
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"policyId\":\"${policy.id}\"}",
            ),
        )
        sendPushAfterCommit(policy.userId, title, body)
    }

    private fun sendPushAfterCommit(userId: String, title: String, body: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body)
            } catch (e: Exception) {
                log.warn("Could not send weather-index payout push to user {}", userId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
