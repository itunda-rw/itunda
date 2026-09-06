package rw.itunda.system

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.ChatReport
import rw.itunda.core.domain.HoodReportStatus
import rw.itunda.core.repository.ChatReportRepository
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ChatReportMessageNotFoundException(message: String) : RuntimeException(message)
class ChatReportForbiddenException(message: String) : RuntimeException(message)
class ChatReportAlreadyOpenException(message: String) : RuntimeException(message)
class ChatReportNotFoundException(message: String) : RuntimeException(message)

@Service
class ChatReportService(
    private val chatReportRepository: ChatReportRepository,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun report(reporterUserId: String, messageId: String, reason: String): ChatReport {
        // Real bug found live (2026-08-02): this real content-creation (report-filing)
        // endpoint had shipped with zero rate limiting -- every other real
        // content/report-creation endpoint in this codebase (ScamReportService,
        // ContactsController, MessagingService, IdentityService.submit, etc.) already
        // has one. An authenticated caller could otherwise spam unlimited real
        // ChatReport rows against any message they can see.
        rateLimiter.checkLimit("chat-report:$reporterUserId", limit = 20, window = Duration.ofHours(1))
        val message = messageRepository.findById(messageId).orElseThrow { ChatReportMessageNotFoundException("Message not found") }
        val conversation = conversationRepository.findById(message.conversationId).orElseThrow { ChatReportMessageNotFoundException("Message not found") }
        if (reporterUserId != conversation.participantAId && reporterUserId != conversation.participantBId) {
            throw ChatReportForbiddenException("Message not found")
        }
        val trimmedReason = reason.trim()
        require(trimmedReason.length in 3..180) { "Give a short reason between 3 and 180 characters" }
        if (chatReportRepository.findByReporterUserIdAndMessageIdAndStatus(reporterUserId, messageId, HoodReportStatus.OPEN) != null) {
            throw ChatReportAlreadyOpenException("You already reported this message")
        }
        return chatReportRepository.save(ChatReport("chat_report_${UUID.randomUUID()}", reporterUserId, messageId, trimmedReason))
    }

    fun queue(pageable: Pageable): Page<ChatReport> = chatReportRepository.findByStatusOrderByCreatedAtAsc(HoodReportStatus.OPEN, pageable)

    @Transactional
    fun resolve(id: String, reviewerId: String): ChatReport {
        val report = chatReportRepository.findById(id).orElseThrow { ChatReportNotFoundException("Report not found") }
        report.status = HoodReportStatus.RESOLVED
        report.reviewedBy = reviewerId
        report.reviewedAt = Instant.now()
        return chatReportRepository.save(report)
    }

    /** Same real soft-delete (deletedAt/deletedByUserId) MessagingService.deleteMessage
     * uses when the sender deletes their own message -- a moderator removal goes through
     * the identical path, just attributed to the reviewer instead of the sender. */
    @Transactional
    fun removeMessage(id: String, reviewerId: String): ChatReport {
        val report = chatReportRepository.findById(id).orElseThrow { ChatReportNotFoundException("Report not found") }
        val message = messageRepository.findById(report.messageId).orElseThrow { ChatReportMessageNotFoundException("Message not found") }
        if (message.deletedAt == null) {
            message.deletedAt = Instant.now()
            message.deletedByUserId = reviewerId
            messageRepository.save(message)
        }
        report.status = HoodReportStatus.RESOLVED
        report.reviewedBy = reviewerId
        report.reviewedAt = Instant.now()
        return chatReportRepository.save(report)
    }
}
