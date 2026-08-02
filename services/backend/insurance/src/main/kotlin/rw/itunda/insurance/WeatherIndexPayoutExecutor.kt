package rw.itunda.insurance

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.WalletType
import rw.itunda.core.domain.WeatherIndexPolicy
import rw.itunda.core.domain.WeatherIndexPolicyStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.repository.WeatherIndexPolicyRepository
import java.time.Instant

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
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
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
            return false
        }

        val wallet = walletRepository.findByUserIdAndType(policy.userId, WalletType.MAIN)
        if (wallet == null) {
            log.warn("Skipping weather-index payout for policy {}: user {} has no MAIN wallet", policy.id, policy.userId)
            return false
        }

        val memo = "Crop weather-index payout - ${policy.cropType}, ${policy.district} ${policy.season}"
        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("insurance_claims_expense", LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, LedgerDirection.DEBIT, policy.insuredAmount, memo),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, policy.insuredAmount, memo),
            ),
        )
        policy.status = WeatherIndexPolicyStatus.PAYOUT_TRIGGERED
        policy.payoutAt = Instant.now()
        weatherIndexPolicyRepository.save(policy)
        return true
    }
}
