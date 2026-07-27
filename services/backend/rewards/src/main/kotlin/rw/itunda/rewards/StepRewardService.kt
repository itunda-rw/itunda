package rw.itunda.rewards

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.DailyStepReward
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.DailyStepRewardRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class InvalidStepCountException(message: String) : RuntimeException(message)

enum class StepRewardTier(val stepsRequired: Int, val rewardAmount: BigDecimal) {
    TIER_1000(1000, BigDecimal("50")),
    TIER_5000(5000, BigDecimal("150")),
    TIER_10000(10000, BigDecimal("300")),
}

data class StepReportResult(val reward: DailyStepReward, val newlyEarned: List<StepRewardTier>, val totalEarnedToday: BigDecimal)

/**
 * Real Toss 만보기 (pedometer) walking-rewards feature -- see DailyStepReward.kt's own
 * doc comment for the full sourced account, including the honest boundary on
 * client-reported step data. Reward amounts are itunda's own reasonable choice (Toss's
 * own real published KRW point values weren't found during research) applied to
 * Toss's own real, sourced tier structure (1,000 / 5,000 / 10,000 steps/day) -- the
 * structure is sourced, the exact RWF amounts are honestly itunda's own, not presented
 * as if copied from a real Toss number that was never found.
 */
@Service
class StepRewardService(
    private val dailyStepRewardRepository: DailyStepRewardRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
) {
    companion object {
        // Real sanity ceiling against obviously-garbage step reports -- not a real
        // anti-spoofing measure (see DailyStepReward.kt's own doc comment for why that's
        // a genuinely separate, out-of-scope follow-up), just an honest guard: even an
        // Olympic race walker covers well under this in a real 24-hour period.
        const val MAX_PLAUSIBLE_DAILY_STEPS = 100_000
    }

    @Transactional
    fun reportSteps(userId: String, steps: Int, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): StepReportResult {
        if (steps < 0) throw InvalidStepCountException("Step count cannot be negative")
        if (steps > MAX_PLAUSIBLE_DAILY_STEPS) throw InvalidStepCountException("Step count exceeds a real plausible daily maximum")

        val dateKey = today.toString()
        val existing = dailyStepRewardRepository.findByUserIdAndRewardDate(userId, dateKey)
        val reward = existing ?: DailyStepReward(id = "stepreward_${UUID.randomUUID()}", userId = userId, rewardDate = dateKey)

        // Real pedometer semantics: a real device's cumulative daily count never
        // decreases within the same day -- a lower report is honestly ignored (not an
        // error, since a real client might report out of order), not silently
        // overwriting a real higher count already on file.
        if (steps > reward.steps) {
            reward.steps = steps
        }
        reward.updatedAt = Instant.now()

        val newlyEarned = mutableListOf<StepRewardTier>()
        var totalEarnedToday = BigDecimal.ZERO
        for (tier in StepRewardTier.entries) {
            val alreadyClaimed = when (tier) {
                StepRewardTier.TIER_1000 -> reward.claimedTier1000
                StepRewardTier.TIER_5000 -> reward.claimedTier5000
                StepRewardTier.TIER_10000 -> reward.claimedTier10000
            }
            if (alreadyClaimed) {
                totalEarnedToday = totalEarnedToday.add(tier.rewardAmount)
                continue
            }
            if (reward.steps >= tier.stepsRequired) {
                creditTierReward(userId, tier)
                when (tier) {
                    StepRewardTier.TIER_1000 -> reward.claimedTier1000 = true
                    StepRewardTier.TIER_5000 -> reward.claimedTier5000 = true
                    StepRewardTier.TIER_10000 -> reward.claimedTier10000 = true
                }
                newlyEarned.add(tier)
                totalEarnedToday = totalEarnedToday.add(tier.rewardAmount)
            }
        }

        val saved = dailyStepRewardRepository.save(reward)
        return StepReportResult(saved, newlyEarned, totalEarnedToday)
    }

    private fun creditTierReward(userId: String, tier: StepRewardTier) {
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw RewardsNoWalletException("No wallet found for this account")
        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, tier.rewardAmount, "Walking reward - ${tier.stepsRequired} steps"),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, tier.rewardAmount, "Walking reward - ${tier.stepsRequired} steps"),
            ),
        )
    }

    fun getToday(userId: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): DailyStepReward? =
        dailyStepRewardRepository.findByUserIdAndRewardDate(userId, today.toString())
}
