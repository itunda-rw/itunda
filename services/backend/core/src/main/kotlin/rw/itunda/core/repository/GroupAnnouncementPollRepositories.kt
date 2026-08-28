package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import rw.itunda.core.domain.GroupAnnouncement
import rw.itunda.core.domain.GroupPoll
import rw.itunda.core.domain.GroupPollOption
import rw.itunda.core.domain.GroupPollVote

interface GroupAnnouncementRepository : JpaRepository<GroupAnnouncement, String> {
    fun findFirstByGroupConversationIdOrderByCreatedAtDesc(groupConversationId: String): GroupAnnouncement?
}

interface GroupPollRepository : JpaRepository<GroupPoll, String> {
    fun findByGroupConversationIdOrderByCreatedAtDesc(groupConversationId: String): List<GroupPoll>
}

interface GroupPollOptionRepository : JpaRepository<GroupPollOption, String> {
    fun findByPollId(pollId: String): List<GroupPollOption>
    fun findByPollIdIn(pollIds: List<String>): List<GroupPollOption>
}

interface GroupPollVoteRepository : JpaRepository<GroupPollVote, String> {
    fun findByPollId(pollId: String): List<GroupPollVote>
    fun findByPollIdIn(pollIds: List<String>): List<GroupPollVote>
    fun findByPollIdAndUserId(pollId: String, userId: String): List<GroupPollVote>

    @Modifying
    fun deleteByPollIdAndUserId(pollId: String, userId: String)
}
