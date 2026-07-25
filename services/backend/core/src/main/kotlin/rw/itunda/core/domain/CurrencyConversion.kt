package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real conversion record between a user's own MAIN (RWF) wallet and one of their
 * FOREIGN_CURRENCY wallets -- see `ForeignCurrencyWalletService.convert`'s own doc
 * comment for the real two-ledger-transaction mechanics this snapshots. `rate` is the
 * real live mid-market rate at conversion time (before itunda's own margin);
 * `marginAmount` is itunda's real spread, denominated in `toCurrency`.
 */
@Entity
@Table(name = "currency_conversions")
class CurrencyConversion(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "from_currency", nullable = false, length = 8)
    val fromCurrency: String,

    @Column(name = "to_currency", nullable = false, length = 8)
    val toCurrency: String,

    @Column(name = "from_amount", nullable = false, precision = 18, scale = 2)
    val fromAmount: BigDecimal,

    @Column(name = "to_amount", nullable = false, precision = 18, scale = 2)
    val toAmount: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 6)
    val rate: BigDecimal,

    @Column(name = "margin_amount", nullable = false, precision = 18, scale = 2)
    val marginAmount: BigDecimal,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", userId = "", fromCurrency = "", toCurrency = "", fromAmount = BigDecimal.ZERO,
        toAmount = BigDecimal.ZERO, rate = BigDecimal.ZERO, marginAmount = BigDecimal.ZERO, transactionId = "",
    )
}
