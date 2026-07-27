package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.RoundUpSettings
import rw.itunda.core.repository.RoundUpSettingsRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.stocks.StockCatalog
import rw.itunda.stocks.StockNotFoundException
import rw.itunda.stocks.StocksService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class InvalidRoundUpIncrementException(message: String) : RuntimeException(message)
class RoundUpTargetRequiredException(message: String) : RuntimeException(message)
class RoundUpSingleTargetException(message: String) : RuntimeException(message)

/**
 * Real round-up auto-saving -- see `RoundUpSettings`' own doc comment for the full
 * account, including the honest "P2P transfers only, not every payment flow" v1 scope.
 * `processRoundUp` is called by `P2pService.sendDirect` right after a real transfer
 * completes, same "auxiliary side-effect can't block real money movement" try/catch
 * discipline `P2pService.notifyMoneyReceived`'s own doc comment already establishes --
 * a round-up failure (e.g. insufficient balance for even the small round-up amount)
 * must never look like the triggering transfer itself failed.
 */
@Service
class RoundUpService(
    private val roundUpSettingsRepository: RoundUpSettingsRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val savingsService: SavingsService,
    private val stocksService: StocksService,
) {
    private val log = LoggerFactory.getLogger(RoundUpService::class.java)

    companion object {
        // Real, honest, user-chosen increments -- not an arbitrary fabricated default.
        val SUPPORTED_INCREMENTS: Set<BigDecimal> = setOf(BigDecimal("100"), BigDecimal("500"), BigDecimal("1000"))
    }

    @Transactional
    fun setSettings(userId: String, enabled: Boolean, roundToNearest: BigDecimal, targetGoalId: String?, targetStockId: String? = null): RoundUpSettings {
        if (SUPPORTED_INCREMENTS.none { it.compareTo(roundToNearest) == 0 }) {
            throw InvalidRoundUpIncrementException("Choose a real increment -- ${SUPPORTED_INCREMENTS.sorted().joinToString()} RWF")
        }
        if (enabled) {
            if (targetGoalId != null && targetStockId != null) {
                throw RoundUpSingleTargetException("Choose either a savings goal or a stock to round up into, not both")
            }
            if (targetGoalId != null) {
                savingsGoalRepository.findById(targetGoalId).filter { it.userId == userId }
                    .orElseThrow { GoalNotFoundException("Goal not found") }
            } else if (targetStockId != null) {
                StockCatalog.find(targetStockId) ?: throw StockNotFoundException("Stock not found: $targetStockId")
            } else {
                throw RoundUpTargetRequiredException("Choose a real savings goal or a real stock to round up into first")
            }
        }
        val existing = roundUpSettingsRepository.findByUserId(userId)
        val settings = existing ?: RoundUpSettings(id = "round_up_${UUID.randomUUID()}", userId = userId, roundToNearest = roundToNearest)
        settings.enabled = enabled
        settings.roundToNearest = roundToNearest
        settings.targetGoalId = targetGoalId
        settings.targetStockId = targetStockId
        settings.updatedAt = Instant.now()
        return roundUpSettingsRepository.save(settings)
    }

    fun getSettings(userId: String): RoundUpSettings? = roundUpSettingsRepository.findByUserId(userId)

    // Real three-attempt bug fix (2026-07-27) -- see P2pService.sendDirect's own
    // call-site doc comment for attempts 1 and 2 (UnexpectedRollbackException, then a
    // self-deadlock). This is attempt 3's own real failure and the actual fix: calling
    // this method with plain REQUIRED from inside the caller's afterCommit() callback
    // real-threw "Query requires transaction be in progress, but no transaction is known
    // to be in progress" -- Spring's transaction-synchronization ThreadLocal state is
    // genuinely ambiguous during the post-commit callback window (isSynchronizationActive
    // can still read true, a leftover from the just-committed transaction's own
    // synchronization list still being drained, while isActualTransactionActive is
    // already false), and REQUIRED's "join if present" logic doesn't reliably resolve
    // that ambiguity into starting a real new physical transaction. REQUIRES_NEW always
    // unconditionally suspends whatever's on the ThreadLocal and opens a genuinely new
    // one -- safe here specifically because it now only ever runs from the post-commit
    // callback, after the caller's own row locks have already been released, unlike
    // attempt 2 where REQUIRES_NEW was invoked synchronously while those locks were
    // still held.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processRoundUp(userId: String, transferAmount: BigDecimal) {
        try {
            val settings = roundUpSettingsRepository.findByUserId(userId) ?: return
            if (!settings.enabled) return
            val remainder = transferAmount.remainder(settings.roundToNearest)
            if (remainder.compareTo(BigDecimal.ZERO) == 0) return
            val roundUpAmount = settings.roundToNearest.subtract(remainder)

            val goalId = settings.targetGoalId
            val stockId = settings.targetStockId
            if (goalId != null) {
                savingsService.depositToGoal(userId, goalId, roundUpAmount, null)
            } else if (stockId != null) {
                val stock = StockCatalog.find(stockId) ?: return
                stocksService.fundInvestmentWallet(userId, roundUpAmount)
                val shares = roundUpAmount.divide(stock.price, 6, RoundingMode.DOWN)
                if (shares.compareTo(BigDecimal.ZERO) > 0) {
                    stocksService.buyStock(userId, stockId, shares)
                }
            }
        } catch (e: Exception) {
            log.warn("Round-up skipped for user {}: {}", userId, e.message)
        }
    }
}
