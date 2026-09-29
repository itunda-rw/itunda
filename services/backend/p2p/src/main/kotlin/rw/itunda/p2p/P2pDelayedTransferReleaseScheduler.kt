package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled auto-release for a 지연이체 (delayed transfer) once its real delay
 * window elapses -- see `P2pDelayedTransfer.DELAY_WINDOW`'s own doc comment for the
 * real sourced window. Same "poll for due rows, act per row, done" shape
 * `MarketplaceEscrowAutoReleaseScheduler` already establishes: the *business* window is
 * real (3 hours); the poll interval below is demo-speed on purpose, matching every
 * other scheduler in this codebase.
 *
 * Deliberately the established safe shape, not a batch-transactional loop: [run] itself
 * carries no `@Transactional` of its own -- [P2pDelayedTransferService.getDueForRelease]
 * is a plain read, and each row's own real state change happens inside
 * [P2pDelayedTransferService.release]'s own per-item `@Transactional` boundary, with a
 * try/catch per row right here so one bad row (e.g. a since-deleted recipient account)
 * can never poison every other real due transfer in the same poll -- the exact
 * previously-recurring "scheduler transaction-poisoning" bug class this codebase's own
 * Sections 115-181 already found and fixed nine times.
 */
@Component
class P2pDelayedTransferReleaseScheduler(private val p2pDelayedTransferService: P2pDelayedTransferService) {
    private val log = LoggerFactory.getLogger(P2pDelayedTransferReleaseScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = p2pDelayedTransferService.getDueForRelease()
        for (transfer in due) {
            try {
                p2pDelayedTransferService.release(transfer.id)
                log.info("Released delayed P2P transfer {} after its real delay window elapsed", transfer.id)
            } catch (e: Exception) {
                log.error("Delayed P2P transfer release failed for {}", transfer.id, e)
            }
        }
    }
}
