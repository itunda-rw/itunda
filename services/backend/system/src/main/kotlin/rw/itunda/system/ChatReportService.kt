package rw.itunda.system

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.ChatReport
import rw.itunda.core.domain.HoodReportStatus
import rw.itunda.core.repository.ChatReportRepository
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageRepository
import java.util.UUID

class ChatReportMessageNotFoundException(message: String) : RuntimeException(message)
class ChatReportForbiddenException(message: String) : RuntimeException(message)
class ChatReportAlreadyOpenException(message: String) : RuntimeException(message)

@Service
class ChatReportService(
    private val chatReportRepository: ChatReportRepository,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
) {
    @Transactional
    fun report(reporterUserId: String, messageId: String, reason: String): ChatReport {
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
}
