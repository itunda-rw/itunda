package rw.itunda.stocks

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Toss Securities 목표가 알림 (target price alert) scheduler (2026-08-16) -- see
 * StocksService.getDuePriceAlerts/triggerPriceAlert's own doc comments. Same real
 * "poll for due rows, act, done" shape as ProductPriceDropScheduler -- the poll
 * interval below is demo-speed on purpose, matching every other scheduler in this
 * codebase. Resilient per-alert: one bad row never blocks the sweep for every other
 * real due alert.
 */
@Component
class StockPriceAlertScheduler(private val stocksService: StocksService) {
    private val log = LoggerFactory.getLogger(StockPriceAlertScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = stocksService.getDuePriceAlerts()
        for (watchlist in due) {
            try {
                stocksService.triggerPriceAlert(watchlist.id)
                log.info("Triggered price alert for user {} on stock {}", watchlist.userId, watchlist.stockId)
            } catch (e: Exception) {
                log.error("Price alert trigger failed for watchlist {}", watchlist.id, e)
            }
        }
    }
}
