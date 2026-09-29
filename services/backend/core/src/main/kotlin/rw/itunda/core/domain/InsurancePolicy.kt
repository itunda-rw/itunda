package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "insurance_policies")
class InsurancePolicy(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "plan_id", nullable = false, length = 64)
    val planId: String,

    @Column(name = "plan_name", nullable = false)
    val planName: String,

    @Column(nullable = false)
    val category: String,

    @Column(nullable = false, length = 32)
    var status: String,

    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    val endDate: LocalDate,

    @Column(name = "monthly_premium", nullable = false)
    val monthlyPremium: BigDecimal,

    @Column(name = "next_payment_date", nullable = false)
    var nextPaymentDate: LocalDate,

    @Column(name = "policy_number", nullable = false, length = 64)
    val policyNumber: String,

    // Real Kakao Pay Insurance/Toss Insurance 갱신 안내 (renewal notice) -- real Korean
    // insurers send a renewal reminder 30-45 days before a policy's term expires, via
    // KakaoTalk among other channels. itunda's own honest scoping: 30 days (the lower
    // bound of that sourced range, not a fabricated figure), since it's the earliest a
    // real insurer would already be reminding by. Null until a real reminder has been
    // sent, same "re-check right before sending, never re-fire" discipline
    // SavingsGoal.maturityNotifiedAt already establishes for a structurally identical
    // one-shot date-based reminder.
    @Column(name = "renewal_reminder_sent_at")
    var renewalReminderSentAt: Instant? = null,
) {
    protected constructor() : this(id = "", userId = "", planId = "", planName = "", category = "", status = "", startDate = LocalDate.now(), endDate = LocalDate.now(), monthlyPremium = BigDecimal.ZERO, nextPaymentDate = LocalDate.now(), policyNumber = "")
}
