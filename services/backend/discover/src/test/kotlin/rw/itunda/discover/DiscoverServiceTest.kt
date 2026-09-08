package rw.itunda.discover

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import rw.itunda.core.domain.AnalyticsEvent
import rw.itunda.core.domain.IkiminaMember
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.SaccoShareholding
import rw.itunda.core.domain.User
import rw.itunda.core.repository.AnalyticsEventRepository
import rw.itunda.core.repository.IkiminaMemberRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.SaccoShareholdingRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.rewards.RewardTasksResult
import rw.itunda.rewards.RewardsService

/**
 * Real live-caught bug (2026-08-26): impression recording used to be unconditional
 * on every single getDiscoverFor call, so countByUserIdAndEventNameAndMetadataJson
 * counted raw API polls, not genuine sightings -- a handful of real app relaunches in
 * one day permanently exhausted MAX_IMPRESSIONS=4 for every item, forever, with no
 * reset. Caught live via this session's own repeated test relaunches emptying the
 * Home Discover feed. See DiscoverService's own doc comment for the full account.
 */
class DiscoverServiceTest : BehaviorSpec({

    // An old, fully-onboarded, non-new user so `personalized` is always empty and
    // every test only has to reason about the 8 real staticFallback items.
    val user = User(
        id = "user_1", phoneNumber = "0788000000", firstName = "Test", lastName = "User",
        passwordHash = "hash", kycVerified = true, createdAt = Instant.now().minus(365, ChronoUnit.DAYS),
    )

    fun mockAnalyticsEventRepository(): AnalyticsEventRepository {
        val repository = mockk<AnalyticsEventRepository>(relaxed = true)
        // Real MockK quirk, not itunda-specific: JpaRepository's real save<S extends
        // T>(S): S signature is generic, and a relaxed mock's default answer can't
        // infer/cast it correctly (throws ClassCastException at call time) without
        // this explicit stub -- same pattern MenuOptionServiceTest.kt already
        // establishes for the identical situation. saveAll has the same generic
        // shape (2026-09-08 N+1 fix batched the per-item save into one saveAll).
        every { repository.save(any()) } answers { firstArg() }
        every { repository.saveAll(any<List<AnalyticsEvent>>()) } answers { firstArg() }
        return repository
    }

    fun buildService(analyticsEventRepository: AnalyticsEventRepository): DiscoverService {
        val userRepository = mockk<UserRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val saccoShareholdingRepository = mockk<SaccoShareholdingRepository>()
        val ikiminaMemberRepository = mockk<IkiminaMemberRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val rewardsService = mockk<RewardsService>()

        every { userRepository.findById("user_1") } returns Optional.of(user)
        every { savingsGoalRepository.existsByUserId("user_1") } returns true
        every { saccoShareholdingRepository.findByUserId("user_1") } returns mockk<SaccoShareholding>()
        every { ikiminaMemberRepository.findByUserId("user_1") } returns listOf(mockk<IkiminaMember>())
        every { loanAccountRepository.findByUserId("user_1") } returns listOf(mockk<LoanAccount>())
        every { rewardsService.getTasks("user_1") } returns RewardTasksResult(emptyList(), BigDecimal.ZERO)

        return DiscoverService(
            userRepository, savingsGoalRepository, saccoShareholdingRepository,
            ikiminaMemberRepository, loanAccountRepository, rewardsService, analyticsEventRepository,
        )
    }

    Given("a user who has already been shown an item earlier today") {
        val analyticsEventRepository = mockAnalyticsEventRepository()
        every { analyticsEventRepository.countByUserIdAndEventNameAndMetadataJson("user_1", "discover_banner_impression", any()) } returns 1
        // Real N+1 fix (2026-09-08): the per-item existsBy... check is now one
        // batched findBy...In query -- stub it to report d_1 as already recorded.
        every { analyticsEventRepository.findByUserIdAndEventNameAndMetadataJsonInAndCreatedAtAfter(any(), any(), any(), any()) } returns listOf(
            AnalyticsEvent(id = "analytics_event_seed", userId = "user_1", eventName = "discover_banner_impression", platform = "server", metadataJson = "d_1"),
        )
        val service = buildService(analyticsEventRepository)

        When("fetching Discover a second time the same day") {
            val result = service.getDiscoverFor("user_1")

            Then("the already-seen item still renders (real cap not yet hit)") {
                result.items.any { it.id == "d_1" } shouldBe true
            }
            Then("no duplicate impression row is recorded for it") {
                verify(exactly = 0) { analyticsEventRepository.saveAll(match<List<AnalyticsEvent>> { it.any { e -> e.metadataJson == "d_1" } }) }
            }
        }
    }

    Given("a user seeing an item for the first time today") {
        val analyticsEventRepository = mockAnalyticsEventRepository()
        every { analyticsEventRepository.countByUserIdAndEventNameAndMetadataJson("user_1", "discover_banner_impression", any()) } returns 0
        every { analyticsEventRepository.findByUserIdAndEventNameAndMetadataJsonInAndCreatedAtAfter(any(), any(), any(), any()) } returns emptyList()
        val service = buildService(analyticsEventRepository)

        When("fetching Discover") {
            service.getDiscoverFor("user_1")

            Then("a real impression row is recorded once per item, batched into one saveAll") {
                verify(exactly = 1) { analyticsEventRepository.saveAll(match<List<AnalyticsEvent>> { it.any { e -> e.metadataJson == "d_1" } }) }
            }

            // Real N+1 fix (2026-09-08) -- this used to be one existsBy... query plus
            // up to one save() per ranked item; proves it's now exactly one batched
            // findBy...In query and one saveAll(), regardless of how many of the real
            // 8 staticFallback items are new today.
            Then("it queries for already-recorded impressions exactly once, not once per item") {
                verify(exactly = 1) { analyticsEventRepository.findByUserIdAndEventNameAndMetadataJsonInAndCreatedAtAfter(any(), any(), any(), any()) }
            }
            Then("it writes every new impression in exactly one saveAll call, never a per-item save") {
                verify(exactly = 1) { analyticsEventRepository.saveAll(any<List<AnalyticsEvent>>()) }
                verify(exactly = 0) { analyticsEventRepository.save(any()) }
            }
        }
    }

    Given("an item already shown across 4 distinct days (the real cap)") {
        val analyticsEventRepository = mockAnalyticsEventRepository()
        every { analyticsEventRepository.countByUserIdAndEventNameAndMetadataJson("user_1", "discover_banner_impression", "d_1") } returns 4
        every { analyticsEventRepository.countByUserIdAndEventNameAndMetadataJson("user_1", "discover_banner_impression", match { it != "d_1" }) } returns 0
        every { analyticsEventRepository.findByUserIdAndEventNameAndMetadataJsonInAndCreatedAtAfter(any(), any(), any(), any()) } returns emptyList()
        val service = buildService(analyticsEventRepository)

        When("fetching Discover") {
            val result = service.getDiscoverFor("user_1")

            Then("the exhausted item is real filtered out, the rest still show") {
                result.items.any { it.id == "d_1" } shouldBe false
                result.items.any { it.id == "d_2" } shouldBe true
            }
        }
    }
})
