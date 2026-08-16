package rw.itunda.wallet

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Toss 외환 환율 알림 (exchange rate alert) scheduler (2026-08-17) -- see
 * ForeignCurrencyWalletService.getDueRateAlerts/triggerRateAlert's own doc comments.
 * Same real "loop lives in a separate, non-@Transactional bean, calls the real
 * @Transactional method on a DIFFERENT bean" structure StockPriceAlertScheduler/
 * BillAutoPayProcessor already establish -- each `triggerRateAlert` call gets its own
 * independent physical transaction via a genuine cross-bean proxied call, so one bad
 * row (or one currently-unreachable rate) can never poison the sweep for every other
 * real due alert. The poll interval below is demo-speed on purpose, matching every
 * other scheduler in this codebase -- the real upstream rate itself only refreshes
 * ~hourly (ForeignCurrencyRateClient's own cache TTL), but polling more often than that
 * is cheap and correct, not wasteful, since it's a cheap in-memory-cache read after the
 * first fetch.
 */
@Component
class ExchangeRateAlertScheduler(private val foreignCurrencyWalletService: ForeignCurrencyWalletService) {
    private val log = LoggerFactory.getLogger(ExchangeRateAlertScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = foreignCurrencyWalletService.getDueRateAlerts()
        for (alert in due) {
            try {
                foreignCurrencyWalletService.triggerRateAlert(alert.id)
                log.info("Triggered exchange rate alert for user {} on {}->{}", alert.userId, alert.fromCurrency, alert.toCurrency)
            } catch (e: Exception) {
                log.error("Exchange rate alert trigger failed for alert {}", alert.id, e)
            }
        }
    }
}
