package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real, deliberately low-frequency batch trigger for AiSummaryService -- once daily
 * (03:00 Africa/Kigali, a real low-traffic window, same "don't compete with real user
 * load" reasoning this session's own private-cloud-overload incident makes concrete),
 * a small bounded batch at a time (AiSummaryService's own default limit), never a
 * tight poll loop the way this codebase's other `@Scheduled` reminders use -- an LLM
 * completion call is genuinely expensive compared to those, unlike a plain DB query.
 * A no-op (zero real API calls) whenever AiSummaryClient isn't configured -- see that
 * class's own doc comment.
 */
@Component
class AiSummaryScheduler(private val aiSummaryService: AiSummaryService) {
    private val log = LoggerFactory.getLogger(AiSummaryScheduler::class.java)

    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Kigali")
    fun run() {
        val generated = aiSummaryService.generateMissing()
        if (generated > 0) log.info("Real AI summary batch generated {} merchant summaries", generated)
    }
}
