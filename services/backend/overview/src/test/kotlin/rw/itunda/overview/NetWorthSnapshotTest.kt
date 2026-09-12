package rw.itunda.overview

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.NetWorthSnapshot
import rw.itunda.core.domain.RewardClaim
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.NetWorthSnapshotRepository
import rw.itunda.core.repository.RewardClaimRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.VehicleRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/** OverviewService.captureSnapshot/hasSnapshotToday/getNetWorthHistory coverage --
 * extracted from OverviewServiceTest.kt from the start (not reactively, once it
 * crossed file-size-lint), since real net-worth-trend tracking is a genuinely
 * distinct concern from the main getOverview aggregation and doesn't need most of
 * that file's own 10-mock setup. See NetWorthSnapshot.kt's own doc comment for the
 * full sourced account (real Toss "자산 변화" reference). */
class NetWorthSnapshotTest : BehaviorSpec({

    fun newService(
        accountRepository: AccountRepository = mockk(),
        rewardClaimRepository: RewardClaimRepository = mockk(),
        netWorthSnapshotRepository: NetWorthSnapshotRepository = mockk(),
    ) = OverviewService(
        accountRepository, mockk(), mockk(), mockk(), mockk(), mockk(),
        mockk<DebitCardRepository>(), mockk<VehicleRepository>(), mockk<TransactionRepository>(), rewardClaimRepository, netWorthSnapshotRepository,
    )

    Given("a real user with real account balances and real reward points") {
        val accountRepository = mockk<AccountRepository>()
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val netWorthSnapshotRepository = mockk<NetWorthSnapshotRepository>()
        every { accountRepository.findByUserId("user_1") } returns listOf(
            Account(id = "w1", userId = "user_1", accountNumber = "ACC1", accountName = "Main", type = AccountType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000")),
            Account(id = "pay1", userId = "user_1", accountNumber = "ACC2", accountName = "Pay", type = AccountType.PAY, balance = BigDecimal("3000"), availableBalance = BigDecimal("3000")),
        )
        every { rewardClaimRepository.findByUserId("user_1") } returns listOf(
            RewardClaim(id = "rc1", userId = "user_1", taskId = "task_1", amount = BigDecimal("200"), claimedAt = Instant.now()),
        )
        val savedSlot = slot<NetWorthSnapshot>()
        every { netWorthSnapshotRepository.save(capture(savedSlot)) } answers { firstArg() }
        val service = newService(accountRepository, rewardClaimRepository, netWorthSnapshotRepository)

        When("capturing a snapshot") {
            val snapshot = service.captureSnapshot("user_1")

            // Real cross-currency-correctness-adjacent scope decision (see
            // NetWorthSnapshot's own doc comment): only account balances + reward
            // points, deliberately narrower than the wider netWorth figure
            // getOverview computes (which also folds in savings/investments/loans).
            Then("liquidTotal is real account balances plus real reward points, nothing else") {
                snapshot.liquidTotal shouldBe BigDecimal("53200")
                snapshot.userId shouldBe "user_1"
            }
        }
    }

    Given("a user who already has a snapshot captured earlier today") {
        val netWorthSnapshotRepository = mockk<NetWorthSnapshotRepository>()
        val sinceSlot = slot<Instant>()
        every { netWorthSnapshotRepository.existsByUserIdAndCapturedAtGreaterThanEqual("user_1", capture(sinceSlot)) } returns true
        val service = newService(netWorthSnapshotRepository = netWorthSnapshotRepository)

        When("checking hasSnapshotToday") {
            val result = service.hasSnapshotToday("user_1")

            Then("it real-reports true") {
                result shouldBe true
            }
        }
    }

    Given("real users, only one of whom already has a snapshot captured earlier today") {
        val netWorthSnapshotRepository = mockk<NetWorthSnapshotRepository>()
        val sinceSlot = slot<Instant>()
        every { netWorthSnapshotRepository.findDistinctUserIdsCapturedSince(capture(sinceSlot)) } returns listOf("user_1")
        val service = newService(netWorthSnapshotRepository = netWorthSnapshotRepository)

        When("checking getUserIdsWithSnapshotToday") {
            val result = service.getUserIdsWithSnapshotToday()

            Then("it real-reports only that one user, so NetWorthSnapshotScheduler skips them without a per-user query") {
                result shouldBe setOf("user_1")
            }
        }
    }

    Given("a user with 3 months of real snapshot history") {
        val netWorthSnapshotRepository = mockk<NetWorthSnapshotRepository>()
        val now = Instant.now()
        every { netWorthSnapshotRepository.findByUserIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc("user_1", any()) } returns listOf(
            // Two snapshots in the SAME month -- getNetWorthHistory must keep only
            // the later (month-end) one, never average or otherwise fabricate a
            // number this user's real history never had.
            NetWorthSnapshot(id = "s1", userId = "user_1", liquidTotal = BigDecimal("10000"), capturedAt = now.minus(65, ChronoUnit.DAYS)),
            NetWorthSnapshot(id = "s2", userId = "user_1", liquidTotal = BigDecimal("12000"), capturedAt = now.minus(35, ChronoUnit.DAYS)),
            NetWorthSnapshot(id = "s3", userId = "user_1", liquidTotal = BigDecimal("15000"), capturedAt = now.minus(30, ChronoUnit.DAYS)),
            NetWorthSnapshot(id = "s4", userId = "user_1", liquidTotal = BigDecimal("18000"), capturedAt = now),
        )
        val service = newService(netWorthSnapshotRepository = netWorthSnapshotRepository)

        When("fetching net worth history") {
            val history = service.getNetWorthHistory("user_1")

            Then("each month keeps only its last (month-end) snapshot, real months in order") {
                history.size shouldBe 3
                history.map { it.liquidTotal } shouldBe listOf(BigDecimal("10000"), BigDecimal("15000"), BigDecimal("18000"))
            }
        }
    }

    Given("a brand new user with zero snapshot history") {
        val netWorthSnapshotRepository = mockk<NetWorthSnapshotRepository>()
        every { netWorthSnapshotRepository.findByUserIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc("user_2", any()) } returns emptyList()
        val service = newService(netWorthSnapshotRepository = netWorthSnapshotRepository)

        When("fetching net worth history") {
            val history = service.getNetWorthHistory("user_2")

            Then("it's honestly empty, not a fabricated backfilled trend") {
                history shouldBe emptyList()
            }
        }
    }

    Given("real users, one already snapshotted today and one not") {
        val userRepository = mockk<rw.itunda.core.repository.UserRepository>()
        val overviewService = mockk<OverviewService>()
        every { userRepository.findAll() } returns listOf(
            rw.itunda.core.domain.User(id = "user_1", phoneNumber = "0780000001", firstName = "A", lastName = "One", passwordHash = "x"),
            rw.itunda.core.domain.User(id = "user_2", phoneNumber = "0780000002", firstName = "B", lastName = "Two", passwordHash = "x"),
        )
        every { overviewService.getUserIdsWithSnapshotToday() } returns setOf("user_1")
        every { overviewService.captureSnapshot("user_2") } returns NetWorthSnapshot(id = "s1", userId = "user_2", liquidTotal = BigDecimal.ZERO)
        val scheduler = NetWorthSnapshotScheduler(userRepository, overviewService)

        When("running the scheduler's captureAll") {
            val captured = scheduler.captureAll()

            Then("only the user without today's snapshot gets a new one -- no duplicate row for the other") {
                captured shouldBe 1
                verify(exactly = 0) { overviewService.captureSnapshot("user_1") }
                verify(exactly = 1) { overviewService.captureSnapshot("user_2") }
            }
            // Real N+1 regression test (2026-09-12): captureAll used to call
            // hasSnapshotToday once per user inside the loop -- confirm it's now
            // exactly ONE batched call regardless of how many real users exist.
            Then("it checks the whole user base's snapshot status with exactly one batched call, not one per user") {
                verify(exactly = 1) { overviewService.getUserIdsWithSnapshotToday() }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
