package rw.itunda.rewards

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.RewardClaim
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.domain.User
import rw.itunda.core.repository.DailyStepRewardRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.KnowledgeAnswerRepository
import rw.itunda.core.repository.RewardClaimRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant

class RewardsServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
    )

    Given("a user who hasn't claimed anything yet and hasn't done any of the checkable tasks") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.findByUserId("user_1") } returns emptyList()
        every { transactionRepository.existsBySenderIdAndTypeAndStatus("user_1", any(), TransactionStatus.COMPLETED) } returns false
        every { savingsGoalRepository.existsByUserId("user_1") } returns false
        every { userRepository.findAllByReferredByUserId("user_1") } returns emptyList()
        every { userRepository.findById("user_1") } returns java.util.Optional.of(
            User(id = "user_1", phoneNumber = "+250788000001", firstName = "Jean", lastName = "B", passwordHash = "unused"),
        )
        every { knowledgeAnswerRepository.countByAnswererIdAndIsAdoptedTrue("user_1") } returns 0L
        every { eatsReviewRepository.existsByBuyerIdAndPhotoUrlIsNotNull("user_1") } returns false

        When("listing tasks") {
            val result = service.getTasks("user_1")

            Then("every catalog task shows claimed=false and the total is zero") {
                result.tasks.size shouldBe service.taskCatalog.size
                result.tasks.all { !it.claimed } shouldBe true
                result.rewardsTotal shouldBe BigDecimal.ZERO
            }
            Then("every task shows eligible=false -- all seven are now real-activity-verified, and this user hasn't done any of them") {
                val byId = result.tasks.associateBy { it.id }
                byId.getValue("task_first_transfer").eligible shouldBe false
                byId.getValue("task_first_bill").eligible shouldBe false
                byId.getValue("task_savings_goal").eligible shouldBe false
                byId.getValue("task_referral").eligible shouldBe false
                byId.getValue("task_profile").eligible shouldBe false
                byId.getValue("task_knowledge_answer_adopted").eligible shouldBe false
                byId.getValue("task_first_photo_review").eligible shouldBe false
            }
        }
    }

    Given("a real Coupang/Baemin-style photo-review reward: a user who submitted an Eats review with a real photo") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_12", "task_first_photo_review") } returns false
        every { accountRepository.findByUserIdAndType("user_12", AccountType.MAIN) } returns account("account_main", "user_12")
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_5", emptyList())
        every { rewardClaimRepository.save(any()) } answers { firstArg() }
        every { rewardClaimRepository.findByUserId("user_12") } returns listOf(
            RewardClaim(id = "rwc_5", userId = "user_12", taskId = "task_first_photo_review", amount = BigDecimal("300"), claimedAt = Instant.now()),
        )

        When("the user has a real photo-bearing review and claims the task") {
            every { eatsReviewRepository.existsByBuyerIdAndPhotoUrlIsNotNull("user_12") } returns true

            val result = service.claim("user_12", "task_first_photo_review")

            Then("it succeeds and credits the real 300 RWF reward") {
                result.rewardAmount shouldBe BigDecimal("300")
                verify(exactly = 1) { rewardClaimRepository.save(any()) }
            }
        }

        When("the user has no photo-bearing review yet") {
            every { eatsReviewRepository.existsByBuyerIdAndPhotoUrlIsNotNull("user_12") } returns false

            Then("claiming throws RewardTaskNotEligibleException and never touches the ledger") {
                try {
                    service.claim("user_12", "task_first_photo_review")
                    error("expected RewardTaskNotEligibleException")
                } catch (e: RewardTaskNotEligibleException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a user with a real profile photo and a verified email, claiming task_profile for the first time") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        val completeUser = User(
            id = "user_10", phoneNumber = "+250788000095", firstName = "Eve", lastName = "R",
            passwordHash = "unused", profilePhotoUrl = "https://cdn.itunda.rw/avatars/user_10.jpg", emailVerified = true,
        )
        every { rewardClaimRepository.existsByUserIdAndTaskId("user_10", "task_profile") } returns false
        every { userRepository.findById("user_10") } returns java.util.Optional.of(completeUser)
        every { accountRepository.findByUserIdAndType("user_10", AccountType.MAIN) } returns account("account_main", "user_10")
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_3", emptyList())
        every { rewardClaimRepository.save(any()) } answers { firstArg() }
        every { rewardClaimRepository.findByUserId("user_10") } returns listOf(
            RewardClaim(id = "rwc_3", userId = "user_10", taskId = "task_profile", amount = BigDecimal("500"), claimedAt = Instant.now()),
        )

        When("claiming task_profile (500 RWF)") {
            val result = service.claim("user_10", "task_profile")

            Then("it succeeds -- both a real photo URL and real emailVerified are set") {
                result.rewardAmount shouldBe BigDecimal("500")
                verify(exactly = 1) { rewardClaimRepository.save(any()) }
            }
        }
    }

    Given("a real Naver Pay-style non-transactional engagement reward: a user whose Community Q&A answer was adopted") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_11", "task_knowledge_answer_adopted") } returns false
        every { accountRepository.findByUserIdAndType("user_11", AccountType.MAIN) } returns account("account_main", "user_11")
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_4", emptyList())
        every { rewardClaimRepository.save(any()) } answers { firstArg() }
        every { rewardClaimRepository.findByUserId("user_11") } returns listOf(
            RewardClaim(id = "rwc_4", userId = "user_11", taskId = "task_knowledge_answer_adopted", amount = BigDecimal("500"), claimedAt = Instant.now()),
        )

        When("they have a real adopted answer") {
            every { knowledgeAnswerRepository.countByAnswererIdAndIsAdoptedTrue("user_11") } returns 1L

            Then("claiming task_knowledge_answer_adopted succeeds") {
                val result = service.claim("user_11", "task_knowledge_answer_adopted")
                result.rewardAmount shouldBe BigDecimal("500")
                verify(exactly = 1) { rewardClaimRepository.save(any()) }
            }
        }

        When("they have posted answers, but none real-adopted yet") {
            every { knowledgeAnswerRepository.countByAnswererIdAndIsAdoptedTrue("user_11") } returns 0L

            Then("claiming throws RewardTaskNotEligibleException -- posting alone doesn't earn it, adoption does") {
                try {
                    service.claim("user_11", "task_knowledge_answer_adopted")
                    error("expected RewardTaskNotEligibleException")
                } catch (e: RewardTaskNotEligibleException) {
                    // expected
                }
            }
        }
    }

    Given("a user with a profile photo but an unverified email, trying to claim task_profile") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        val partialUser = User(
            id = "user_11", phoneNumber = "+250788000094", firstName = "Frank", lastName = "R",
            passwordHash = "unused", profilePhotoUrl = "https://cdn.itunda.rw/avatars/user_11.jpg", emailVerified = false,
        )
        every { rewardClaimRepository.existsByUserIdAndTaskId("user_11", "task_profile") } returns false
        every { userRepository.findById("user_11") } returns java.util.Optional.of(partialUser)

        When("claiming it") {
            Then("it throws RewardTaskNotEligibleException -- a photo alone isn't a complete profile") {
                try {
                    service.claim("user_11", "task_profile")
                    error("expected RewardTaskNotEligibleException")
                } catch (e: RewardTaskNotEligibleException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { rewardClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a user who referred a friend, and that friend has completed a real transfer") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        val friend = User(
            id = "user_friend", phoneNumber = "+250788000099", firstName = "Alice", lastName = "R",
            passwordHash = "unused", referredByUserId = "user_7",
        )

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_7", "task_referral") } returns false
        every { userRepository.findAllByReferredByUserId("user_7") } returns listOf(friend)
        every { transactionRepository.findDistinctSenderIdsBySenderIdInAndTypeAndStatus(listOf("user_friend"), TransactionType.TRANSFER, TransactionStatus.COMPLETED) } returns listOf("user_friend")
        every { accountRepository.findByUserIdAndType("user_7", AccountType.MAIN) } returns account("account_main", "user_7")
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
        every { rewardClaimRepository.save(any()) } answers { firstArg() }
        every { rewardClaimRepository.findByUserId("user_7") } returns listOf(
            RewardClaim(id = "rwc_2", userId = "user_7", taskId = "task_referral", amount = BigDecimal("5000"), claimedAt = Instant.now()),
        )

        When("claiming task_referral (5,000 RWF)") {
            val result = service.claim("user_7", "task_referral")

            Then("it succeeds -- real attribution plus the friend's real completed transfer is enough") {
                result.rewardAmount shouldBe BigDecimal("5000")
                verify(exactly = 1) { rewardClaimRepository.save(any()) }
            }
        }
    }

    Given("a user who referred a friend, but that friend hasn't transacted yet") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        val friend = User(
            id = "user_friend_2", phoneNumber = "+250788000098", firstName = "Bob", lastName = "R",
            passwordHash = "unused", referredByUserId = "user_8",
        )

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_8", "task_referral") } returns false
        every { userRepository.findAllByReferredByUserId("user_8") } returns listOf(friend)
        every { transactionRepository.findDistinctSenderIdsBySenderIdInAndTypeAndStatus(listOf("user_friend_2"), TransactionType.TRANSFER, TransactionStatus.COMPLETED) } returns emptyList()

        When("claiming task_referral before the friend has transacted") {
            Then("it throws RewardTaskNotEligibleException -- a referral with no activity yet doesn't pay out") {
                try {
                    service.claim("user_8", "task_referral")
                    error("expected RewardTaskNotEligibleException")
                } catch (e: RewardTaskNotEligibleException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { rewardClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a user with a real referral code and one referred friend who hasn't transacted") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        val self = User(
            id = "user_9", phoneNumber = "+250788000097", firstName = "Carol", lastName = "R",
            passwordHash = "unused", referralCode = "ITDCAROL",
        )
        val friend = User(
            id = "user_friend_3", phoneNumber = "+250788000096", firstName = "Dan", lastName = "R",
            passwordHash = "unused", referredByUserId = "user_9",
        )
        every { userRepository.findById("user_9") } returns java.util.Optional.of(self)
        every { userRepository.findAllByReferredByUserId("user_9") } returns listOf(friend)
        every { transactionRepository.findDistinctSenderIdsBySenderIdInAndTypeAndStatus(listOf("user_friend_3"), TransactionType.TRANSFER, TransactionStatus.COMPLETED) } returns emptyList()

        When("fetching referral info") {
            val info = service.getReferralInfo("user_9")

            Then("it returns the real code, one referred friend, and zero completions") {
                info.referralCode shouldBe "ITDCAROL"
                info.referredCount shouldBe 1
                info.completedReferralCount shouldBe 0
            }
        }
    }

    // Real N+1 fix (2026-09-13): both isEligible("task_referral") and getReferralInfo
    // used to call existsBySenderIdAndTypeAndStatus once per referred friend -- this
    // proves the batched replacement resolves all referred friends in ONE call and
    // still attributes completion to the right friend.
    Given("a user with two referred friends, only one of whom has transacted") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        val self = User(
            id = "user_10", phoneNumber = "+250788000095", firstName = "Eve", lastName = "R",
            passwordHash = "unused", referralCode = "ITDEVE",
        )
        val friendA = User(
            id = "user_friend_a", phoneNumber = "+250788000094", firstName = "Frank", lastName = "R",
            passwordHash = "unused", referredByUserId = "user_10",
        )
        val friendB = User(
            id = "user_friend_b", phoneNumber = "+250788000093", firstName = "Grace", lastName = "R",
            passwordHash = "unused", referredByUserId = "user_10",
        )
        every { userRepository.findById("user_10") } returns java.util.Optional.of(self)
        every { userRepository.findAllByReferredByUserId("user_10") } returns listOf(friendA, friendB)
        every {
            transactionRepository.findDistinctSenderIdsBySenderIdInAndTypeAndStatus(
                match { it.toSet() == setOf("user_friend_a", "user_friend_b") }, TransactionType.TRANSFER, TransactionStatus.COMPLETED,
            )
        } returns listOf("user_friend_a")

        When("fetching referral info") {
            val info = service.getReferralInfo("user_10")

            Then("both friends are resolved in a single batched query, and only the transacted one counts as completed") {
                info.referredCount shouldBe 2
                info.completedReferralCount shouldBe 1
                verify(exactly = 1) { transactionRepository.findDistinctSenderIdsBySenderIdInAndTypeAndStatus(any(), any(), any()) }
            }
        }
    }

    Given("a user who has completed a real transfer, claiming task_first_transfer for the first time") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_2", "task_first_transfer") } returns false
        every { transactionRepository.existsBySenderIdAndTypeAndStatus("user_2", TransactionType.TRANSFER, TransactionStatus.COMPLETED) } returns true
        every { accountRepository.findByUserIdAndType("user_2", AccountType.MAIN) } returns account("account_main", "user_2")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { rewardClaimRepository.save(any()) } answers { firstArg() }
        every { rewardClaimRepository.findByUserId("user_2") } returns listOf(
            RewardClaim(id = "rwc_1", userId = "user_2", taskId = "task_first_transfer", amount = BigDecimal("1000"), claimedAt = Instant.now()),
        )

        When("claiming task_first_transfer (1,000 RWF)") {
            val result = service.claim("user_2", "task_first_transfer")

            Then("it debits rewards_expense and credits the MAIN account for the exact same amount") {
                val expenseLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.REWARDS_EXPENSE }
                expenseLeg.accountId shouldBe "rewards_expense"
                expenseLeg.direction shouldBe LedgerDirection.DEBIT
                expenseLeg.amount shouldBe BigDecimal("1000")

                val accountLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                accountLeg.accountId shouldBe "account_main"
                accountLeg.direction shouldBe LedgerDirection.CREDIT
                accountLeg.amount shouldBe BigDecimal("1000")
            }
            Then("it saves a real claim record and returns the new total") {
                result.rewardAmount shouldBe BigDecimal("1000")
                result.newBalance shouldBe BigDecimal("1000")
                verify(exactly = 1) { rewardClaimRepository.save(any()) }
            }
        }
    }

    Given("a user who has NOT completed a real transfer, trying to claim task_first_transfer") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_6", "task_first_transfer") } returns false
        every { transactionRepository.existsBySenderIdAndTypeAndStatus("user_6", TransactionType.TRANSFER, TransactionStatus.COMPLETED) } returns false

        When("claiming it") {
            Then("it throws RewardTaskNotEligibleException before ever touching the account or ledger -- this is the real fix for the honor-system gap") {
                try {
                    service.claim("user_6", "task_first_transfer")
                    error("expected RewardTaskNotEligibleException")
                } catch (e: RewardTaskNotEligibleException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { rewardClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a task that's already been claimed") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_3", "task_profile") } returns true

        When("claiming it again") {
            Then("it throws RewardTaskAlreadyClaimedException before touching the ledger -- the whole point of the claim-once guard") {
                try {
                    service.claim("user_3", "task_profile")
                    error("expected RewardTaskAlreadyClaimedException")
                } catch (e: RewardTaskAlreadyClaimedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { rewardClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a task ID that isn't in the catalog") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        When("claiming it") {
            Then("it throws RewardTaskNotFoundException before checking claim status at all") {
                try {
                    service.claim("user_4", "task_does_not_exist")
                    error("expected RewardTaskNotFoundException")
                } catch (e: RewardTaskNotFoundException) {
                    verify(exactly = 0) { rewardClaimRepository.existsByUserIdAndTaskId(any(), any()) }
                }
            }
        }
    }

    Given("a user with no account at all") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_5", "task_profile") } returns false
        every { userRepository.findById("user_5") } returns java.util.Optional.of(
            User(
                id = "user_5", phoneNumber = "+250788000093", firstName = "Grace", lastName = "R",
                passwordHash = "unused", profilePhotoUrl = "https://cdn.itunda.rw/avatars/user_5.jpg", emailVerified = true,
            ),
        )
        every { accountRepository.findByUserIdAndType("user_5", AccountType.MAIN) } returns null

        When("claiming a valid, unclaimed task") {
            Then("it throws RewardsNoAccountException before touching the ledger") {
                try {
                    service.claim("user_5", "task_profile")
                    error("expected RewardsNoAccountException")
                } catch (e: RewardsNoAccountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a brand new user with zero real reward activity") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.findByUserId("user_6") } returns emptyList()
        every { dailyStepRewardRepository.countByUserId("user_6") } returns 0L

        When("getting the pet") {
            val pet = service.getPet("user_6")

            Then("it's exactly level 1, the base Egg stage -- no unearned progress") {
                pet.level shouldBe 1
                pet.stageName shouldBe "Egg"
                pet.claimedTaskCount shouldBe 0
                pet.activeRewardDays shouldBe 0L
            }
        }
    }

    Given("a user with 2 real claimed tasks and 5 real active reward days") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val userRepository = mockk<UserRepository>()
        val dailyStepRewardRepository = mockk<DailyStepRewardRepository>()
        val knowledgeAnswerRepository = mockk<KnowledgeAnswerRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = RewardsService(rewardClaimRepository, accountRepository, ledgerService, transactionRepository, savingsGoalRepository, userRepository, dailyStepRewardRepository, knowledgeAnswerRepository, eatsReviewRepository)

        every { rewardClaimRepository.findByUserId("user_7") } returns listOf(
            RewardClaim(id = "claim_1", userId = "user_7", taskId = "task_profile", amount = BigDecimal("500"), claimedAt = Instant.now()),
            RewardClaim(id = "claim_2", userId = "user_7", taskId = "task_first_transfer", amount = BigDecimal("1000"), claimedAt = Instant.now()),
        )
        every { dailyStepRewardRepository.countByUserId("user_7") } returns 5L

        When("getting the pet") {
            val pet = service.getPet("user_7")

            Then("real activity moves it forward to the real matching stage") {
                // 1 base + 2 claimed tasks + 5 active days = level 8, which is Fledgling (min 7)
                pet.level shouldBe 8
                pet.stageName shouldBe "Fledgling"
                pet.emoji shouldBe "🕊️"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
