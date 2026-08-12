package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) daily mission row,
 * sourced from a direct user screenshot of the real Toss Shopping tab (2026-08-12):
 * Check-in, Scroll, Draw a prize, and a "Cat" icon, each a real once-per-day
 * claimable reward. Same exact shape as `DailyStepReward` (one real row per user per
 * real calendar day, `missionDate` "yyyy-MM-dd") -- same honest boundary too: "scrolled"
 * means the client reported that the user scrolled through real content, not that this
 * backend independently verified a scroll gesture, the same relationship
 * `DailyStepReward.steps` already has with the client's own device pedometer.
 *
 * Every mission here pays out real RWF credited straight to the user's real wallet via
 * `LedgerService` (see `ShoppingMissionService`), the same real-money-not-fake-points
 * architecture `StepRewardService` already established -- itunda has never built a
 * separate internal points currency, and this doesn't start one.
 *
 * `@Version` for the same real reason `DailyStepReward.version` exists: two concurrent
 * claims of the same mission in the same request window must not both see the flag
 * still `false` and both credit the wallet.
 */
@Entity
@Table(name = "shopping_mission_rewards")
class ShoppingMissionReward(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "mission_date", nullable = false, length = 10)
    val missionDate: String,

    @Column(name = "checked_in", nullable = false)
    var checkedIn: Boolean = false,

    @Column(name = "scrolled", nullable = false)
    var scrolled: Boolean = false,

    @Column(name = "spun", nullable = false)
    var spun: Boolean = false,

    @Column(name = "cat_fed", nullable = false)
    var catFed: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", missionDate = "")
}

/**
 * Real one-time-ever "welcome" bonus claim (the reference screenshot's "Claim re..."
 * icon) -- unlike the daily missions above, this pays out exactly once per account,
 * ever. The primary key (`userId` itself) is the real once-ever guard: a second claim
 * attempt fails a real unique-constraint insert, not an app-level flag check that a
 * race condition could slip past.
 */
@Entity
@Table(name = "shopping_welcome_bonus_claims")
class ShoppingWelcomeBonusClaim(
    @Id
    @Column(name = "user_id", length = 64)
    val userId: String,

    @Column(name = "claimed_at", nullable = false)
    val claimedAt: Instant = Instant.now(),
) {
    protected constructor() : this(userId = "")
}
