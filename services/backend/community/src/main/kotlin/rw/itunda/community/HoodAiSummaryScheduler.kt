package rw.itunda.community

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real, deliberately low-frequency batch trigger for HoodAiSummaryService -- see
 * eats.AiSummaryScheduler's own doc comment for the full "why once-daily, why a
 * bounded batch" account this mirrors exactly. Deliberately offset 15 minutes from
 * that scheduler's own 03:00 run so the two real LLM-completion batches don't both
 * land on the same VM at the same moment.
 */
@Component
class HoodAiSummaryScheduler(private val hoodAiSummaryService: HoodAiSummaryService) {
    private val log = LoggerFactory.getLogger(HoodAiSummaryScheduler::class.java)

    @Scheduled(cron = "0 15 3 * * *", zone = "Africa/Kigali")
    fun run() {
        val generated = hoodAiSummaryService.generateMissing()
        if (generated > 0) log.info("Real AI summary batch generated {} meetup summaries", generated)
    }
}
