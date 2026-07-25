package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.RoundUpSettings
import rw.itunda.core.repository.RoundUpSettingsRepository
import rw.itunda.core.repository.SavingsGoalRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class InvalidRoundUpIncrementException(message: String) : RuntimeException(message)
class RoundUpTargetRequiredException(message: String) : RuntimeException(message)

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
) {
    private val log = LoggerFactory.getLogger(RoundUpService::class.java)

    companion object {
        // Real, honest, user-chosen increments -- not an arbitrary fabricated default.
        val SUPPORTED_INCREMENTS: Set<BigDecimal> = setOf(BigDecimal("100"), BigDecimal("500"), BigDecimal("1000"))
    }

    @Transactional
    fun setSettings(userId: String, enabled: Boolean, roundToNearest: BigDecimal, targetGoalId: String?): RoundUpSettings {
        if (SUPPORTED_INCREMENTS.none { it.compareTo(roundToNearest) == 0 }) {
            throw InvalidRoundUpIncrementException("Choose a real increment -- ${SUPPORTED_INCREMENTS.sorted().joinToString()} RWF")
        }
        if (enabled) {
            val goalId = targetGoalId ?: throw RoundUpTargetRequiredException("Choose a real savings goal to round up into first")
            savingsGoalRepository.findById(goalId).filter { it.userId == userId }
                .orElseThrow { GoalNotFoundException("Goal not found") }
        }
        val existing = roundUpSettingsRepository.findByUserId(userId)
        val settings = existing ?: RoundUpSettings(id = "round_up_${UUID.randomUUID()}", userId = userId, roundToNearest = roundToNearest)
        settings.enabled = enabled
        settings.roundToNearest = roundToNearest
        settings.targetGoalId = targetGoalId
        settings.updatedAt = Instant.now()
        return roundUpSettingsRepository.save(settings)
    }

    fun getSettings(userId: String): RoundUpSettings? = roundUpSettingsRepository.findByUserId(userId)

    @Transactional
    fun processRoundUp(userId: String, transferAmount: BigDecimal) {
        try {
            val settings = roundUpSettingsRepository.findByUserId(userId) ?: return
            if (!settings.enabled) return
            val goalId = settings.targetGoalId ?: return
            val remainder = transferAmount.remainder(settings.roundToNearest)
            if (remainder.compareTo(BigDecimal.ZERO) == 0) return
            val roundUpAmount = settings.roundToNearest.subtract(remainder)
            savingsService.depositToGoal(userId, goalId, roundUpAmount, null)
        } catch (e: Exception) {
            log.warn("Round-up skipped for user {}: {}", userId, e.message)
        }
    }
}
