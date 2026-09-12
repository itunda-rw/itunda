package rw.itunda.overview

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import rw.itunda.core.repository.UserRepository

/**
 * Real Toss "자산 변화" (asset change over time) reference -- see NetWorthSnapshot's
 * own doc comment for the full scope rationale. Once daily (03:30 Africa/Kigali,
 * the same real low-traffic window AiSummaryScheduler's own doc comment already
 * establishes, just offset 30 minutes so both don't compete for the same instant)
 * a snapshot is captured for every real user -- a plain DB read + write per user,
 * cheap enough not to need AiSummaryScheduler's own "small bounded batch" limit.
 *
 * `getUserIdsWithSnapshotToday` guards against a double-run on the same calendar day
 * (a manual re-trigger, or the job firing twice across a deploy) leaving two rows for
 * one day -- `getNetWorthHistory` would still only read the LAST one per month, but
 * skipping the redundant write outright keeps the table honest (one row per user
 * per day, not "however many times this happened to run"). Checked as one batched
 * query up front (2026-09-12 N+1 fix), not per-user inside the loop below -- see
 * OverviewService.getUserIdsWithSnapshotToday's own doc comment.
 */
@Component
class NetWorthSnapshotScheduler(
    private val userRepository: UserRepository,
    private val overviewService: OverviewService,
) {
    private val log = LoggerFactory.getLogger(NetWorthSnapshotScheduler::class.java)

    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Kigali")
    fun run() {
        captureAll()
    }

    fun captureAll(): Int {
        var captured = 0
        val alreadyCapturedToday = overviewService.getUserIdsWithSnapshotToday()
        for (user in userRepository.findAll()) {
            if (user.id in alreadyCapturedToday) continue
            try {
                overviewService.captureSnapshot(user.id)
                captured++
            } catch (e: Exception) {
                log.error("Net worth snapshot failed for user {}", user.id, e)
            }
        }
        if (captured > 0) log.info("Captured {} net worth snapshots", captured)
        return captured
    }
}
