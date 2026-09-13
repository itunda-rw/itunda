package rw.itunda.messaging

import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.GroupAnnouncement
import rw.itunda.core.domain.GroupPoll
import rw.itunda.core.domain.GroupPollOption
import rw.itunda.core.domain.GroupPollVote
import rw.itunda.core.repository.GroupAnnouncementRepository
import rw.itunda.core.repository.GroupPollOptionRepository
import rw.itunda.core.repository.GroupPollRepository
import rw.itunda.core.repository.GroupPollVoteRepository

class InvalidGroupAnnouncementException(message: String) : RuntimeException(message)
class InvalidGroupPollException(message: String) : RuntimeException(message)
class GroupPollNotFoundException(message: String) : RuntimeException(message)
class GroupPollOptionNotFoundException(message: String) : RuntimeException(message)
class GroupPollClosedException(message: String) : RuntimeException(message)

data class GroupPollWithResults(val poll: GroupPoll, val options: List<GroupPollOption>, val voteCountByOptionId: Map<String, Int>, val myVoteOptionIds: Set<String>)

/**
 * Real group 공지/투표 (announcement/poll) (itunda Talk redesign, 2026-08-28) -- see
 * `GroupAnnouncement`/`GroupPoll`'s own doc comments. Creation is open to "any real
 * member," matching every other current group action -- a deliberate, explicit choice,
 * not an oversight: this codebase's own `GroupMessagingService` doc comment already
 * names a real admin/owner role as a separate, still-open follow-up, and this feature
 * does not invent one as a side effect. Membership is verified via
 * `GroupMessagingService.getGroupForMember`, the same real public IDOR-check wrapper
 * `SplitBillService` already reuses.
 */
@Service
class GroupPollAnnouncementService(
    private val groupMessagingService: GroupMessagingService,
    private val groupAnnouncementRepository: GroupAnnouncementRepository,
    private val groupPollRepository: GroupPollRepository,
    private val groupPollOptionRepository: GroupPollOptionRepository,
    private val groupPollVoteRepository: GroupPollVoteRepository,
) {
    companion object {
        private const val MAX_BODY_LENGTH = 1000
        private const val MAX_QUESTION_LENGTH = 500
        private const val MIN_OPTIONS = 2
        private const val MAX_OPTIONS = 10
    }

    @Transactional
    fun postAnnouncement(userId: String, groupId: String, body: String): GroupAnnouncement {
        groupMessagingService.getGroupForMember(userId, groupId)
        val trimmed = body.trim()
        if (trimmed.isEmpty()) throw InvalidGroupAnnouncementException("Announcement body is required")
        if (trimmed.length > MAX_BODY_LENGTH) throw InvalidGroupAnnouncementException("Announcement must be $MAX_BODY_LENGTH characters or fewer")
        return groupAnnouncementRepository.save(
            GroupAnnouncement(id = "group_announcement_${UUID.randomUUID()}", groupConversationId = groupId, createdBy = userId, body = trimmed),
        )
    }

    fun getAnnouncement(userId: String, groupId: String): GroupAnnouncement? {
        groupMessagingService.getGroupForMember(userId, groupId)
        return groupAnnouncementRepository.findFirstByGroupConversationIdOrderByCreatedAtDesc(groupId)
    }

    @Transactional
    fun createPoll(userId: String, groupId: String, question: String, options: List<String>, allowMultiple: Boolean, closesAt: Instant?): GroupPollWithResults {
        groupMessagingService.getGroupForMember(userId, groupId)
        val trimmedQuestion = question.trim()
        if (trimmedQuestion.isEmpty()) throw InvalidGroupPollException("A poll question is required")
        if (trimmedQuestion.length > MAX_QUESTION_LENGTH) throw InvalidGroupPollException("Poll question must be $MAX_QUESTION_LENGTH characters or fewer")
        val trimmedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmedOptions.size < MIN_OPTIONS) throw InvalidGroupPollException("A poll needs at least $MIN_OPTIONS real options")
        if (trimmedOptions.size > MAX_OPTIONS) throw InvalidGroupPollException("A poll can have at most $MAX_OPTIONS options")

        val poll = groupPollRepository.save(
            GroupPoll(id = "group_poll_${UUID.randomUUID()}", groupConversationId = groupId, createdBy = userId, question = trimmedQuestion, allowMultiple = allowMultiple, closesAt = closesAt),
        )
        val savedOptions = trimmedOptions.map { text -> groupPollOptionRepository.save(GroupPollOption(id = "group_poll_option_${UUID.randomUUID()}", pollId = poll.id, text = text)) }
        return GroupPollWithResults(poll, savedOptions, emptyMap(), emptySet())
    }

    fun getPolls(userId: String, groupId: String): List<GroupPollWithResults> {
        groupMessagingService.getGroupForMember(userId, groupId)
        val polls = groupPollRepository.findByGroupConversationIdOrderByCreatedAtDesc(groupId)
        if (polls.isEmpty()) return emptyList()
        val pollIds = polls.map { it.id }
        val optionsByPollId = groupPollOptionRepository.findByPollIdIn(pollIds).groupBy { it.pollId }
        val votes = groupPollVoteRepository.findByPollIdIn(pollIds)
        val voteCountsByPollId = votes.groupBy { it.pollId }.mapValues { (_, v) -> v.groupingBy { it.optionId }.eachCount() }
        val myVotesByPollId = votes.filter { it.userId == userId }.groupBy { it.pollId }.mapValues { (_, v) -> v.map { it.optionId }.toSet() }
        return polls.map { poll ->
            GroupPollWithResults(
                poll = poll,
                options = optionsByPollId[poll.id] ?: emptyList(),
                voteCountByOptionId = voteCountsByPollId[poll.id] ?: emptyMap(),
                myVoteOptionIds = myVotesByPollId[poll.id] ?: emptySet(),
            )
        }
    }

    // Real single-choice "replace" semantics -- see GroupPollVote's own doc comment on
    // why this is a delete-then-insert transaction rather than a second, conflicting
    // unique-constraint shape.
    @Transactional
    fun vote(userId: String, groupId: String, pollId: String, optionId: String): GroupPollWithResults {
        groupMessagingService.getGroupForMember(userId, groupId)
        val poll = groupPollRepository.findById(pollId).orElseThrow { GroupPollNotFoundException("Poll not found") }
        if (poll.groupConversationId != groupId) throw GroupPollNotFoundException("Poll not found")
        val option = groupPollOptionRepository.findById(optionId).orElseThrow { GroupPollOptionNotFoundException("Option not found") }
        if (option.pollId != pollId) throw GroupPollOptionNotFoundException("Option not found")
        // Real gap found live (2026-09-13): closesAt is a real, persisted, API-accepted
        // field (createPoll/CreateGroupPollRequest) but was never actually enforced --
        // a poll could be voted on forever regardless of its own stated closing time.
        // No real UI sets closesAt yet (a real, disclosed follow-up -- see
        // project_itunda_group_poll_multiselect_unreached memory), but the backend
        // must be correct for anyone calling the real API directly regardless.
        val closesAt = poll.closesAt
        if (closesAt != null && Instant.now().isAfter(closesAt)) {
            throw GroupPollClosedException("This poll has already closed")
        }

        if (poll.allowMultiple) {
            // Real bug found live (2026-09-13): this branch was completely unreached by
            // any real UI (allowMultiple is persisted and voted on, but never set to
            // true anywhere on web/Android/iOS), so a real tap-the-same-option-twice
            // double-vote was never caught -- it would have hit GroupPollVote's own
            // real DB unique constraint (poll_id, option_id, user_id) as an unhandled
            // 500, not a clean no-op. Real toggle semantics: voting for an option
            // you've already voted for removes that one vote; a genuinely new option
            // is added alongside your other votes, never replacing them.
            if (groupPollVoteRepository.existsByPollIdAndOptionIdAndUserId(pollId, optionId, userId)) {
                groupPollVoteRepository.deleteByPollIdAndOptionIdAndUserId(pollId, optionId, userId)
                return getPolls(userId, groupId).first { it.poll.id == pollId }
            }
        } else {
            groupPollVoteRepository.deleteByPollIdAndUserId(pollId, userId)
        }
        groupPollVoteRepository.save(GroupPollVote(id = "group_poll_vote_${UUID.randomUUID()}", pollId = pollId, optionId = optionId, userId = userId))
        return getPolls(userId, groupId).first { it.poll.id == pollId }
    }
}
