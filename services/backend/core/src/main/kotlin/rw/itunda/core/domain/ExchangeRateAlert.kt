package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real Toss 외환 환율 알림 (exchange rate alert) (2026-08-17) -- set a target rate on
 * a real currency pair (RWF vs. one of `ForeignCurrencyWalletService.SUPPORTED_CURRENCIES`)
 * and get notified once `ForeignCurrencyRateClient`'s real live mid-market rate
 * (open.er-api.com, ECB-sourced) crosses it. Same real "ABOVE"/"BELOW" one-shot alert
 * shape `StockWatchlist.targetPrice`'s own doc comment already establishes for Toss
 * Securities' 목표가 알림 -- explicit direction (never inferred from the rate at
 * set-time), `alertTriggeredAt` marks it fired so the scheduler never re-notifies for
 * the same crossing, re-armed by setting a new target. Deliberately its own entity
 * rather than reusing `StockWatchlist` -- there's no "watch a currency pair without an
 * alert" concept in the real product the way there is for stocks, so no separate
 * watchlist-membership row is needed. Real DB-unique `(user_id, from_currency,
 * to_currency)` backs the same "one active alert per pair, setting again replaces it"
 * application-level check `ForeignCurrencyWalletService.setRateAlert` makes.
 */
@Entity
@Table(name = "exchange_rate_alerts")
class ExchangeRateAlert(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "from_currency", nullable = false, length = 8)
    val fromCurrency: String,

    @Column(name = "to_currency", nullable = false, length = 8)
    val toCurrency: String,

    @Column(name = "target_rate", nullable = false)
    var targetRate: Double,

    @Column(name = "direction", nullable = false, length = 8)
    var direction: String,

    @Column(name = "alert_triggered_at")
    var alertTriggeredAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", fromCurrency = "", toCurrency = "", targetRate = 0.0, direction = "")
}
