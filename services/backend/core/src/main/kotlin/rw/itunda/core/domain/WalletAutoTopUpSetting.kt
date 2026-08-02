package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * A real Naver Pay Money 자동충전 (auto-charge) equivalent -- see AutoTopUpService's own
 * doc comment for the full sourcing and account. A standing rule, one per real wallet
 * (unique on `walletId`), not a payment-time hook: `WalletService.confirmTransfer` and
 * every other real money-moving method stay completely untouched, matching how Naver's
 * own real auto-charge is a background threshold check, not something wired into each
 * individual payment flow.
 *
 * `triggersToday`/`lastTriggerDate` are a real, simple daily-cap guard against a
 * flapping balance (repeatedly dipping below threshold, topping up, spending again)
 * firing runaway top-ups -- resets automatically whenever `lastTriggerDate` isn't
 * today, evaluated in `AutoTopUpService` rather than a separate scheduled reset job.
 */
@Entity
@Table(name = "wallet_auto_topup_settings")
class WalletAutoTopUpSetting(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "linked_account_id", nullable = false, length = 64)
    var linkedAccountId: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "threshold_amount", nullable = false, precision = 18, scale = 2)
    var thresholdAmount: BigDecimal,

    @Column(name = "top_up_amount", nullable = false, precision = 18, scale = 2)
    var topUpAmount: BigDecimal,

    @Column(name = "daily_trigger_cap", nullable = false)
    var dailyTriggerCap: Int = 3,

    @Column(name = "triggers_today", nullable = false)
    var triggersToday: Int = 0,

    @Column(name = "last_trigger_date")
    var lastTriggerDate: LocalDate? = null,

    @Column(name = "last_triggered_at")
    var lastTriggeredAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // Real bug found live (2026-08-02): AutoTopUpService.evaluateAndTopUp/topUpShortfall
    // both read this exact entity, check-then-act (enabled, dailyTriggerCap vs
    // triggersToday, wallet balance vs threshold), pull real money over an external
    // rail, THEN write triggersToday/lastTriggeredAt back -- the same check-then-act
    // shape every other real money-moving entity in this codebase already needed
    // `@Version` for. With none here, two concurrent triggers for the same wallet
    // (a user double-tapping POST .../auto-topup/trigger, or a trigger racing the
    // background AutoTopUpScheduler sweep) could both read `triggersToday` below
    // `dailyTriggerCap` before either committed, both pull and credit the wallet, and
    // silently bypass the real daily cap this field exists to enforce.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", walletId = "", linkedAccountId = "",
        thresholdAmount = BigDecimal.ZERO, topUpAmount = BigDecimal.ZERO,
    )
}
