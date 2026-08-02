package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
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
    val policyNumber: String
) {
    protected constructor() : this(id = "", userId = "", planId = "", planName = "", category = "", status = "", startDate = LocalDate.now(), endDate = LocalDate.now(), monthlyPremium = BigDecimal.ZERO, nextPaymentDate = LocalDate.now(), policyNumber = "")
}
