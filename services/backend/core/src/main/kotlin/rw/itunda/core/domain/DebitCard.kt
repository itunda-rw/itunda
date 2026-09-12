package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
 *
 * `design` (2026-08-27, direct user instruction: "update itunda bank with all those
 * cards designs allowing users to choose from those designs... that's how toss does it
 * too") -- real Toss Bank precedent (namu.wiki: 5 named colorways, e.g. 레몬 블루/오렌지
 * 밀크/나이트 핑크). itunda's own 5, each a real front/back color-split validated in the
 * card-lineup design pass: [[DebitCardDesign]] is the single whitelist both this entity
 * and CardService's validation share, so a design can never be persisted that no client
 * actually knows how to render.
 */
@Entity
@Table(name = "debit_cards")
class DebitCard(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    // `var` (2026-09-01): CardService.reissue regenerates this in place on a real
    // "카드 재발급" -- see this class's own doc comment on why reissue mutates the
    // same row instead of creating a second DebitCard.
    @Column(name = "last_4", nullable = false, length = 4)
    var last4: String,

    @Column(name = "daily_limit", nullable = false, precision = 18, scale = 2)
    var dailyLimit: BigDecimal,

    @Column(name = "monthly_limit", nullable = false, precision = 18, scale = 2)
    var monthlyLimit: BigDecimal,

    @Column(nullable = false)
    var frozen: Boolean = false,

    // Real "분실신고"/"카드 해지하기" (report lost-or-stolen / close card) states
    // (2026-09-01, direct user-supplied Toss Bank card-management screenshots) --
    // deliberately distinct from `frozen` above: `frozen` is a self-service toggle
    // the user can flip back at will (`unfreeze`), while `lost`/`closedAt` are
    // one-way -- see CardService.unfreeze's own real block on unfreezing either
    // state. Closes a real gap bank-mfe's own CardView had already found and
    // flagged live: "Report lost or stolen" used to just relabel the ordinary
    // freeze() call because "itunda has no distinct lost-card-report flow on the
    // backend" -- this is that real, distinct flow.
    @Column(nullable = false)
    var lost: Boolean = false,

    @Column(name = "closed_at")
    var closedAt: Instant? = null,

    // Real card PIN (2026-08-27 Toss reference: "카드 비밀번호 변경") -- a real,
    // itunda-issued 4-digit debit-card PIN, deliberately separate from the 6-digit
    // login PIN (`User.passwordHash`, see AuthService.setPin's own doc comment):
    // real debit cards worldwide use a 4-digit PIN distinct from any app-login
    // credential. Hashed with the same BCryptPasswordEncoder convention
    // AuthService already uses for the login PIN -- never stored or compared in
    // plaintext.
    @Column(name = "pin_hash")
    var pinHash: String? = null,

    // Real "카드 재발급" (reissue) audit marker -- see CardService.reissue's own
    // doc comment for why reissuing regenerates `last4`/clears `lost`/`closedAt`/
    // `pinHash` in place rather than creating a second DebitCard row (userId is
    // uniquely constrained, matching one real card per user at a time).
    @Column(name = "reissued_at")
    var reissuedAt: Instant? = null,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant = Instant.now(),

    @Column(nullable = false, length = 32)
    val design: String = DebitCardDesign.DEFAULT,

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
 * The 5 real card colorways from itunda's own card-design pass (each a real front/back
 * color pair: Onyx Indigo, Indigo Onyx, Rose Forest, Frost Onyx, Forest Rose). A plain
 * whitelist object rather than a JPA `@Enumerated` type -- `design` is stored as a raw
 * string column so a client can never desync from a strict enum ordinal, matching the
 * same reasoning `LedgerAccountType` string-keyed seeding already established.
 */
object DebitCardDesign {
    const val ONYX_INDIGO = "onyx_indigo"
    const val INDIGO_ONYX = "indigo_onyx"
    const val ROSE_FOREST = "rose_forest"
    const val FROST_ONYX = "frost_onyx"
    const val FOREST_ROSE = "forest_rose"
    const val DEFAULT = ONYX_INDIGO

    val ALL: Set<String> = setOf(ONYX_INDIGO, INDIGO_ONYX, ROSE_FOREST, FROST_ONYX, FOREST_ROSE)
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

    // Real Toss "결제 계좌" (payment account) reference (2026-09-12) -- which of the
    // user's own accounts actually funded this charge. See CardService.chargeWithCard's
    // own doc comment: scoped to MAIN/PAY only (both always RWF), never
    // FOREIGN_CURRENCY -- a foreign-currency-funded charge would need its own
    // per-currency card_spend_expense clearing account (same pattern
    // ForeignCurrencyAccountService's fx_clearing_${currency} already establishes),
    // which this pass deliberately doesn't build. Defaults to MAIN so every
    // pre-existing row (and any client that hasn't been updated yet) keeps its real,
    // unchanged meaning.
    @Enumerated(EnumType.STRING)
    @Column(name = "funding_account_type", nullable = false, length = 32)
    val fundingAccountType: AccountType = AccountType.MAIN,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", cardId = "", userId = "", amount = BigDecimal.ZERO, merchantName = "", ledgerTransactionId = "",
    )
}
