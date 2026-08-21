package rw.itunda.rewards

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.DailyStepReward
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.DailyStepRewardRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDate

/** First test coverage for Toss 만보기-style walking rewards -- see StepRewardService's
 * own doc comment for the full sourced account. */
class StepRewardServiceTest : BehaviorSpec({

    fun wallet(userId: String) = Wallet(
        id = "wallet_$userId", userId = userId, accountNumber = "ACC-$userId", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
    )

    Given("a real user with no real step report yet today") {
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        // Real flaky test found live (2026-08-10) -- this Given block previously left
        // `random` at its default SecureRandom(), so "verify(exactly = 3)" below would
        // spuriously fail with 4 calls whenever the real lottery draw actually won.
        // Stubbed to guarantee a loss, same "above every tier's real odds" value the
        // dedicated loss-path Given block below already established.
        val random = mockk<java.util.Random>()
        every { random.nextDouble() } returns 0.99
        val service = StepRewardService(dailyStepRewardRepository, walletRepository, ledgerService, random)
        val today = LocalDate.of(2026, 7, 27)

        every { dailyStepRewardRepository.findByUserIdAndRewardDateForUpdate("user_1", "2026-07-27") } returns null
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val savedSlot = mutableListOf<DailyStepReward>()
        every { dailyStepRewardRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("reporting a real 1200 steps -- crosses only the real 1,000-step tier") {
            val result = service.reportSteps("user_1", 1200, today)

            Then("it real-credits exactly the 1,000-step tier reward, not the others") {
                result.newlyEarned shouldBe listOf(StepRewardTier.TIER_1000)
                result.totalEarnedToday shouldBe StepRewardTier.TIER_1000.rewardAmount
                savedSlot.first().claimedTier1000 shouldBe true
                savedSlot.first().claimedTier5000 shouldBe false
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        "RWF",
                        listOf(
                            LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, StepRewardTier.TIER_1000.rewardAmount, "Walking reward - 1000 steps"),
                            LedgerLeg("wallet_user_1", LedgerAccountType.WALLET, LedgerDirection.CREDIT, StepRewardTier.TIER_1000.rewardAmount, "Walking reward - 1000 steps"),
                        ),
                    )
                }
            }
        }

        When("reporting a real 12000 steps in one call -- crosses all three real tiers at once") {
            val result = service.reportSteps("user_1", 12000, today)

            Then("it real-credits all three tiers, matching a real user who walked a lot before ever opening the app") {
                result.newlyEarned shouldBe listOf(StepRewardTier.TIER_1000, StepRewardTier.TIER_5000, StepRewardTier.TIER_10000)
                result.totalEarnedToday shouldBe StepRewardTier.TIER_1000.rewardAmount.add(StepRewardTier.TIER_5000.rewardAmount).add(StepRewardTier.TIER_10000.rewardAmount)
                verify(exactly = 3) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("reporting a real negative step count") {
            Then("it throws InvalidStepCountException before touching any repository") {
                try {
                    service.reportSteps("user_1", -5, today)
                    error("expected InvalidStepCountException")
                } catch (e: InvalidStepCountException) {
                    verify(exactly = 0) { dailyStepRewardRepository.save(any()) }
                }
            }
        }

        When("reporting an implausibly large real step count") {
            Then("it throws InvalidStepCountException") {
                try {
                    service.reportSteps("user_1", 500_000, today)
                    error("expected InvalidStepCountException")
                } catch (e: InvalidStepCountException) {
                    // expected
                }
            }
        }
    }

    Given("a real user who already crossed the 1,000-step tier earlier today") {
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        // Same real flaky-test fix as the first Given block above -- the third When
        // below crosses TIER_5000 and asserts an exact call count, which the real
        // unseeded SecureRandom default could spuriously break on a lottery win.
        val random = mockk<java.util.Random>()
        every { random.nextDouble() } returns 0.99
        val service = StepRewardService(dailyStepRewardRepository, walletRepository, ledgerService, random)
        val today = LocalDate.of(2026, 7, 27)

        val existing = DailyStepReward(id = "stepreward_1", userId = "user_1", rewardDate = "2026-07-27", steps = 1200, claimedTier1000 = true)
        every { dailyStepRewardRepository.findByUserIdAndRewardDateForUpdate("user_1", "2026-07-27") } returns existing
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        every { dailyStepRewardRepository.save(any()) } answers { firstArg() }

        When("reporting more steps that don't cross a new real tier") {
            val result = service.reportSteps("user_1", 2000, today)

            Then("it never double-credits the already-claimed 1,000-step tier") {
                result.newlyEarned shouldBe emptyList()
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("reporting a real LOWER step count than already on file (e.g. an out-of-order report)") {
            service.reportSteps("user_1", 500, today)

            Then("it honestly ignores the lower count -- a real pedometer's own count never decreases within a day") {
                existing.steps shouldBe 1200
            }
        }

        When("reporting enough new steps to cross the real 5,000-step tier too") {
            val result = service.reportSteps("user_1", 6000, today)

            Then("it real-credits only the newly-crossed 5,000-step tier, not re-crediting the 1,000-step one") {
                result.newlyEarned shouldBe listOf(StepRewardTier.TIER_5000)
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    // Real optimistic-lock regression test (found live in a 2026-08-02 audit pass):
    // reportSteps is a real check-then-act shape (read the day's row, credit any
    // newly-crossed tier, save) with no DB-level guard beyond the unique constraint on
    // (user_id, reward_date) -- which only protects the very first report of the day's
    // INSERT, not two concurrent reports racing to credit the SAME already-existing
    // row's tier. This proves the fix's real mechanism: crediting a tier saves the
    // SAME pre-existing `DailyStepReward` row (not a detached copy), which is what
    // makes DailyStepReward's own @Version field actually guard a concurrent second
    // credit attempt on that exact row (the loser's save would real-409 via the
    // existing global ObjectOptimisticLockingFailureException handler, same proven-safe
    // shape BikeRentalServiceTest's own doc comment already establishes for a different
    // check-then-act race).
    Given("a real user's existing today row, about to newly cross a tier") {
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = StepRewardService(dailyStepRewardRepository, walletRepository, ledgerService)
        val today = LocalDate.of(2026, 7, 27)

        val existing = DailyStepReward(id = "stepreward_9", userId = "user_9", rewardDate = "2026-07-27", steps = 500)
        every { dailyStepRewardRepository.findByUserIdAndRewardDateForUpdate("user_9", "2026-07-27") } returns existing
        every { walletRepository.findByUserIdAndType("user_9", WalletType.MAIN) } returns wallet("user_9")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_9", emptyList())
        val savedSlot = mutableListOf<DailyStepReward>()
        every { dailyStepRewardRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("reporting enough steps to cross the 1,000-step tier") {
            service.reportSteps("user_9", 1000, today)

            Then("the exact same row object -- the one carrying the real @Version -- is what gets saved") {
                (savedSlot.first() === existing) shouldBe true
                savedSlot.first().claimedTier1000 shouldBe true
            }
        }
    }

    // Real lottery-style bonus (item 248) -- see StepRewardService's own doc comment for
    // the full sourced account (Toss Makers Conference 25) and why this is
    // additive-only, real-odds-disclosed. `random` is injected as a mock so both the
    // win and lose path can be asserted deterministically, rather than at the mercy of
    // real SecureRandom output.
    Given("a real user newly crossing a tier, and the real lottery draw wins") {
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val random = mockk<java.util.Random>()
        val service = StepRewardService(dailyStepRewardRepository, walletRepository, ledgerService, random)
        val today = LocalDate.of(2026, 7, 27)

        every { dailyStepRewardRepository.findByUserIdAndRewardDateForUpdate("user_3", "2026-07-27") } returns null
        every { walletRepository.findByUserIdAndType("user_3", WalletType.MAIN) } returns wallet("user_3")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_3", emptyList())
        every { dailyStepRewardRepository.save(any()) } answers { firstArg() }
        // Below TIER_1000's real 5% odds -- a real win.
        every { random.nextDouble() } returns 0.01

        When("reporting 1200 steps") {
            val result = service.reportSteps("user_3", 1200, today)

            Then("the guaranteed reward AND the real lottery bonus both credit, as two separate ledger legs") {
                result.newlyEarned shouldBe listOf(StepRewardTier.TIER_1000)
                result.lotteryBonusWon shouldBe listOf(StepRewardTier.TIER_1000)
                result.lotteryBonusTotal shouldBe StepRewardTier.TIER_1000.lotteryBonusAmount
                result.reward.lotteryWonTier1000 shouldBe true
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        "RWF",
                        listOf(
                            LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, StepRewardTier.TIER_1000.lotteryBonusAmount, "Step lottery bonus - 1000 steps"),
                            LedgerLeg("wallet_user_3", LedgerAccountType.WALLET, LedgerDirection.CREDIT, StepRewardTier.TIER_1000.lotteryBonusAmount, "Step lottery bonus - 1000 steps"),
                        ),
                    )
                }
                // Two separate real ledger postings: the guaranteed reward and the bonus.
                verify(exactly = 2) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real user newly crossing a tier, and the real lottery draw doesn't win") {
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val random = mockk<java.util.Random>()
        val service = StepRewardService(dailyStepRewardRepository, walletRepository, ledgerService, random)
        val today = LocalDate.of(2026, 7, 27)

        every { dailyStepRewardRepository.findByUserIdAndRewardDateForUpdate("user_4", "2026-07-27") } returns null
        every { walletRepository.findByUserIdAndType("user_4", WalletType.MAIN) } returns wallet("user_4")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_4", emptyList())
        every { dailyStepRewardRepository.save(any()) } answers { firstArg() }
        // Above TIER_1000's real 5% odds -- a real loss.
        every { random.nextDouble() } returns 0.99

        When("reporting 1200 steps") {
            val result = service.reportSteps("user_4", 1200, today)

            Then("the guaranteed reward still credits in full -- the lottery losing never reduces it") {
                result.newlyEarned shouldBe listOf(StepRewardTier.TIER_1000)
                result.totalEarnedToday shouldBe StepRewardTier.TIER_1000.rewardAmount
                result.lotteryBonusWon shouldBe emptyList()
                result.lotteryBonusTotal shouldBe BigDecimal.ZERO
                result.reward.lotteryWonTier1000 shouldBe false
                // Only the one guaranteed-reward ledger posting -- no bonus leg at all.
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real user with no real MAIN wallet, somehow crossing a tier") {
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = StepRewardService(dailyStepRewardRepository, walletRepository, ledgerService)
        val today = LocalDate.of(2026, 7, 27)

        every { dailyStepRewardRepository.findByUserIdAndRewardDateForUpdate("user_2", "2026-07-27") } returns null
        every { walletRepository.findByUserIdAndType("user_2", WalletType.MAIN) } returns null

        When("reporting 1000 steps") {
            Then("it throws RewardsNoWalletException rather than silently marking the tier claimed unpaid") {
                try {
                    service.reportSteps("user_2", 1000, today)
                    error("expected RewardsNoWalletException")
                } catch (e: RewardsNoWalletException) {
                    verify(exactly = 0) { dailyStepRewardRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
