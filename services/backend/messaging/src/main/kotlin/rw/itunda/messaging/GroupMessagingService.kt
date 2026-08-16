package rw.itunda.messaging

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupConversationMember
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.GroupMessageReaction
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.ReactionGroup
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.GroupConversationMemberRepository
import rw.itunda.core.repository.GroupConversationRepository
import rw.itunda.core.repository.GroupMessageReactionRepository
import rw.itunda.core.repository.GroupMessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

class GroupNotFoundException(message: String) : RuntimeException(message)
class GroupNameRequiredException(message: String) : RuntimeException(message)
class GroupNeedsMoreMembersException(message: String) : RuntimeException(message)
class GroupMemberNotFoundException(message: String) : RuntimeException(message)
class AlreadyGroupMemberException(message: String) : RuntimeException(message)
class EmptyGroupMessageException(message: String) : RuntimeException(message)
class GroupMessageTooLongException(message: String) : RuntimeException(message)
class GroupNameTooLongException(message: String) : RuntimeException(message)
class GroupPhotoUrlTooLongException(message: String) : RuntimeException(message)
class GroupDescriptionTooLongException(message: String) : RuntimeException(message)
class GroupMessageNotFoundException(message: String) : RuntimeException(message)
class GroupMessageDeleteForbiddenException(message: String) : RuntimeException(message)
class InvalidGroupReactionException(message: String) : RuntimeException(message)
class InvalidGroupMessageImageException(message: String) : RuntimeException(message)
class InvalidGroupJoinCodeException(message: String) : RuntimeException(message)

data class GroupSummary(
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val lastMessageAt: Instant,
    val lastMessagePreview: String?,
    val unreadCount: Long,
)

data class GroupMemberInfo(val userId: String, val name: String)

/**
 * Real group chat -- the single most defining KakaoTalk capability the original
 * 1:1-only messaging pair didn't cover, built at the user's direct request ("Talk
 * should be 100% like KakaoTalk + 당근 채팅 for Rwanda"). See `GroupConversation.kt`'s
 * own doc comment for why this is a brand-new, additive entity set rather than a
 * widened `Conversation`.
 *
 * Real membership (`GroupConversationMember`), real per-member read cursors
 * (`lastReadAt`, which also now drives a real Kakao-style per-message read-receipt
 * countdown -- see `getUnreadCounts`'s own doc comment), real notifications on every new
 * message to every other real member, and a real live push over the same
 * `RealtimeMessagePublisher` 1:1 messaging just added, fanned out to every member
 * instead of a single recipient. Reuses the exact same `RateLimiter` per-sender
 * convention 1:1 `MessagingService.sendMessage` already established.
 *
 * Honestly scoped v1: a group's membership is flat -- no admin/owner role beyond
 * `createdBy` being recorded, no kick/promote -- real, deliberately not attempted in
 * this pass since the defining gap was "can more than two people chat at once at all,"
 * not group-management tooling; this remains the one still-open follow-up. **Group
 * photo/description closed 2026-07-28** -- see `setGroupPhotoUrl`/`setGroupDescription`'s
 * own doc comments, open to any real member (same flat-membership discipline
 * `setPinnedMessage` already established, not gated to `createdBy` since this codebase
 * doesn't have a real admin/owner concept yet).
 */
@Service
class GroupMessagingService(
    private val groupConversationRepository: GroupConversationRepository,
    private val groupConversationMemberRepository: GroupConversationMemberRepository,
    private val groupMessageRepository: GroupMessageRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val groupMessageReactionRepository: GroupMessageReactionRepository,
    private val rateLimiter: RateLimiter,
    private val realtimeMessagePublisher: RealtimeMessagePublisher,
    private val pushNotificationService: PushNotificationService,
) {
    private val secureRandom = SecureRandom()
    // Excludes visually ambiguous characters (0/O, 1/I) -- a real, spoken/typed-aloud
    // share code, same alphabet GroupEatsOrderService.joinCodeAlphabet already uses for
    // an identical real invite-code convention.
    private val joinCodeAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    @Transactional
    fun createGroup(creatorUserId: String, name: String, memberUserIds: List<String>): GroupConversation {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw GroupNameRequiredException("A group needs a name")
        }
        // Real bound, matching `deliveryAddress`'s own fix on the Eats/Commerce rows
        // the same day -- `name` is VARCHAR(100), and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length
        // insert rather than truncating.
        if (trimmedName.length > 100) {
            throw GroupNameTooLongException("Group name must be 100 characters or fewer")
        }
        val distinctOtherMembers = memberUserIds.filter { it != creatorUserId }.distinct()
        if (distinctOtherMembers.isEmpty()) {
            throw GroupNeedsMoreMembersException("A group needs at least one other real member")
        }
        // Real bug found live (2026-08-02): group creation had shipped with zero rate
        // limiting -- every other real content-creation endpoint in this codebase
        // (MessagingService.sendMessage/toggleReaction, FamilyLinkService.inviteChild,
        // GiftService.sendGift, etc) already has one; an authenticated caller could
        // otherwise spam unlimited GroupConversation + member rows.
        rateLimiter.checkLimit("messaging:group-create:$creatorUserId", limit = 20, window = Duration.ofHours(1))
        // Real N+1 fix (2026-07-19 sweep): one batch findAllById instead of one
        // findById call per invited member, same convention as
        // WalletRepository.findByUserIdInAndType/UserRepository.findAllByPhoneNumberIn.
        val foundIds = userRepository.findAllById(distinctOtherMembers).map { it.id }.toSet()
        if (foundIds.size != distinctOtherMembers.size) {
            throw GroupMemberNotFoundException("No itunda account found for one of the invited members")
        }
        return createGroupInternal(creatorUserId, trimmedName, distinctOtherMembers)
    }

    /** Same as [createGroup], resolved by each real member's real phone number -- the
     * human-friendly entry point a real UI needs, same reasoning
     * `MessagingService.startOrGetConversationByPhoneNumber` already established for
     * 1:1 chat (a user only ever knows someone else's phone number, never their
     * internal id). */
    @Transactional
    fun createGroupByPhoneNumbers(creatorUserId: String, name: String, memberPhoneNumbers: List<String>): GroupConversation {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw GroupNameRequiredException("A group needs a name")
        }
        // Real bound, matching `deliveryAddress`'s own fix on the Eats/Commerce rows
        // the same day -- `name` is VARCHAR(100), and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length
        // insert rather than truncating.
        if (trimmedName.length > 100) {
            throw GroupNameTooLongException("Group name must be 100 characters or fewer")
        }
        // Real N+1 fix (2026-07-19 sweep): one batch findAllByPhoneNumberIn instead of
        // one findByPhoneNumber call per invited phone number, same convention as
        // createGroup's own fix just above.
        val trimmedPhones = memberPhoneNumbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val usersByPhone = userRepository.findAllByPhoneNumberIn(trimmedPhones).associateBy { it.phoneNumber }
        val distinctOtherMembers = trimmedPhones.map { phone ->
            usersByPhone[phone]?.id ?: throw GroupMemberNotFoundException("No itunda account found for phone number $phone")
        }.filter { it != creatorUserId }.distinct()
        if (distinctOtherMembers.isEmpty()) {
            throw GroupNeedsMoreMembersException("A group needs at least one other real member")
        }
        // Real bug found live (2026-08-02) -- see createGroup's own identical fix just
        // above; this is the other real entry point into the same unguarded creation path.
        rateLimiter.checkLimit("messaging:group-create:$creatorUserId", limit = 20, window = Duration.ofHours(1))
        return createGroupInternal(creatorUserId, trimmedName, distinctOtherMembers)
    }

    private fun createGroupInternal(creatorUserId: String, trimmedName: String, distinctOtherMembers: List<String>, isDirect: Boolean = false): GroupConversation {
        val group = groupConversationRepository.save(
            GroupConversation(id = "group_${UUID.randomUUID()}", name = trimmedName, createdBy = creatorUserId, isDirect = isDirect),
        )
        val now = Instant.now()
        val members = (distinctOtherMembers + creatorUserId).map { userId ->
            GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = group.id, userId = userId, joinedAt = now)
        }
        groupConversationMemberRepository.saveAll(members)
        return group
    }

    // Real, unpredictable, human-shareable 6-character code -- retried on the rare
    // collision, same defensive-uniqueness discipline
    // GroupEatsOrderService.generateUniqueJoinCode already establishes for an identical
    // real invite-code shape.
    private fun generateUniqueJoinCode(): String {
        repeat(20) {
            val code = (1..6).map { joinCodeAlphabet[secureRandom.nextInt(joinCodeAlphabet.length)] }.joinToString("")
            if (!groupConversationRepository.existsByJoinCode(code)) return code
        }
        throw IllegalStateException("Could not generate a unique join code")
    }

    /**
     * Real KakaoTalk 오픈채팅 (Open Chat)-style public room -- anyone with the real,
     * shareable [GroupConversation.joinCode] can join without the creator inviting them
     * by userId/phone number first, unlike every other group creation path in this
     * class. Sourced from KakaoTalk's own real join-by-link/search room type
     * (kakaocorp.com/page/service/service/KakaoTalk).
     *
     * Deliberately does NOT port Kakao's own real "Open Profile" pseudonymous-identity
     * layer (up to 3 per user, participate under a name distinct from your real
     * KakaoTalk identity) -- itunda's entire identity model is KYC-verified real names
     * tied to a real wallet, unlike Kakao's separate pseudonymous layer; porting that
     * honestly needs an explicit scoping decision about whether pseudonymous
     * participation belongs in a real-money app at all, not something to build
     * silently as a side effect of this feature. Every open-group member here is a
     * real, real-name itunda user, same as every other group. The 4,000-member cap and
     * search/recommendation indexing from Kakao's own real feature are also
     * deliberately left out of this first pass -- real scope-growers, not needed for
     * the core "join without an invite" mechanic this closes.
     */
    @Transactional
    fun createOpenGroup(creatorUserId: String, name: String): GroupConversation {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw GroupNameRequiredException("A group needs a name")
        }
        if (trimmedName.length > 100) {
            throw GroupNameTooLongException("Group name must be 100 characters or fewer")
        }
        rateLimiter.checkLimit("messaging:group-create:$creatorUserId", limit = 20, window = Duration.ofHours(1))
        val group = groupConversationRepository.save(
            GroupConversation(id = "group_${UUID.randomUUID()}", name = trimmedName, createdBy = creatorUserId, joinCode = generateUniqueJoinCode()),
        )
        groupConversationMemberRepository.save(
            GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = group.id, userId = creatorUserId, joinedAt = Instant.now()),
        )
        return group
    }

    /** Real join-by-code -- the other real half of [createOpenGroup]. Idempotent
     * re-join, same "already a member" quiet-success convention
     * `GroupEatsOrderService.join`'s own doc comment already establishes for an
     * identical real invite-code flow, rather than erroring on a member who taps the
     * same link twice. */
    @Transactional
    fun joinByCode(userId: String, joinCode: String): GroupConversation {
        val normalized = joinCode.trim().uppercase()
        if (normalized.isEmpty()) throw InvalidGroupJoinCodeException("A join code is required")
        val group = groupConversationRepository.findByJoinCode(normalized)
            ?: throw InvalidGroupJoinCodeException("No open group found for this code")
        if (groupConversationMemberRepository.findByGroupConversationIdAndUserId(group.id, userId) == null) {
            groupConversationMemberRepository.save(
                GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = group.id, userId = userId, joinedAt = Instant.now()),
            )
        }
        return group
    }

    /**
     * Real 1:1-chat split-bill support (2026-08-09, docs/DESIGN_REFERENCES.md Section
     * 19) -- resolves (or creates) a synthetic, hidden 2-person [GroupConversation]
     * between [userId] and [otherUserId] so `rw.itunda.splitbill.SplitBillService
     * .createSplitBill` can back a bill split between two people talking 1:1, reusing
     * 100% of its existing group logic unmodified rather than building a second,
     * parallel split-bill code path for pairs.
     *
     * This was investigated twice before and shelved both times over the same concrete
     * problem: a synthetic group created via the ordinary, public [createGroup] would
     * show up in both people's real "My Groups" list -- a confusing, generically-named
     * group neither of them asked to create. [GroupConversation.isDirect] (and
     * [GroupConversationRepository.findByMember]'s own exclusion of it) is what actually
     * closes that gap -- a real group row exists (SplitBillService needs one to attach
     * to), it's just never surfaced as one.
     *
     * Idempotent per pair: [GroupConversationRepository.findDirectGroupBetween] reuses
     * an existing direct group between the same two people rather than spawning a new
     * hidden group -- and therefore a second, disconnected settlement thread -- every
     * time they split another bill together.
     *
     * Real gap caught while re-reading this file's own rate-limiting discipline
     * (2026-08-09): [createGroup]/[createGroupByPhoneNumbers] both rate-limit real group
     * creation, but this method's first version didn't -- calling
     * `createSplitBill.createDirectSplitBill` with a different real `otherUserId` each
     * time would have created an unlimited number of hidden groups + member rows with no
     * throttle at all, unlike every other creation path in this file. Rate-limited on
     * the "creating a new one" branch only, not the "reusing an existing pair" branch,
     * so two people who've already split a bill together aren't throttled by their own
     * repeat, legitimate usage -- `createSplitBill`'s own `splitbill:create` limit
     * already covers that case.
     */
    @Transactional
    fun getOrCreateDirectSplitGroup(userId: String, otherUserId: String): GroupConversation {
        if (userId == otherUserId) {
            throw GroupNeedsMoreMembersException("A group needs at least one other real member")
        }
        groupConversationRepository.findDirectGroupBetween(userId, otherUserId)?.let { return it }
        rateLimiter.checkLimit("messaging:group-create:$userId", limit = 20, window = Duration.ofHours(1))
        val otherUser = userRepository.findById(otherUserId)
            .orElseThrow { GroupMemberNotFoundException("No itunda account found for that user") }
        return createGroupInternal(
            creatorUserId = userId,
            trimmedName = "Split with ${otherUser.firstName}",
            distinctOtherMembers = listOf(otherUserId),
            isDirect = true,
        )
    }

    /**
     * Real read-only peek at whether [userId] and [otherUserId] already have a hidden
     * direct-split group (2026-08-09) -- unlike [getOrCreateDirectSplitGroup], never
     * creates one. Backs a client's "show my past split bills with this person" view:
     * opening a 1:1 conversation's split-bill tab shouldn't itself create a hidden
     * group before any real bill exists between them.
     */
    fun findDirectGroup(userId: String, otherUserId: String): GroupConversation? =
        groupConversationRepository.findDirectGroupBetween(userId, otherUserId)

    /** Real 404 (not 403) for a non-member -- same "don't reveal a resource exists to
     * someone who shouldn't see it" discipline `MessagingService.requireParticipant`
     * already established for 1:1 conversations. */
    private fun requireMember(userId: String, groupId: String): GroupConversation {
        val group = groupConversationRepository.findById(groupId).orElseThrow { GroupNotFoundException("Group not found") }
        groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId)
            ?: throw GroupNotFoundException("Group not found")
        return group
    }

    /** Public wrapper over [requireMember] -- same reasoning as
     * `MessagingService.getConversationForParticipant`: lets a feature built on top of
     * an already-open group (e.g. `rw.itunda.splitbill.SplitBillService`, a real
     * chat-embedded split-bill) resolve/authorize "is this caller a real member of this
     * group" without duplicating this same IDOR check. */
    fun getGroupForMember(userId: String, groupId: String): GroupConversation = requireMember(userId, groupId)

    // Real message forwarding (2026-07-25) -- see MessageForwardService.forward's own
    // doc comment; identical shape to MessagingService.getMessageForParticipant.
    fun getMessageForMember(userId: String, messageId: String): GroupMessage {
        val message = groupMessageRepository.findById(messageId).orElseThrow { GroupMessageNotFoundException("Message not found") }
        requireMember(userId, message.groupConversationId)
        if (message.deletedAt != null) throw GroupMessageNotFoundException("Message not found")
        return message
    }

    /**
     * Real @mention resolution (2026-07-25) -- see `GroupMessage.mentionedUserIds`'s own
     * doc comment. Extracts `@Token` runs from [body] with a real regex, then resolves
     * each token against [memberIds]'s actual first names (case-insensitive exact
     * match) -- never trusts a client-supplied user-id list, since that would let a
     * message claim to mention anyone, including a non-member, which would be a real
     * IDOR-adjacent spoof (a fabricated "you were mentioned" notification to someone who
     * never actually appeared in this conversation). A first-name collision between two
     * real members resolves to whichever member matches first (itunda's own honest v1
     * scoping choice -- Kakao's own product disambiguates via a real tap-to-select
     * autocomplete in the composer, which is a client-side UI concern, not something
     * this server-side parser can decide on the sender's behalf).
     */
    internal fun parseMentions(body: String, memberIds: List<String>): Set<String> {
        if (memberIds.isEmpty()) return emptySet()
        val members = userRepository.findAllById(memberIds).associateBy { it.firstName.lowercase() }
        val tokens = Regex("@(\\w+)").findAll(body).map { it.groupValues[1].lowercase() }
        return tokens.mapNotNull { token -> members[token]?.id }.toSet()
    }

    @Transactional
    fun sendMessage(
        userId: String,
        groupId: String,
        body: String,
        replyToMessageId: String? = null,
        forwardedFromMessageId: String? = null,
        forwardedFromType: String? = null,
        imageUrl: String? = null,
        // Real Emoticon Store send (2026-07-26) -- see MessagingService.sendMessage's
        // own doc comment on the identical trust boundary here (ownership already
        // verified by EmoticonService before this is ever invoked).
        emoticonId: String? = null,
    ): GroupMessage {
        // Real composer photo send (2026-07-25) -- see MessagingService.sendMessage's
        // own doc comment for the full account; identical shape here.
        if (imageUrl != null && !imageUrl.startsWith("/api/v1/uploads/")) {
            throw InvalidGroupMessageImageException("imageUrl must be a real uploaded file from /api/v1/uploads")
        }
        val trimmed = body.trim().ifEmpty { if (emoticonId != null) "😀 Emoticon" else if (imageUrl != null) "📷 Photo" else "" }
        if (trimmed.isEmpty()) {
            throw EmptyGroupMessageException("Message body cannot be empty")
        }
        // Real bound, matching MessagingService.sendMessage's own identical fix the
        // same day -- `body` is VARCHAR(2000), and this DB's real STRICT_TRANS_TABLES
        // mode throws a raw, unhandled 500 on an over-length insert.
        if (trimmed.length > 2000) {
            throw GroupMessageTooLongException("Message body must be 2000 characters or fewer")
        }
        rateLimiter.checkLimit("messaging:group-send:$userId", limit = 30, window = Duration.ofMinutes(1))

        val group = requireMember(userId, groupId)
        replyToMessageId?.let { replyId ->
            val replied = groupMessageRepository.findById(replyId).orElseThrow { GroupMessageNotFoundException("Message not found") }
            if (replied.groupConversationId != groupId) throw GroupMessageNotFoundException("Message not found")
        }
        val memberIds = groupConversationMemberRepository.findByGroupConversationId(groupId).map { it.userId }
        val mentionedUserIds = parseMentions(trimmed, memberIds)
        val message = groupMessageRepository.save(
            GroupMessage(
                id = "group_message_${UUID.randomUUID()}", groupConversationId = groupId, senderId = userId, body = trimmed,
                replyToMessageId = replyToMessageId,
                forwardedFromMessageId = forwardedFromMessageId, forwardedFromType = forwardedFromType,
                mentionedUserIds = mentionedUserIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                imageUrl = imageUrl, emoticonId = emoticonId,
            ),
        )
        group.lastMessageAt = message.sentAt
        groupConversationRepository.save(group)

        val recipientIds = groupConversationMemberRepository.findByGroupConversationId(groupId)
            .map { it.userId }
            .filter { it != userId }
        val senderName = userRepository.findById(userId).map { "${it.firstName} ${it.lastName}" }.orElse("Someone")
        // Real N+1 fix (2026-07-19 sweep): one batch saveAll instead of one save call
        // per recipient, same convention createGroupInternal's own member-insert already
        // uses just above.
        notificationRepository.saveAll(
            recipientIds.map { recipientId ->
                // Real @mention-aware notification (2026-07-25) -- a mentioned recipient
                // gets a distinctly-titled, higher-signal notification, matching Kakao's
                // own real "mention" treatment as more attention-worthy than an ordinary
                // new message in a group they're already in.
                val mentioned = recipientId in mentionedUserIds
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = recipientId,
                    type = if (mentioned) "GROUP_MENTION" else "NEW_GROUP_MESSAGE",
                    title = if (mentioned) "$senderName mentioned you in ${group.name}" else "${group.name}: $senderName",
                    body = trimmed.take(120),
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"groupConversationId\":\"$groupId\"}",
                )
            },
        )
        // Real push wired in (2026-07-28) -- deliberately mention-only, not every
        // ordinary group message: a busy group can send dozens of messages an hour, and
        // pushing for each one would be exactly the notification spam real messaging
        // apps (Slack, KakaoTalk) avoid by pushing only for an explicit @mention, same
        // "higher-signal" reasoning the GROUP_MENTION notification type above already
        // establishes.
        recipientIds.filter { it in mentionedUserIds }.forEach { recipientId ->
            runAfterCommit {
                pushNotificationService.sendToUser(
                    recipientId, "$senderName mentioned you in ${group.name}", trimmed.take(120),
                    mapOf("groupConversationId" to groupId),
                )
            }
        }
        runAfterCommit {
            realtimeMessagePublisher.publishNewGroupMessage(groupId, recipientIds, message)
        }
        return message
    }

    @Transactional
    fun deleteMessage(userId: String, groupId: String, messageId: String) {
        requireMember(userId, groupId)
        val message = groupMessageRepository.findById(messageId).orElseThrow { GroupMessageNotFoundException("Message not found") }
        if (message.groupConversationId != groupId) throw GroupMessageNotFoundException("Message not found")
        if (message.senderId != userId) throw GroupMessageDeleteForbiddenException("Only the sender can delete this message")
        if (message.deletedAt == null) {
            message.deletedAt = Instant.now()
            message.deletedByUserId = userId
            groupMessageRepository.save(message)
        }
    }

    @Transactional
    fun getMessages(userId: String, groupId: String, pageable: Pageable): Page<GroupMessage> {
        requireMember(userId, groupId)
        val page = groupMessageRepository.findByGroupConversationIdOrderBySentAtDesc(groupId, pageable)
        val member = groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId)!!
        val now = Instant.now()
        member.lastReadAt = now
        groupConversationMemberRepository.save(member)

        // Real live read-receipt countdown (2026-07-26) -- see getUnreadCounts's own
        // doc comment. Pushed to every other real member so an open thread's per-message
        // countdown decrements live, not only on their own next refetch.
        val otherMemberIds = groupConversationMemberRepository.findByGroupConversationId(groupId)
            .map { it.userId }
            .filter { it != userId }
        realtimeMessagePublisher.publishGroupReadReceiptChange(groupId, otherMemberIds, userId, now)
        return page
    }

    /**
     * Real per-message unread countdown -- see `GroupConversationMember.lastReadAt`'s
     * own doc comment, which named this exact upgrade path. Reuses the existing
     * per-member cursor rather than a new per-message-per-member row: since opening a
     * thread always marks it read up through "now" (this service's own `getMessages`
     * convention), a message's real remaining-unread count is exactly how many OTHER
     * members (excluding the sender, who trivially "read" their own message) have a
     * `lastReadAt` earlier than that message's `sentAt`, or `null` (never opened this
     * thread at all) -- the exact real cursor state, not a best-effort estimate.
     */
    fun getUnreadCounts(groupId: String, messages: List<GroupMessage>): Map<String, Int> {
        if (messages.isEmpty()) return emptyMap()
        val members = groupConversationMemberRepository.findByGroupConversationId(groupId)
        return messages.associate { message ->
            val unread = members.count { it.userId != message.senderId && (it.lastReadAt == null || it.lastReadAt!!.isBefore(message.sentAt)) }
            message.id to unread
        }
    }

    // Real batch fetch (2026-07-19, found in a security/performance sweep) -- was a real
    // N+1: up to 4 queries per group (own-membership-row lookup, last message, member
    // count via fetching every member row just to call .size, unread count), so a real
    // 20-item page cost up to 80 queries. Now 4 queries total for the first three
    // sources plus one small per-group query for unread counts (that last one has a
    // real per-group cursor and isn't batchable without raw SQL -- see
    // GroupMessageRepository.countUnread's own doc comment for why that's an honest,
    // named partial fix rather than blocking the other three on it).
    fun listMyGroups(userId: String, pageable: Pageable): Page<GroupSummary> {
        val page = groupConversationRepository.findByMember(userId, pageable)
        val groups = page.content
        if (groups.isEmpty()) return PageImpl(emptyList(), pageable, page.totalElements)

        val groupIds = groups.map { it.id }
        val myMembershipByGroupId = groupConversationMemberRepository
            .findByGroupConversationIdInAndUserId(groupIds, userId)
            .associateBy { it.groupConversationId }
        val lastMessageByGroupId = groupMessageRepository
            .findByGroupConversationIdInOrderBySentAtDesc(groupIds, Pageable.ofSize(500))
            .groupBy { it.groupConversationId }
            .mapValues { (_, messages) -> messages.first() }
        val memberCountByGroupId = groupConversationMemberRepository.countMembersByGroupConversationIds(groupIds)
            .associate { it.groupConversationId to it.memberCount }

        val summaries = groups.map { group ->
            val member = myMembershipByGroupId.getValue(group.id)
            GroupSummary(
                groupId = group.id,
                name = group.name,
                memberCount = (memberCountByGroupId[group.id] ?: 0L).toInt(),
                lastMessageAt = group.lastMessageAt,
                lastMessagePreview = lastMessageByGroupId[group.id]?.let { if (it.deletedAt == null) it.body else "This message was deleted" },
                unreadCount = groupMessageRepository.countUnread(group.id, userId, member.lastReadAt),
            )
        }
        return PageImpl(summaries, pageable, page.totalElements)
    }

    /**
     * Real member list with real resolved display names (2026-07-18) -- closes the
     * honest, repeatedly-named limitation every group chat UI (bank-mfe, Android, iOS)
     * has carried since group chat first shipped: message bubbles showing a truncated
     * sender id instead of a real name. Batch-resolves every member's real name in one
     * `findAllById` call rather than one query per member.
     */
    fun getMembers(userId: String, groupId: String): List<GroupMemberInfo> {
        requireMember(userId, groupId)
        val members = groupConversationMemberRepository.findByGroupConversationId(groupId)
        val usersById = userRepository.findAllById(members.map { it.userId }).associateBy { it.id }
        return members.map { m ->
            val name = usersById[m.userId]?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown user"
            GroupMemberInfo(userId = m.userId, name = name)
        }
    }

    // Real emoji reactions (2026-07-19) -- same real toggle shape as 1:1
    // MessagingService.toggleReaction, fanned out to every other real member.
    @Transactional
    fun toggleReaction(userId: String, groupMessageId: String, emoji: String): List<ReactionGroup> {
        val trimmedEmoji = emoji.trim()
        if (trimmedEmoji.isEmpty() || trimmedEmoji.length > 16) {
            throw InvalidGroupReactionException("Reaction must be between 1 and 16 characters")
        }
        // Real anti-spam limit -- see MessagingService.toggleReaction's own identical
        // note; a group toggle fans out to every other real member, so an unbounded
        // caller here amplifies further than the 1:1 case does.
        rateLimiter.checkLimit("messaging:group-reaction:$userId", limit = 60, window = Duration.ofMinutes(1))
        val message = groupMessageRepository.findById(groupMessageId).orElseThrow { GroupMessageNotFoundException("Message not found") }
        requireMember(userId, message.groupConversationId)

        val existing = groupMessageReactionRepository.findByGroupMessageIdAndUserIdAndEmoji(groupMessageId, userId, trimmedEmoji)
        if (existing != null) {
            groupMessageReactionRepository.delete(existing)
        } else {
            groupMessageReactionRepository.save(
                GroupMessageReaction(id = "group_message_reaction_${UUID.randomUUID()}", groupMessageId = groupMessageId, userId = userId, emoji = trimmedEmoji),
            )
        }

        val reactions = groupReactions(groupMessageReactionRepository.findByGroupMessageId(groupMessageId).map { it.emoji to it.userId })
        val recipientIds = groupConversationMemberRepository.findByGroupConversationId(message.groupConversationId)
            .map { it.userId }
            .filter { it != userId }
        realtimeMessagePublisher.publishGroupReactionChange(message.groupConversationId, recipientIds, groupMessageId, reactions)
        return reactions
    }

    fun getReactionSummaries(groupMessageIds: List<String>): Map<String, List<ReactionGroup>> {
        if (groupMessageIds.isEmpty()) return emptyMap()
        return groupMessageReactionRepository.findByGroupMessageIdIn(groupMessageIds)
            .groupBy { it.groupMessageId }
            .mapValues { (_, reactions) -> groupReactions(reactions.map { it.emoji to it.userId }) }
    }

    // Real Thread support (2026-08-05) -- see MessageReplyCount's own doc comment
    // (rw.itunda.core.repository) for the full sourced account; identical shape here.
    fun getReplyCounts(groupMessageIds: List<String>): Map<String, Long> {
        if (groupMessageIds.isEmpty()) return emptyMap()
        return groupMessageRepository.countRepliesByMessageIds(groupMessageIds).associate { it.rootMessageId to it.replyCount }
    }

    fun getThread(userId: String, groupId: String, rootMessageId: String): List<GroupMessage> {
        requireMember(userId, groupId)
        val root = groupMessageRepository.findById(rootMessageId).orElseThrow { GroupMessageNotFoundException("Message not found") }
        if (root.groupConversationId != groupId) throw GroupMessageNotFoundException("Message not found")
        val replies = groupMessageRepository.findByReplyToMessageIdAndDeletedAtIsNullOrderBySentAtAsc(rootMessageId)
        return listOf(root) + replies
    }

    private fun groupReactions(emojiAndUserIds: List<Pair<String, String>>): List<ReactionGroup> =
        emojiAndUserIds.groupBy({ it.first }, { it.second }).map { (emoji, userIds) -> ReactionGroup(emoji, userIds) }

    @Transactional
    fun addMember(requesterId: String, groupId: String, newUserId: String): GroupConversation {
        val group = requireMember(requesterId, groupId)
        userRepository.findById(newUserId).orElseThrow { GroupMemberNotFoundException("No itunda account found for this user") }
        if (groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, newUserId) != null) {
            throw AlreadyGroupMemberException("This user is already a member of the group")
        }
        groupConversationMemberRepository.save(
            GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = groupId, userId = newUserId),
        )
        return group
    }

    /**
     * Real group-chat pin (2026-07-26) -- closes the "pin" half of
     * docs/DESIGN_REFERENCES.md's Talk long-press-menu recommendation for group chats;
     * 1:1 conversations already had this (`MessagingService.setPinnedMessage`), whose
     * exact same "any real member can pin/unpin, no owner-only restriction" shape this
     * mirrors -- a shared conversation has one shared pin, same as Kakao's own real
     * behavior.
     */
    @Transactional
    fun setPinnedMessage(userId: String, groupId: String, messageId: String?) {
        val group = requireMember(userId, groupId)
        if (messageId != null) {
            val message = groupMessageRepository.findById(messageId).orElseThrow { GroupMessageNotFoundException("Message not found") }
            if (message.groupConversationId != groupId || message.deletedAt != null) throw GroupMessageNotFoundException("Message not found")
        }
        group.pinnedMessageId = messageId
        groupConversationRepository.save(group)
    }

    /**
     * Real group photo (2026-07-28), open to any real member -- same flat-membership
     * discipline `setPinnedMessage` already establishes, since this codebase has no
     * real admin/owner concept yet (see this class's own doc comment). A URL, not a
     * binary upload -- this backend has no file-storage layer, same honest
     * simplification `AuthService.updateProfilePhoto`/`SplitBillService.attachReceipt`
     * already use. Blank clears it back to unset.
     */
    @Transactional
    fun setGroupPhotoUrl(userId: String, groupId: String, photoUrl: String): GroupConversation {
        val group = requireMember(userId, groupId)
        val trimmed = photoUrl.trim()
        if (trimmed.length > 2048) {
            throw GroupPhotoUrlTooLongException("Group photo URL must be 2048 characters or fewer")
        }
        group.photoUrl = trimmed.ifEmpty { null }
        return groupConversationRepository.save(group)
    }

    /** Real group description (2026-07-28) -- see `setGroupPhotoUrl`'s own doc comment
     * for the shared open-to-any-member/no-file-storage/blank-clears conventions. */
    @Transactional
    fun setGroupDescription(userId: String, groupId: String, description: String): GroupConversation {
        val group = requireMember(userId, groupId)
        val trimmed = description.trim()
        if (trimmed.length > 500) {
            throw GroupDescriptionTooLongException("Group description must be 500 characters or fewer")
        }
        group.description = trimmed.ifEmpty { null }
        return groupConversationRepository.save(group)
    }

    /** Resolves the shared pin only after the usual non-disclosing membership check. */
    fun getPinnedMessage(userId: String, groupId: String): GroupMessage? {
        val group = requireMember(userId, groupId)
        return group.pinnedMessageId?.let { messageId ->
            // A stale legacy reference must not make the group inaccessible.
            groupMessageRepository.findById(messageId).orElse(null)?.takeIf { it.groupConversationId == groupId }
        }
    }

    @Transactional
    fun leaveGroup(userId: String, groupId: String) {
        val member = groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId)
            ?: throw GroupNotFoundException("Group not found")
        groupConversationMemberRepository.delete(member)
    }
}
