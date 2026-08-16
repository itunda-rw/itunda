package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ExchangeRateAlert

interface ExchangeRateAlertRepository : JpaRepository<ExchangeRateAlert, String> {
    fun findByUserIdAndFromCurrencyAndToCurrency(userId: String, fromCurrency: String, toCurrency: String): ExchangeRateAlert?

    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<ExchangeRateAlert>

    // Real due-alert candidates backing ExchangeRateAlertScheduler -- a real,
    // not-yet-fired alert. Matches StockWatchlistRepository
    // .findByTargetPriceIsNotNullAndAlertTriggeredAtIsNull's own shape.
    fun findByAlertTriggeredAtIsNull(): List<ExchangeRateAlert>
}
