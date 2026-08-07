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
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class InvalidStepCountException(message: String) : RuntimeException(message)

// Real lottery-style bonus (item 248) -- see StepRewardService's own doc comment for the
// full sourced account and why lotteryOdds is a real, public, stated constant, not a
// hidden mechanic. Odds are flat across tiers (5%) for simplicity; the bonus amount
// scales with the tier's own guaranteed reward (2x), same relative-value shape the
// guaranteed tiers themselves already use.
enum class StepRewardTier(val stepsRequired: Int, val rewardAmount: BigDecimal, val lotteryOdds: Double, val lotteryBonusAmount: BigDecimal) {
    TIER_1000(1000, BigDecimal("50"), 0.05, BigDecimal("100")),
    TIER_5000(5000, BigDecimal("150"), 0.05, BigDecimal("300")),
    TIER_10000(10000, BigDecimal("300"), 0.05, BigDecimal("600")),
}

data class StepReportResult(
    val reward: DailyStepReward,
    val newlyEarned: List<StepRewardTier>,
    val totalEarnedToday: BigDecimal,
    // Real lottery-style bonus (item 248) -- always returned, whether or not anything
    // was won this call, so a client can show the real, stated odds even on a report
    // that doesn't cross a new tier (never a surprise-only mechanic).
    val lotteryBonusWon: List<StepRewardTier> = emptyList(),
    val lotteryBonusTotal: BigDecimal = BigDecimal.ZERO,
)

/**
 * Real Toss 만보기 (pedometer) walking-rewards feature -- see DailyStepReward.kt's own
 * doc comment for the full sourced account, including the honest boundary on
 * client-reported step data. Reward amounts are itunda's own reasonable choice (Toss's
 * own real published KRW point values weren't found during research) applied to
 * Toss's own real, sourced tier structure (1,000 / 5,000 / 10,000 steps/day) -- the
 * structure is sourced, the exact RWF amounts are honestly itunda's own, not presented
 * as if copied from a real Toss number that was never found.
 *
 * **Real lottery-style bonus (item 248, added 2026-08-08, explicit user decision).**
 * Toss Makers Conference 25 (toss.tech/article/42221) published a real A/B result: a
 * lottery-style unpredictable payout sustained user engagement better than a fixed
 * reward, with no measured churn, for a real Toss step-count feature. Checked against
 * itunda's own dark-pattern-prevention discipline (docs/DESIGN_REFERENCES.md Section
 * 11) before building anything -- a variable-ratio payout is the same reinforcement
 * mechanism a slot machine uses, and blindly copying it would be a real regression, not
 * a feature. The user was asked directly and explicitly chose to build it "toss style":
 * additive-only (every guaranteed StepRewardTier.rewardAmount payout is completely
 * unaffected -- the lottery can only ever add money on top, never replace or reduce
 * what a user was already going to earn) and with the odds (`StepRewardTier.lotteryOdds`)
 * a real, stated, public constant a client can display up front -- never a hidden
 * mechanic the user only discovers by winning. That combination -- guaranteed floor
 * intact, real odds disclosed -- is what keeps this a bonus rather than the dark
 * pattern this project has otherwise deliberately avoided building.
 */
@Service
class StepRewardService(
    private val dailyStepRewardRepository: DailyStepRewardRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    // Real randomness in production (SecureRandom, the default) -- injectable so a test
    // can substitute a deterministic java.util.Random and assert both the win and the
    // lose path of the lottery draw, rather than being at the mercy of real randomness.
    private val random: java.util.Random = SecureRandom(),
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
        val lotteryBonusWon = mutableListOf<StepRewardTier>()
        var totalEarnedToday = BigDecimal.ZERO
        var lotteryBonusTotal = BigDecimal.ZERO
        for (tier in StepRewardTier.entries) {
            val alreadyClaimed = when (tier) {
                StepRewardTier.TIER_1000 -> reward.claimedTier1000
                StepRewardTier.TIER_5000 -> reward.claimedTier5000
                StepRewardTier.TIER_10000 -> reward.claimedTier10000
            }
            val alreadyWonLottery = when (tier) {
                StepRewardTier.TIER_1000 -> reward.lotteryWonTier1000
                StepRewardTier.TIER_5000 -> reward.lotteryWonTier5000
                StepRewardTier.TIER_10000 -> reward.lotteryWonTier10000
            }
            if (alreadyClaimed) {
                totalEarnedToday = totalEarnedToday.add(tier.rewardAmount)
                if (alreadyWonLottery) lotteryBonusTotal = lotteryBonusTotal.add(tier.lotteryBonusAmount)
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

                // Real lottery-style bonus draw (item 248) -- runs exactly once, at the
                // same moment this tier is first claimed, using the tier's own real,
                // stated odds. Additive only: the guaranteed creditTierReward call above
                // already ran unconditionally, so this can only ever add money on top.
                if (random.nextDouble() < tier.lotteryOdds) {
                    creditLotteryBonus(userId, tier)
                    when (tier) {
                        StepRewardTier.TIER_1000 -> reward.lotteryWonTier1000 = true
                        StepRewardTier.TIER_5000 -> reward.lotteryWonTier5000 = true
                        StepRewardTier.TIER_10000 -> reward.lotteryWonTier10000 = true
                    }
                    lotteryBonusWon.add(tier)
                    lotteryBonusTotal = lotteryBonusTotal.add(tier.lotteryBonusAmount)
                }
            }
        }

        val saved = dailyStepRewardRepository.save(reward)
        return StepReportResult(saved, newlyEarned, totalEarnedToday, lotteryBonusWon, lotteryBonusTotal)
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

    // Real lottery-style bonus payout (item 248) -- a separate, clearly-labeled ledger
    // transaction from the guaranteed reward above, so it's distinguishable in a real
    // transaction history rather than silently folded into the same line item.
    private fun creditLotteryBonus(userId: String, tier: StepRewardTier) {
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw RewardsNoWalletException("No wallet found for this account")
        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, tier.lotteryBonusAmount, "Step lottery bonus - ${tier.stepsRequired} steps"),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, tier.lotteryBonusAmount, "Step lottery bonus - ${tier.stepsRequired} steps"),
            ),
        )
    }

    fun getToday(userId: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): DailyStepReward? =
        dailyStepRewardRepository.findByUserIdAndRewardDate(userId, today.toString())
}
