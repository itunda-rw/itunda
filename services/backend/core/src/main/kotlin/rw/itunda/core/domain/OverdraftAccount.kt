package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class OverdraftAccountStatus { ACTIVE, CLOSED }

/**
 * Real Toss Bank/KakaoBank 마이너스통장 (literally "minus account", an overdraft /
 * revolving line-of-credit) -- sourced from Toss Bank's own real published product
 * (tossbank.com/product-service/loans/minus-account) and KakaoBank's own real
 * 마이너스 통장대출 (kakaobank.com/products/creditLine): a real pre-approved credit
 * LIMIT, not a lump-sum disbursement -- the borrower draws any amount up to the limit
 * whenever they need it, and real interest accrues only on the actual drawn balance
 * for the actual days it's outstanding (Toss's own real copy: "쓴 만큼 이자 내는
 * 마이너스통장" -- "pay interest only on what you actually used"), not a fixed
 * repayment schedule on a fixed principal.
 *
 * Genuinely, structurally distinct from the existing lump-sum `LoanAccount` (see
 * `LoansService.applyForLoan`'s own doc comment): a term loan disburses its full
 * `principal` once and amortizes down to `PAID`; this account's `creditLimit` never
 * changes, `drawnBalance` can be drawn UP and repaid DOWN repeatedly over the account's
 * real lifetime (revolving), and a real repayment never closes the account -- it just
 * frees up real available credit (`creditLimit - drawnBalance`) to draw again, exactly
 * like the real product both banks describe.
 *
 * Reuses the exact real `LOAN_PAYABLE` ledger account `LoansService` already
 * establishes for a borrower's real amount owed to itunda (a draw debits it, a
 * repayment credits it, the identical shape) -- an overdraft's drawn balance is, from
 * itunda's own ledger's perspective, the same real kind of receivable a term loan's
 * outstanding principal already is, just revolving instead of amortizing. Real daily
 * interest accrual (`OverdraftInterestAccrualScheduler`) additionally debits
 * `LOAN_PAYABLE` (the borrower now owes more) and credits the new dedicated
 * `INTEREST_INCOME` account (itunda's own real earned revenue), only for accounts with
 * a real nonzero `drawnBalance` -- an account sitting fully repaid at zero accrues
 * real zero interest, matching the sourced "pay interest only on what you actually
 * used" behavior exactly.
 */
@Entity
@Table(name = "overdraft_accounts")
class OverdraftAccount(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "credit_limit", nullable = false, precision = 18, scale = 2)
    val creditLimit: BigDecimal,

    @Column(name = "drawn_balance", nullable = false, precision = 18, scale = 2)
    var drawnBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "interest_rate", nullable = false)
    val interestRate: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: OverdraftAccountStatus = OverdraftAccountStatus.ACTIVE,

    @Column(name = "last_accrual_at")
    var lastAccrualAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", accountId = "", creditLimit = BigDecimal.ZERO, interestRate = 0.0)
}
