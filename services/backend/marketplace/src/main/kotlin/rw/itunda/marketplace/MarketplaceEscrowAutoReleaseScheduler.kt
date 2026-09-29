package rw.itunda.marketplace

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled auto-release for a 당근마켓/Naver Cafe 안심결제-style escrow the buyer
 * never explicitly confirmed -- see `MarketplaceEscrow.AUTO_RELEASE_TIMEOUT`'s own doc
 * comment for the real sourced window. Closes this feature's own previously-named
 * deferred follow-up (see `MarketplaceEscrow`'s own doc comment history). Same
 * "poll for due rows, act, done" shape as `GiftExpiryScheduler` -- the *business*
 * window is real (7 days); the poll interval below is demo-speed on purpose, matching
 * every other scheduler in this codebase. Resilient per-escrow: one bad row (e.g. a
 * since-deleted listing) never blocks the sweep for every other real due escrow.
 */
@Component
class MarketplaceEscrowAutoReleaseScheduler(private val marketplaceService: MarketplaceService) {
    private val log = LoggerFactory.getLogger(MarketplaceEscrowAutoReleaseScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = marketplaceService.getEscrowsDueForAutoRelease()
        for (escrow in due) {
            try {
                marketplaceService.autoReleaseEscrow(escrow.id)
                log.info("Auto-released marketplace escrow {} after timeout", escrow.id)
            } catch (e: Exception) {
                log.error("Marketplace escrow auto-release failed for {}", escrow.id, e)
            }
        }
    }
}
