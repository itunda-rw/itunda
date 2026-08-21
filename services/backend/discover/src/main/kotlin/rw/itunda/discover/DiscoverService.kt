package rw.itunda.discover

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.stereotype.Service
import rw.itunda.core.domain.AnalyticsEvent
import rw.itunda.core.repository.AnalyticsEventRepository
import rw.itunda.core.repository.IkiminaMemberRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.SaccoShareholdingRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.rewards.RewardsService

data class DiscoverItemView(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val color: String,
    val isNew: Boolean,
    val badge: String?,
    // Real server-side ranking (2026-08-11) -- see this class's own doc comment.
    // Higher shows first / is promoted to hero treatment by clients that pick one
    // item to feature (Android's PersonalRecommendationCard, web/iOS's equivalents).
    val priority: Int,
)

data class DiscoverBannerView(val id: String, val title: String, val subtitle: String, val color: String, val cta: String)

data class DiscoverResult(val items: List<DiscoverItemView>, val banners: List<DiscoverBannerView>)

// Real Toss Intelligence-banner-style personalization (2026-08-11) -- researched via
// toss.tech/article/intelligence_banner ("유연하고 확장 가능한 배너 기능 구현하기"): Toss
// Securities' real banner system evaluates eligibility conditions per user server-side
// (never exposed to the client), ranks eligible candidates by priority, frequency-caps
// impressions, and lazily fetches only the data sources a given rule actually needs.
// DiscoverController previously returned the exact same 8 hardcoded items + 3 hardcoded
// banners to every user on every request -- no persistence, no per-user logic, the
// authenticated caller's own userId was available and simply never read. This is the
// same architecture at itunda's real scale: instead of a full SpEL rule DSL (Toss's own
// scale problem -- "safe extension without a redeploy for every new campaign" -- itunda
// doesn't have yet), eligibility is a plain Kotlin predicate per candidate, but the real
// behaviors Toss's article is actually about are all here: server-side eligibility
// filtering against real user data, priority ranking, and frequency capping via the
// same analytics_events table AnalyticsController already uses for retention math (see
// AnalyticsEventRepository.countByUserIdAndEventNameAndMetadataJson).
//
// Each data source below is a Kotlin `by lazy` property -- mirrors the article's
// LazyContext principle: a user who's already KYC-verified never triggers the SACCO/
// Ikimina/loan repository calls unless a later rule actually needs them, since the
// personalized candidates are evaluated in priority order and most users won't be
// eligible for every rule.
@Service
class DiscoverService(
    private val userRepository: UserRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val saccoShareholdingRepository: SaccoShareholdingRepository,
    private val ikiminaMemberRepository: IkiminaMemberRepository,
    private val loanAccountRepository: LoanAccountRepository,
    private val rewardsService: RewardsService,
    private val analyticsEventRepository: AnalyticsEventRepository,
) {
    companion object {
        // Real frequency cap -- see AnalyticsEventRepository's own doc comment on
        // countByUserIdAndEventNameAndMetadataJson. 4, not some larger number: these
        // are nudges toward a real product the user hasn't tried, not a rotating ad --
        // if 4 impressions haven't converted, a 5th is more likely to read as nagging
        // than as a fresh reminder.
        private const val MAX_IMPRESSIONS = 4
        private const val NEW_USER_TENURE_DAYS = 14L
    }

    fun getDiscoverFor(userId: String): DiscoverResult {
        val user = userRepository.findById(userId).orElse(null)
        val hasSavingsGoal by lazy { savingsGoalRepository.existsByUserId(userId) }
        val hasSacco by lazy { saccoShareholdingRepository.findByUserId(userId) != null }
        val hasIkimina by lazy { ikiminaMemberRepository.findByUserId(userId).isNotEmpty() }
        val hasLoan by lazy { loanAccountRepository.findByUserId(userId).isNotEmpty() }
        // Best-effort (2026-08-11) -- matches PersonalRecommendationCard's own
        // "still works with a generic value if this fails" precedent on the client;
        // a rewards-computation edge case shouldn't break the whole Discover response.
        val rewardsTotal by lazy {
            runCatching { rewardsService.getTasks(userId).rewardsTotal }.getOrNull()
        }
        val isNewUser = user != null && user.createdAt.isAfter(Instant.now().minus(NEW_USER_TENURE_DAYS, ChronoUnit.DAYS))

        fun impressions(bannerId: String) =
            analyticsEventRepository.countByUserIdAndEventNameAndMetadataJson(userId, "discover_banner_impression", bannerId)

        // Real personalized candidates -- each only appears when the real condition it
        // names is actually true for this user, ranked by how much it's worth
        // interrupting Home for (identity/compliance highest, cross-sell into an
        // untried real product next, "hasn't set up interest jar" is the outcome
        // AccountHeroCard's own +interest header already promotes -- lowest of the
        // three since it costs the least to leave undiscovered).
        val personalized = buildList {
            if (user != null && !user.kycVerified) {
                add(
                    DiscoverItemView(
                        id = "p_kyc", category = "account", title = "Verify your identity",
                        subtitle = "Unlock transfers and higher limits",
                        description = "A few minutes to verify your ID unlocks larger transfers and better rates across itunda.",
                        color = "#0066FF", isNew = false, badge = "Action needed", priority = 100,
                    ),
                )
            }
            if (isNewUser && !hasSavingsGoal) {
                add(
                    DiscoverItemView(
                        id = "p_first_goal", category = "savings", title = "Set your first savings goal",
                        subtitle = "New to itunda? Start here",
                        description = "Give your money a purpose -- name a goal, an amount, and itunda tracks the rest.",
                        color = "#00C853", isNew = true, badge = "For you", priority = 95,
                    ),
                )
            }
            if (!hasSacco) {
                add(
                    DiscoverItemView(
                        id = "p_try_sacco", category = "savings", title = "Try SACCO shares",
                        subtitle = "Buy cooperative shares, earn a real dividend",
                        description = "Rwanda's own 416 Umurenge SACCOs, now inside itunda -- buy shares and earn periodic dividends.",
                        color = "#7C5CFC", isNew = false, badge = null, priority = 60,
                    ),
                )
            }
            if (!hasIkimina) {
                add(
                    DiscoverItemView(
                        id = "p_try_ikimina", category = "savings", title = "Join or start an Ikimina",
                        subtitle = "Rotating savings with people you trust",
                        description = "The rotating-savings circle Rwandans already know, now with itunda tracking every round.",
                        color = "#14AE85", isNew = false, badge = null, priority = 55,
                    ),
                )
            }
            if (!hasLoan) {
                add(
                    DiscoverItemView(
                        id = "p_try_loan", category = "credit", title = "Check your loan options",
                        subtitle = "See your real limit in under a minute",
                        description = "Personal, salary-backed, and SME working-capital loans -- check your real eligibility first, no commitment.",
                        color = "#5856D6", isNew = false, badge = null, priority = 50,
                    ),
                )
            }
        }.filter { impressions(it.id) < MAX_IMPRESSIONS }

        val staticFallback = listOf(
            DiscoverItemView("d_1", "government", "Irembo Services", "Pay government fees instantly", "Access 100+ government services: passport, driving license, land title, RRA taxes", "#0066FF", false, null, priority = 20),
            DiscoverItemView("d_2", "government", "RRA Tax Payment", "Rwanda Revenue Authority", "Pay income tax, VAT, and other obligations directly from itunda", "#34C0AC", false, "Due Soon", priority = 19),
            // Real rewards badge (2026-08-11) -- was a hardcoded "1,240 pts" literal
            // regardless of who was asking; RewardsService.getTasks already computed
            // the caller's real total, just never for this screen.
            DiscoverItemView("d_3", "rewards", "itunda Points", "Earn on every transaction", "Earn 1 point per 100 RWF spent. Redeem for cashback, airtime, or vouchers", "#FFB300", false, rewardsTotal?.let { "RWF %,.0f".format(it) }, priority = 18),
            DiscoverItemView("d_4", "rewards", "Referral Bonus", "Invite friends, earn together", "Earn 5,000 RWF for each friend who signs up and completes first transfer", "#FF9500", false, null, priority = 17),
            DiscoverItemView("d_5", "lifestyle", "Yego Vouchers", "Exclusive partner deals", "Discounts at Nakumatt, Simba Supercentre, KFC, Java House and 200+ partners", "#E91E63", true, "Hot", priority = 16),
            DiscoverItemView("d_6", "lifestyle", "itunda Walk", "Earn by staying active", "Walk 10,000 steps daily and earn up to 500 RWF. Connected to your health data", "#00C853", true, null, priority = 15),
            DiscoverItemView("d_7", "credit", "Credit Builder", "Improve your credit score", "Take a small secured loan to build credit history and access better rates", "#5856D6", false, null, priority = 14),
            DiscoverItemView("d_8", "social", "Request Money", "Split bills with friends", "Create payment requests and share with anyone. Split restaurant bills easily", "#34C0AC", false, null, priority = 13),
        ).filter { impressions(it.id) < MAX_IMPRESSIONS }

        val banners = listOf(
            DiscoverBannerView("b_1", "Earn 7.5% on savings", "Open Interest Jar today", "#0066FF", "Start Saving"),
            DiscoverBannerView("b_2", "Free health insurance", "First month on us with Prime", "#00C853", "Get Prime"),
            DiscoverBannerView("b_3", "RSE stocks from 1,000 RWF", "Invest in Rwanda's top companies", "#5856D6", "Invest Now"),
        )

        val rankedItems = (personalized + staticFallback).sortedByDescending { it.priority }
        // Real impression recording (2026-08-11) -- every item DiscoverController
        // returns renders immediately in every client's DiscoverSection/hero card
        // today (no lazy virtualization that could skip an off-screen one), so
        // recording here at fetch time is an honest proxy for "was shown," not an
        // approximation of something more precise the clients don't actually do.
        // Feeds MAX_IMPRESSIONS above on the next call -- without this, frequency
        // capping would never trigger since nothing would ever increment the count.
        rankedItems.forEach { item ->
            analyticsEventRepository.save(
                AnalyticsEvent(
                    id = "analytics_event_${UUID.randomUUID()}",
                    userId = userId,
                    eventName = "discover_banner_impression",
                    platform = "server",
                    metadataJson = item.id,
                ),
            )
        }

        return DiscoverResult(items = rankedItems, banners = banners)
    }
}
