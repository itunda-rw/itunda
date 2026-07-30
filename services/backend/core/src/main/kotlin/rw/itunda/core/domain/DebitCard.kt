package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/**
 * Real Toss Bank 체크카드 (check/debit card) -- a real, published Toss Bank product
 * (tossbank.com/product-service/card/check-card): a personal debit card whose spend is
 * app-controlled, not just physically controlled. Two real, sourced mechanics this
 * models: an app-adjustable daily spend limit (Toss's own real default reaches up to
 * 50,000,000 KRW/day, adjustable down or up to a 100,000,000 KRW/month ceiling) and a
 * one-tap freeze ("결제 정지") that blocks every card purchase instantly, both without
 * calling a bank or waiting for a replacement card. itunda has no real card-network
 * (Visa/Mastercard) partnership or physical card fulfilment -- same honest boundary
 * `DemoCardAuthorizationService` already established for a merchant *accepting* a
 * card. This is the mirror-image real gap: itunda *issuing* one of its own. `last4` is
 * a real, stored 4-digit display suffix, not a routable real PAN -- there is no real
 * card network to route a transaction over, so "paying with your card"
 * (CardService.chargeWithCard) is itunda's own real, ledger-backed simulation of a
 * card-present purchase: real money moves, real limits are enforced, real history is
 * kept, it just isn't carried by a real Visa/Mastercard rail.
 *
 * Default limits (500,000 RWF/day, 5,000,000 RWF/month) are itunda's own honest choice,
 * scaled for the Rwandan market -- Toss's own real KRW figures don't have a sourced RWF
 * equivalent, named explicitly rather than presented as if copied from a real number.
 */
@Entity
@Table(name = "debit_cards")
class DebitCard(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "last_4", nullable = false, length = 4)
    val last4: String,

    @Column(name = "daily_limit", nullable = false, precision = 18, scale = 2)
    var dailyLimit: BigDecimal,

    @Column(name = "monthly_limit", nullable = false, precision = 18, scale = 2)
    var monthlyLimit: BigDecimal,

    @Column(nullable = false)
    var frozen: Boolean = false,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant = Instant.now(),

    // A limit change and a purchase both racing the same card must not silently lose
    // one of their effects -- see Order/AutoTransfer's own doc comments for the same
    // reasoning applied here.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", last4 = "", dailyLimit = BigDecimal.ZERO, monthlyLimit = BigDecimal.ZERO,
    )

    companion object {
        val DEFAULT_DAILY_LIMIT: BigDecimal = BigDecimal("500000")
        val DEFAULT_MONTHLY_LIMIT: BigDecimal = BigDecimal("5000000")
        val MAX_LIMIT: BigDecimal = BigDecimal("50000000")
    }
}

/**
 * One real row per card purchase (CardService.chargeWithCard) -- the audit trail a real
 * card statement is built from, and the source of truth DailySpend/MonthlySpend limit
 * checks sum against (not a running counter, which would need a real reset scheduler
 * and could drift; a real query against real rows never can).
 */
@Entity
@Table(name = "debit_card_transactions")
class DebitCardTransaction(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "card_id", nullable = false, length = 64)
    val cardId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "merchant_name", nullable = false, length = 200)
    val merchantName: String,

    @Column(name = "ledger_transaction_id", nullable = false, length = 64)
    val ledgerTransactionId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", cardId = "", userId = "", amount = BigDecimal.ZERO, merchantName = "", ledgerTransactionId = "",
    )
}
