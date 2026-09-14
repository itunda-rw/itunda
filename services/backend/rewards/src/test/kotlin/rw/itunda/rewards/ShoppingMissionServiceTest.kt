package rw.itunda.rewards

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.plusOrMinus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.ShoppingMissionReward
import rw.itunda.core.domain.ShoppingWelcomeBonusClaim
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.ShoppingMissionRewardRepository
import rw.itunda.core.repository.ShoppingWelcomeBonusClaimRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.LocalDate

/** First test coverage for the real Toss Shopping "get points and coupons" mission row
 * -- see ShoppingMissionService's own doc comment for the full sourced account. */
class ShoppingMissionServiceTest : BehaviorSpec({

    fun account(userId: String) = Account(
        id = "account_$userId", userId = userId, accountNumber = "ACC-$userId", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
    )

    Given("a real user with no mission row yet today") {
        val missionRepository = mockk<ShoppingMissionRewardRepository>()
        val welcomeBonusRepository = mockk<ShoppingWelcomeBonusClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ShoppingMissionService(missionRepository, welcomeBonusRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService)
        val today = LocalDate.of(2026, 8, 12)

        every { missionRepository.findByUserIdAndMissionDate("user_1", "2026-08-12") } returns null
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        val savedSlot = mutableListOf<ShoppingMissionReward>()
        every { missionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("completing the real Check-in mission") {
            val result = service.completeDailyMission("user_1", ShoppingMissionType.CHECK_IN, today)

            Then("it real-credits the flat CHECK_IN reward and marks the flag") {
                result.amountEarned shouldBe ShoppingMissionType.CHECK_IN.rewardAmount
                savedSlot.first().checkedIn shouldBe true
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        "RWF",
                        listOf(
                            LedgerLeg("rewards_expense", LedgerAccountType.REWARDS_EXPENSE, LedgerDirection.DEBIT, ShoppingMissionType.CHECK_IN.rewardAmount, "Shopping mission - CHECK_IN"),
                            LedgerLeg("account_user_1", LedgerAccountType.WALLET, LedgerDirection.CREDIT, ShoppingMissionType.CHECK_IN.rewardAmount, "Shopping mission - CHECK_IN"),
                        ),
                    )
                }
            }
            Then("the user is notified their mission reward was earned") {
                verify(exactly = 1) { notificationRepository.save(any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Reward earned", any()) }
            }
        }
    }

    Given("a real user who already checked in today") {
        val missionRepository = mockk<ShoppingMissionRewardRepository>()
        val welcomeBonusRepository = mockk<ShoppingWelcomeBonusClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ShoppingMissionService(missionRepository, welcomeBonusRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService)
        val today = LocalDate.of(2026, 8, 12)

        val existing = ShoppingMissionReward(id = "shopmission_1", userId = "user_1", missionDate = "2026-08-12", checkedIn = true)
        every { missionRepository.findByUserIdAndMissionDate("user_1", "2026-08-12") } returns existing

        When("completing Check-in again the same real day") {
            Then("it throws MissionAlreadyCompletedException, never double-crediting") {
                try {
                    service.completeDailyMission("user_1", ShoppingMissionType.CHECK_IN, today)
                    error("expected MissionAlreadyCompletedException")
                } catch (e: MissionAlreadyCompletedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("completing the real Scroll mission the same day (a different, still-open mission)") {
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { missionRepository.save(any()) } answers { firstArg() }
            val result = service.completeDailyMission("user_1", ShoppingMissionType.SCROLL, today)

            Then("it real-credits Scroll independently of the already-completed Check-in") {
                result.amountEarned shouldBe ShoppingMissionType.SCROLL.rewardAmount
                existing.scrolled shouldBe true
            }
        }
    }

    Given("a real user drawing the Spin mission, real weighted-random payout") {
        val missionRepository = mockk<ShoppingMissionRewardRepository>()
        val welcomeBonusRepository = mockk<ShoppingWelcomeBonusClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val random = mockk<java.util.Random>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ShoppingMissionService(missionRepository, welcomeBonusRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService, random)
        val today = LocalDate.of(2026, 8, 12)

        every { missionRepository.findByUserIdAndMissionDate("user_5", "2026-08-12") } returns null
        every { accountRepository.findByUserIdAndType("user_5", AccountType.MAIN) } returns account("user_5")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_5", emptyList())
        every { missionRepository.save(any()) } answers { firstArg() }

        When("the real draw lands in the top outcome bucket (roll = 0.99, above every cumulative threshold but the last)") {
            every { random.nextDouble() } returns 0.99
            val result = service.completeDailyMission("user_5", ShoppingMissionType.SPIN, today)

            Then("it real-credits the real top-tier SPIN_OUTCOMES amount, not the flat nominal rewardAmount") {
                result.amountEarned shouldBe SPIN_OUTCOMES.last().amount
            }
        }

        When("the real draw lands in the first bucket (roll = 0.0)") {
            every { random.nextDouble() } returns 0.0
            val result = service.completeDailyMission("user_5", ShoppingMissionType.SPIN, today)

            Then("it real-credits the first SPIN_OUTCOMES amount") {
                result.amountEarned shouldBe SPIN_OUTCOMES.first().amount
            }
        }

        Then("the real, stated odds across every outcome sum to 1.0 -- no hidden extra chance") {
            SPIN_OUTCOMES.sumOf { it.odds } shouldBe (1.0 plusOrMinus 0.0001)
        }
    }

    Given("a real user claiming the one-time welcome bonus") {
        val missionRepository = mockk<ShoppingMissionRewardRepository>()
        val welcomeBonusRepository = mockk<ShoppingWelcomeBonusClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ShoppingMissionService(missionRepository, welcomeBonusRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService)

        every { welcomeBonusRepository.existsById("user_6") } returns false
        every { accountRepository.findByUserIdAndType("user_6", AccountType.MAIN) } returns account("user_6")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_6", emptyList())
        val savedSlot = mutableListOf<ShoppingWelcomeBonusClaim>()
        every { welcomeBonusRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("claiming for the real first time") {
            val result = service.claimWelcomeBonus("user_6")

            Then("it real-credits the WELCOME_BONUS amount and records the real once-ever claim") {
                result.amountEarned shouldBe ShoppingMissionType.WELCOME_BONUS.rewardAmount
                savedSlot.first().userId shouldBe "user_6"
            }
            Then("the user is notified their welcome bonus was earned") {
                verify(exactly = 1) { notificationRepository.save(any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_6", "Reward earned", any()) }
            }
        }
    }

    Given("a real user who already claimed the welcome bonus") {
        val missionRepository = mockk<ShoppingMissionRewardRepository>()
        val welcomeBonusRepository = mockk<ShoppingWelcomeBonusClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ShoppingMissionService(missionRepository, welcomeBonusRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService)

        every { welcomeBonusRepository.existsById("user_7") } returns true

        When("claiming again") {
            Then("it throws MissionAlreadyCompletedException, never double-crediting the real once-ever bonus") {
                try {
                    service.claimWelcomeBonus("user_7")
                    error("expected MissionAlreadyCompletedException")
                } catch (e: MissionAlreadyCompletedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real user with no real MAIN account, somehow completing a mission") {
        val missionRepository = mockk<ShoppingMissionRewardRepository>()
        val welcomeBonusRepository = mockk<ShoppingWelcomeBonusClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = ShoppingMissionService(missionRepository, welcomeBonusRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService)
        val today = LocalDate.of(2026, 8, 12)

        every { missionRepository.findByUserIdAndMissionDate("user_8", "2026-08-12") } returns null
        every { accountRepository.findByUserIdAndType("user_8", AccountType.MAIN) } returns null

        When("completing Check-in") {
            Then("it throws RewardsNoAccountException rather than silently marking the mission claimed unpaid") {
                try {
                    service.completeDailyMission("user_8", ShoppingMissionType.CHECK_IN, today)
                    error("expected RewardsNoAccountException")
                } catch (e: RewardsNoAccountException) {
                    verify(exactly = 0) { missionRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
