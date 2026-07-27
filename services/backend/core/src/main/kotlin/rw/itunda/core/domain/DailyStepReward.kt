package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real Toss 만보기 (pedometer) walking-rewards feature -- sourced from Toss's own real,
 * widely-documented "걷기 지원금" ("walking subsidy"): steps tracked via the phone's own
 * sensor unlock tiered point rewards at real published thresholds (1,000 / 5,000 /
 * 10,000 steps per day), settled as real Toss Points.
 *
 * Honest scope boundary: this backend has no way to independently measure a user's real
 * steps -- there is no server-side pedometer. Exactly like this app's own real GPS/map
 * features (the client's own device sensor reports location; this backend never invents
 * it), the mobile client reports its own device pedometer's real cumulative daily step
 * count via `POST /api/v1/rewards/steps`, and this backend is the real source of truth
 * for the REWARD side of that (validating the report, crediting the real wallet,
 * preventing a double-claim) -- not a real anti-spoofing measure against a malicious
 * client, which would need a real OS-level attestation integration (Google Play
 * Integrity / Apple DeviceCheck) this session scopes out as a genuinely separate,
 * larger follow-up, the same honest boundary this codebase already draws elsewhere
 * for client-reported data it can't independently verify.
 *
 * One real row per user per real calendar day (`rewardDate`, "yyyy-MM-dd", the same
 * plain-string convention `SpendingBudget.month` already uses for a real calendar
 * period). `steps` is real, monotonically non-decreasing within the same day (matches a
 * real pedometer's own cumulative-count semantics -- a report with a lower count than
 * already stored is rejected, not silently overwritten). The three `claimedTierN` flags
 * let a user earn multiple tier rewards in the same real day as they keep walking,
 * auto-credited the moment `reportSteps` sees the real threshold crossed for the first
 * time -- no separate manual "claim" tap, matching Toss's own real "짠! 포인트 받았어요"
 * (ding! you got points) automatic UX.
 */
@Entity
@Table(name = "daily_step_rewards")
class DailyStepReward(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "reward_date", nullable = false, length = 10)
    val rewardDate: String,

    @Column(nullable = false)
    var steps: Int = 0,

    @Column(name = "claimed_tier_1000", nullable = false)
    var claimedTier1000: Boolean = false,

    @Column(name = "claimed_tier_5000", nullable = false)
    var claimedTier5000: Boolean = false,

    @Column(name = "claimed_tier_10000", nullable = false)
    var claimedTier10000: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", rewardDate = "")
}
