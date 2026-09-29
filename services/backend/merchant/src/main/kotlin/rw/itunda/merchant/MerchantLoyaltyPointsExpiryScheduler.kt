package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled expiry sweep for a `MerchantLoyaltyAccount` gone dormant past
 * [MerchantLoyaltyPointsService.EXPIRY_WINDOW] -- see that class's own doc comment for
 * the full honest account of why this window is itunda's own self-declared choice, not
 * a sourced Toss Place number. Same "poll for due rows read-only, resolve each real row
 * inside its own per-item @Transactional method, one bad row never blocks the sweep for
 * every other real due row" shape [rw.itunda.p2p.P2pDelayedTransferService]'s own
 * release scheduler already establishes -- deliberately never a single
 * batch-@Transactional loop over every due row.
 */
@Component
class MerchantLoyaltyPointsExpiryScheduler(private val merchantLoyaltyPointsService: MerchantLoyaltyPointsService) {
    private val log = LoggerFactory.getLogger(MerchantLoyaltyPointsExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = merchantLoyaltyPointsService.getExpirableAccounts()
        for (account in due) {
            try {
                merchantLoyaltyPointsService.expireIfDue(account.id)
                log.info("Expired dormant loyalty balance for merchant loyalty account {}", account.id)
            } catch (e: Exception) {
                log.error("Merchant loyalty points expiry failed for account {}", account.id, e)
            }
        }
    }
}
