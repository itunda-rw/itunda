package rw.itunda.wallet

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real background automation for Naver Pay Money 자동충전 (auto-charge) -- see
 * AutoTopUpService's own doc comment. Closes that feature's own previously-named gap:
 * `evaluateAndTopUp` was real but only ever manually/demo-triggered; this sweep is what
 * makes it genuinely automatic, matching the real Naver Pay feature it's modeled on.
 * Same shape as every other recurring feature's own scheduler in this codebase
 * (`AutoTransferScheduler`, `MerchantBillingScheduler`): the *business* condition is
 * real (a wallet's own balance actually falling below its own real threshold), the
 * *poll* interval below is demo-speed so a real low-balance moment doesn't require the
 * process to stay up indefinitely to observe it working end to end. Each setting is
 * evaluated independently and resiliently -- one bad row (a since-unlinked account, a
 * genuine provider decline) never blocks the sweep for every other enabled wallet,
 * same discipline `evaluateAndTopUp` itself already establishes for a single check.
 */
@Component
class AutoTopUpScheduler(private val autoTopUpService: AutoTopUpService) {
    private val log = LoggerFactory.getLogger(AutoTopUpScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val settings = autoTopUpService.getEnabledSettings()
        for (setting in settings) {
            try {
                val result = autoTopUpService.evaluateAndTopUp(setting.userId, setting.walletId)
                if (result.triggered) {
                    log.info("Auto top-up triggered for wallet {} ({})", setting.walletId, result.reason)
                }
            } catch (e: Exception) {
                log.error("Auto top-up sweep failed for wallet {}", setting.walletId, e)
            }
        }
    }
}
