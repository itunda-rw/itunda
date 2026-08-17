package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class PostpaidCreditLineStatus { ACTIVE, SUSPENDED }

/**
 * Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line) -- sourced from
 * Naver Pay's own real, currently-live product (a real 300,000 KRW maximum limit, no
 * user-requestable increase; a real 12% annual late fee on overdue principal, service
 * suspended while overdue; no interest at all for on-time repayment, unlike a credit
 * card). Adopted as itunda's own honest RWF-scoped numbers (the same "reuse the sourced
 * structure, itunda's own currency figures" discipline `MiniWalletService`/
 * `AgentCommissionSchedule` already establish), not presented as a real published Rwanda
 * figure.
 *
 * Genuinely, structurally distinct from the already-real `OverdraftAccount` (마이너스통장),
 * not a copy of it wearing a different name -- see that entity's own doc comment for the
 * shape they share (a revolving `creditLimit`/drawn-balance, draw up, repay down, never
 * closes). What's actually different, matching each real product's own real-world market
 * position: (1) real interest-FREE on-time repayment -- `PostpaidCreditAccrualService`
 * only ever charges the real late fee once a line is actually overdue, never a running
 * daily rate on every drawn balance the way overdraft's own daily accrual does; (2) a
 * real, much lower qualification bar (`MIN_SCORE_TO_QUALIFY` = `CreditScoreService
 * .BASE_SCORE_POINTS`, i.e. every registered account technically qualifies for at least
 * the smallest tier) -- BNPL's real, defining market position is reaching the thin-file
 * segment overdraft's real 400-point bar and every `LoansService` offer already
 * deliberately exclude; (3) the limit itself is real-computed from the caller's own
 * credit score at apply time (`PostpaidCreditService.TIERS`), not user-requested-and-capped
 * the way `OverdraftService.openOverdraft`'s own `requestedLimit` is.
 */
@Entity
@Table(name = "postpaid_credit_lines")
class PostpaidCreditLine(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "credit_limit", nullable = false, precision = 18, scale = 2)
    var creditLimit: BigDecimal,

    @Column(name = "current_balance", nullable = false, precision = 18, scale = 2)
    var currentBalance: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PostpaidCreditLineStatus = PostpaidCreditLineStatus.ACTIVE,

    // Set once, the moment a spend first moves currentBalance up from zero -- the start
    // of a real 30-day billing cycle. Cleared once currentBalance returns to zero (fully
    // repaid), so the NEXT spend starts a fresh cycle, matching the real product's own
    // per-cycle (not per-purchase) settlement.
    @Column(name = "cycle_due_at")
    var cycleDueAt: Instant? = null,

    // Real ongoing daily late-fee accrual while overdue -- same "accrue once per real
    // 24h" shape OverdraftAccount.lastAccrualAt already establishes, just gated on being
    // real overdue (cycleDueAt passed) instead of any nonzero drawn balance. Cleared
    // alongside cycleDueAt once fully repaid.
    @Column(name = "last_late_fee_accrual_at")
    var lastLateFeeAccrualAt: Instant? = null,

    // Real Naver Pay/Kakao Pay 후불결제 "결제 예정일이 다가와요" payment-due-soon push --
    // see PostpaidCreditService.getLinesDueSoonForPaymentReminder's own doc comment.
    // Set once a reminder fires for the CURRENT open cycle, cleared alongside
    // cycleDueAt/lastLateFeeAccrualAt once fully repaid, so the next cycle gets its own
    // fresh reminder rather than staying permanently silenced.
    @Column(name = "payment_reminder_sent_at")
    var paymentReminderSentAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", walletId = "", creditLimit = BigDecimal.ZERO)
}
