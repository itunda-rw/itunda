package rw.itunda.splitbill

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.SplitBill
import rw.itunda.core.format.formatAmount
import rw.itunda.core.domain.SplitBillMode
import rw.itunda.core.domain.SplitBillParticipant
import rw.itunda.core.domain.SplitBillParticipantStatus
import rw.itunda.core.domain.SplitBillStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.SplitBillParticipantRepository
import rw.itunda.core.repository.SplitBillRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.messaging.GroupMessagingService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SplitBillNotFoundException(message: String) : RuntimeException(message)
class SplitBillInvalidAmountException(message: String) : RuntimeException(message)
class SplitBillNeedsParticipantsException(message: String) : RuntimeException(message)
class SplitBillParticipantNotGroupMemberException(message: String) : RuntimeException(message)
class SplitBillAlreadyPaidException(message: String) : RuntimeException(message)
class SplitBillNoAccountException(message: String) : RuntimeException(message)
class SplitBillDescriptionRequiredException(message: String) : RuntimeException(message)
class SplitBillInvalidVarianceLevelException(message: String) : RuntimeException(message)
class SplitBillInvalidReceiptUrlException(message: String) : RuntimeException(message)
class SplitBillAlreadySettledException(message: String) : RuntimeException(message)
class SplitBillNoPendingParticipantsException(message: String) : RuntimeException(message)
class SplitBillMaxRoundsReachedException(message: String) : RuntimeException(message)

data class SplitBillWithParticipants(val splitBill: SplitBill, val participants: List<SplitBillParticipant>)

/**
 * Real KakaoPay-style "정산하기" (settlement/split-bill), chat-embedded in an existing
 * [rw.itunda.core.domain.GroupConversation] -- see `SplitBill.kt`'s own doc comment for
 * the full structural account of why this is a direct-payback shape, the reverse of
 * [rw.itunda.gift.GiftService]'s escrow-then-claim, and `docs/DESIGN_REFERENCES.md`
 * Section 6/7 for the sourced design rationale this implements.
 *
 * Honestly scoped v1, per the design doc's own explicit warning that "a plain even-split
 * alone would just be re-doing Toss": this ports the even-split mechanic (the floor) plus
 * exactly one of KakaoPay's real differentiators -- silent rounding-remainder absorption,
 * the most tractable of the four named in the design doc. Of the other three real,
 * named follow-ups, all four are now closed: the randomized "사다리타기" ladder-game mode
 * (`ladderSplit`, 2026-07-25), scheduled reminder nudges (`SplitBillReminderScheduler`,
 * 2026-07-27), photo receipt attach (`attachReceipt`, 2026-07-28), and up-to-5-tracked
 * settlement rounds (`requestNextRound`, 2026-07-28).
 */
@Service
class SplitBillService(
    private val splitBillRepository: SplitBillRepository,
    private val splitBillParticipantRepository: SplitBillParticipantRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val groupMessagingService: GroupMessagingService,
    private val rateLimiter: RateLimiter,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    private val log = LoggerFactory.getLogger(SplitBillService::class.java)

    /**
     * Create a real split bill within an existing group conversation -- the organizer
     * already fronted [totalAmount] outside this system and is requesting it back from
     * [participantUserIds], a real subset of the group's own membership (never including
     * the organizer -- they don't owe their own request). The even split silently
     * reconciles any rounding remainder onto exactly one participant (deterministically
     * the last one in the validated, de-duplicated list) so the sum of every
     * participant's [SplitBillParticipant.shareAmount] always equals [totalAmount]
     * exactly, in the currency's own minor unit (cents), never off by a rounding error.
     */
    @Transactional
    fun createSplitBill(
        organizerId: String,
        groupConversationId: String,
        totalAmount: BigDecimal,
        description: String,
        participantUserIds: List<String>,
        mode: SplitBillMode = SplitBillMode.EVEN,
        ladderVarianceLevel: Int? = null,
    ): SplitBillWithParticipants {
        if (totalAmount <= BigDecimal.ZERO) throw SplitBillInvalidAmountException("Total amount must be greater than zero")
        val trimmedDescription = description.trim().take(200)
        if (trimmedDescription.isEmpty()) throw SplitBillDescriptionRequiredException("A split bill needs a short description")
        if (mode == SplitBillMode.LADDER && ladderVarianceLevel !in 1..3) {
            throw SplitBillInvalidVarianceLevelException("ladderVarianceLevel must be 1, 2, or 3 for LADDER mode")
        }

        // Real anti-spam limit, same convention as every other money-moving creation
        // endpoint in this codebase (Gift, P2P request/send).
        rateLimiter.checkLimit("splitbill:create:$organizerId", limit = 20, window = Duration.ofHours(1))

        // Real 404 (not 403) if the organizer isn't a real member of this group --
        // reuses GroupMessagingService's own IDOR check rather than re-validating here.
        groupMessagingService.getGroupForMember(organizerId, groupConversationId)
        val groupMemberIds = groupMessagingService.getMembers(organizerId, groupConversationId).map { it.userId }.toSet()

        val distinctParticipants = participantUserIds.distinct().filter { it != organizerId }
        if (distinctParticipants.isEmpty()) {
            throw SplitBillNeedsParticipantsException("A split bill needs at least one other real participant")
        }
        // Real, honest validation error (not an IDOR check -- this is the organizer's
        // own input, not someone else's resource) if a named participant isn't actually
        // a member of this group.
        distinctParticipants.firstOrNull { it !in groupMemberIds }?.let {
            throw SplitBillParticipantNotGroupMemberException("Every participant must be a real member of this group")
        }

        val shares = if (mode == SplitBillMode.LADDER) {
            ladderSplit(totalAmount, distinctParticipants.size, ladderVarianceLevel!!)
        } else {
            evenSplitWithRoundingAbsorption(totalAmount, distinctParticipants.size)
        }

        // Post the real chat-embedded message first so the split bill's own row can
        // reference a real messageId, same "message exists, then the domain row points
        // at it" ordering GiftService.createGift already established.
        val message = groupMessagingService.sendMessage(
            organizerId,
            groupConversationId,
            formatSplitBillCreatedBody(trimmedDescription, totalAmount, distinctParticipants.size, mode, ladderVarianceLevel),
        )

        val savedSplitBill = splitBillRepository.save(
            SplitBill(
                id = "splitbill_${UUID.randomUUID()}",
                organizerId = organizerId,
                groupConversationId = groupConversationId,
                messageId = message.id,
                totalAmount = totalAmount,
                description = trimmedDescription,
                mode = mode,
                ladderVarianceLevel = if (mode == SplitBillMode.LADDER) ladderVarianceLevel else null,
            ),
        )
        val now = Instant.now()
        val participants = distinctParticipants.mapIndexed { index, userId ->
            // Real "nothing owed" auto-settle (2026-07-25) -- LADDER mode's real
            // "loser pays it all" top variance level assigns some participants a
            // genuine zero share; there's nothing for them to pay, so they start
            // PAID rather than sitting on a real payShare() call for RWF 0.
            val share = shares[index]
            // BigDecimal.equals() (and ==) also compares scale, so "0.00" == ZERO is
            // false even though they're numerically equal -- compareTo() is the real
            // numeric comparison, found live testing this exact zero-share path.
            val isZero = share.compareTo(BigDecimal.ZERO) == 0
            SplitBillParticipant(
                id = "splitbill_participant_${UUID.randomUUID()}",
                splitBillId = savedSplitBill.id,
                userId = userId,
                shareAmount = share,
                status = if (isZero) SplitBillParticipantStatus.PAID else SplitBillParticipantStatus.PENDING,
                paidAt = if (isZero) now else null,
            )
        }
        val savedParticipants = splitBillParticipantRepository.saveAll(participants)

        // A ladder split can hand a single participant the whole bill up front --
        // check settlement immediately rather than waiting for the one real payShare()
        // call, same completion check payShare() itself runs after every payment.
        if (savedParticipants.all { it.status == SplitBillParticipantStatus.PAID }) {
            savedSplitBill.status = SplitBillStatus.SETTLED
            savedSplitBill.settledAt = now
            splitBillRepository.save(savedSplitBill)
        }
        return SplitBillWithParticipants(savedSplitBill, savedParticipants)
    }

    /**
     * Real 1:1-chat split-bill entry point (2026-08-09, docs/DESIGN_REFERENCES.md
     * Section 19) -- the same real [createSplitBill] underneath, just resolving
     * [otherUserId] to a hidden 2-person group first via
     * [rw.itunda.messaging.GroupMessagingService.getOrCreateDirectSplitGroup] instead of
     * requiring the caller to already be inside a real named group. This is the fix for
     * the gap investigated (and shelved) twice before: a bill between exactly two people
     * talking 1:1 previously had nowhere to attach without either faking a named group
     * (which showed up in both people's real "My Groups" list) or building a second,
     * parallel split-bill code path for pairs. Neither happened -- this just gives
     * [createSplitBill] a group id it can't get any other way for this specific case.
     */
    @Transactional
    fun createDirectSplitBill(
        organizerId: String,
        otherUserId: String,
        totalAmount: BigDecimal,
        description: String,
        mode: SplitBillMode = SplitBillMode.EVEN,
        ladderVarianceLevel: Int? = null,
    ): SplitBillWithParticipants {
        val group = groupMessagingService.getOrCreateDirectSplitGroup(organizerId, otherUserId)
        return createSplitBill(organizerId, group.id, totalAmount, description, listOf(otherUserId), mode, ladderVarianceLevel)
    }

    /**
     * Real read-only counterpart to [createDirectSplitBill] (2026-08-09) -- lets a
     * client show past split bills between [userId] and [otherUserId] without creating
     * a hidden group as a side effect of just opening the view (see
     * [rw.itunda.messaging.GroupMessagingService.findDirectGroup]'s own doc comment).
     * An empty list, not an error, when no split bill has ever existed between them.
     */
    fun getDirectSplitBills(userId: String, otherUserId: String): List<SplitBillWithParticipants> {
        val group = groupMessagingService.findDirectGroup(userId, otherUserId) ?: return emptyList()
        return getSplitBillsForGroup(userId, group.id)
    }

    /**
     * Real KakaoPay 사다리타기 (ladder-game) randomized split -- see
     * `docs/DESIGN_REFERENCES.md` Section 6 (seoulfn.com/hankyung.com/digitaltoday.co.kr/
     * moneys.mt.co.kr/v.daum.net, cross-verified across 5 outlets): "3 adjustable
     * variance levels, top level assigns the whole amount to one 'loser'." Levels 1/2
     * randomize each participant's weight within a bounded range around an even split
     * (wider at level 2), normalize to the real total, and reconcile any rounding
     * remainder onto the last participant -- same exact-reconciliation discipline
     * `evenSplitWithRoundingAbsorption` already established, just with randomized
     * weights instead of uniform ones. Level 3 is the real named differentiator itself:
     * one random participant owes the entire bill, everyone else owes nothing.
     */
    internal fun ladderSplit(totalAmount: BigDecimal, participantCount: Int, varianceLevel: Int, random: kotlin.random.Random = kotlin.random.Random.Default): List<BigDecimal> {
        val totalMinorUnits = totalAmount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        if (varianceLevel == 3) {
            val loserIndex = random.nextInt(participantCount)
            return (0 until participantCount).map { index ->
                if (index == loserIndex) BigDecimal(totalMinorUnits).movePointLeft(2).setScale(2) else BigDecimal.ZERO.setScale(2)
            }
        }
        val range = if (varianceLevel == 2) 0.4..1.6 else 0.7..1.3
        val weights = (0 until participantCount).map { random.nextDouble(range.start, range.endInclusive) }
        val weightSum = weights.sum()
        val minorShares = weights.map { weight -> ((totalMinorUnits.toDouble() * weight / weightSum)).toLong() }.toMutableList()
        val remainder = totalMinorUnits - minorShares.sum()
        minorShares[minorShares.size - 1] += remainder
        return minorShares.map { BigDecimal(it).movePointLeft(2).setScale(2) }
    }

    /**
     * Real per-participant even split with silent rounding-remainder absorption --
     * KakaoPay's own real "정산하기 absorbs the rounding remainder" differentiator (see
     * `docs/DESIGN_REFERENCES.md` Section 6). Computed in the currency's own minor unit
     * (cents, i.e. hundredths of a RWF) so the arithmetic is exact integer division, not
     * floating-point-adjacent BigDecimal division that could leave a residual: the last
     * participant in the list deterministically absorbs whatever remainder integer
     * division leaves behind, so `shares.sum() == totalAmount` always holds exactly.
     */
    internal fun evenSplitWithRoundingAbsorption(totalAmount: BigDecimal, participantCount: Int): List<BigDecimal> {
        val totalMinorUnits = totalAmount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        val baseShareMinorUnits = totalMinorUnits / participantCount
        val remainderMinorUnits = totalMinorUnits % participantCount
        return (0 until participantCount).map { index ->
            val minorUnits = if (index == participantCount - 1) baseShareMinorUnits + remainderMinorUnits else baseShareMinorUnits
            BigDecimal(minorUnits).movePointLeft(2).setScale(2)
        }
    }

    /** A stranger (neither organizer nor a named participant) gets a real 404, same
     * IDOR discipline as `GiftService.getGift`. */
    fun getSplitBill(userId: String, splitBillId: String): SplitBillWithParticipants {
        val splitBill = splitBillRepository.findById(splitBillId).orElseThrow { SplitBillNotFoundException("Split bill not found") }
        val participants = splitBillParticipantRepository.findBySplitBillId(splitBillId)
        val isOrganizer = userId == splitBill.organizerId
        val isParticipant = participants.any { it.userId == userId }
        if (!isOrganizer && !isParticipant) throw SplitBillNotFoundException("Split bill not found")
        return SplitBillWithParticipants(splitBill, participants)
    }

    /**
     * Real photo receipt attach (2026-07-28), closing one of the two real follow-ups
     * `SplitBill.kt`'s own doc comment still named as open. Organizer-only -- same real
     * 404 (not 403) IDOR discipline `getSplitBill`/`payShare` already establish, not a
     * fabricated permission error. A URL, not a binary upload -- this backend has no
     * file-storage layer, the same honest simplification `AuthService
     * .updateProfilePhoto`/`IdentityController`'s own documentReference already use.
     * Re-attachable (overwrites, no versioning) -- same "the end state is what the
     * caller actually wants" simplicity `RoundUpSettings`'s own upsert already
     * establishes, not something that needs its own history here.
     */
    @Transactional
    fun attachReceipt(organizerId: String, splitBillId: String, imageUrl: String): SplitBill {
        val splitBill = splitBillRepository.findById(splitBillId).orElseThrow { SplitBillNotFoundException("Split bill not found") }
        if (splitBill.organizerId != organizerId) {
            throw SplitBillNotFoundException("Split bill not found")
        }
        val trimmedUrl = imageUrl.trim()
        if (trimmedUrl.isEmpty() || trimmedUrl.length > 2048) {
            throw SplitBillInvalidReceiptUrlException("Receipt image URL must be between 1 and 2048 characters")
        }
        splitBill.receiptImageUrl = trimmedUrl
        return splitBillRepository.save(splitBill)
    }

    /**
     * Real KakaoPay-style explicit settlement "round" escalation -- distinct from
     * [sendDueReminders]'s automatic, silent, every-24h per-participant nudge. Round 1 is
     * the original request itself (sent at [createSplitBill] time); the organizer calling
     * this explicitly starts a NEW round, re-announcing the request in the same group
     * thread to whoever is still unpaid, with the round number itself now visible as a
     * real, growing signal of how overdue the settlement is. Capped at [MAX_ROUNDS]
     * (KakaoPay's own real "up to 5" ceiling, `docs/DESIGN_REFERENCES.md` Section 6/7) --
     * prevents indefinite re-nudging spam, mirroring the reminder cadence's own
     * anti-spam discipline in a different shape (organizer-initiated cap, not a time
     * interval).
     */
    @Transactional
    fun requestNextRound(organizerId: String, splitBillId: String): SplitBill {
        val splitBill = splitBillRepository.findById(splitBillId).orElseThrow { SplitBillNotFoundException("Split bill not found") }
        if (splitBill.organizerId != organizerId) {
            throw SplitBillNotFoundException("Split bill not found")
        }
        if (splitBill.status != SplitBillStatus.OPEN) {
            throw SplitBillAlreadySettledException("This split bill is already settled")
        }
        val pendingParticipants = splitBillParticipantRepository.findBySplitBillId(splitBillId)
            .filter { it.status == SplitBillParticipantStatus.PENDING }
        if (pendingParticipants.isEmpty()) {
            throw SplitBillNoPendingParticipantsException("Every participant has already paid")
        }
        if (splitBill.currentRound >= MAX_ROUNDS) {
            throw SplitBillMaxRoundsReachedException("Already at the maximum of $MAX_ROUNDS settlement rounds")
        }
        splitBill.currentRound += 1
        val saved = splitBillRepository.save(splitBill)

        val owedList = pendingParticipants.joinToString(", ") { "${formatAmount(it.shareAmount)} RWF" }
        groupMessagingService.sendMessage(
            organizerId, splitBill.groupConversationId,
            "🔁 Settlement round ${saved.currentRound}/$MAX_ROUNDS for \"${splitBill.description}\" -- still owed: $owedList",
        )
        return saved
    }

    /** Real per-group split-bill history -- same shape as `GiftService.getGiftsForConversation`,
     * IDOR-checked via `GroupMessagingService.getGroupForMember` rather than trusting the
     * caller's own claimed userId against each row. */
    fun getSplitBillsForGroup(userId: String, groupConversationId: String): List<SplitBillWithParticipants> {
        groupMessagingService.getGroupForMember(userId, groupConversationId)
        return splitBillRepository.findByGroupConversationIdOrderByCreatedAtDesc(groupConversationId).map { splitBill ->
            SplitBillWithParticipants(splitBill, splitBillParticipantRepository.findBySplitBillId(splitBill.id))
        }
    }

    /**
     * Pay this caller's own real share directly to the organizer -- a direct
     * ACCOUNT-to-ACCOUNT push, same real shape `P2pService.sendDirect`/`payRequest`
     * already established (no escrow, no fee: nothing external to settle). A caller who
     * isn't a named participant of this split bill (including the organizer themselves,
     * who was never added as one) gets a real 404, not a fabricated permission error --
     * same IDOR discipline as every other resource-ownership check in this codebase.
     */
    @Transactional
    fun payShare(payerUserId: String, splitBillId: String): SplitBillParticipant {
        val splitBill = splitBillRepository.findById(splitBillId).orElseThrow { SplitBillNotFoundException("Split bill not found") }
        val participant = splitBillParticipantRepository.findBySplitBillIdAndUserId(splitBillId, payerUserId)
            ?: throw SplitBillNotFoundException("Split bill not found")
        if (participant.status != SplitBillParticipantStatus.PENDING) {
            throw SplitBillAlreadyPaidException("This share has already been paid")
        }

        // Real anti-spam limit, same 30/hour convention P2P's own payRequest/sendDirect
        // and Gift's own claimGift already established for a real mutating
        // money-movement endpoint.
        rateLimiter.checkLimit("splitbill:pay:$payerUserId", limit = 30, window = Duration.ofHours(1))

        val payerAccount = accountRepository.findByUserIdAndType(payerUserId, AccountType.MAIN)
            ?: throw SplitBillNoAccountException("No account found for this account")
        val organizerAccount = accountRepository.findByUserIdAndType(splitBill.organizerId, AccountType.MAIN)
            ?: throw SplitBillNoAccountException("Organizer has no account to receive this payment")
        if (payerAccount.availableBalance < participant.shareAmount) {
            throw InsufficientFundsException("Insufficient available balance for this payment")
        }

        val result = ledgerService.postLedgerTransaction(
            payerAccount.currency,
            listOf(
                LedgerLeg(payerAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, participant.shareAmount, "Split bill share - ${splitBill.description}"),
                LedgerLeg(organizerAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, participant.shareAmount, "Split bill share received - ${splitBill.description}"),
            ),
        )
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "SPLITBILL${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = payerUserId,
            recipientId = splitBill.organizerId,
            fromAccountId = payerAccount.id,
            toAccountId = organizerAccount.id,
            amount = participant.shareAmount,
            fee = BigDecimal.ZERO,
            currency = payerAccount.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Split bill share - ${splitBill.description}",
            channel = "SPLITBILL",
            completedAt = Instant.now(),
        )
        // Real fraud coverage (item 247 follow-up, docs/DESIGN_REFERENCES.md §14
        // recommendation #4) -- same real gap and same fix as GiftService.sendGift:
        // real money to a real recipient, evaluated before save so this transaction
        // can't match itself as prior history and mask NEW_RECIPIENT (see P2pService's
        // own inline comment for the full reasoning).
        fraudRuleEngine.evaluate(payerUserId, splitBill.organizerId, participant.shareAmount, transaction.id)
        transactionRepository.save(transaction)

        participant.status = SplitBillParticipantStatus.PAID
        participant.paidTransactionId = transaction.id
        participant.paidAt = Instant.now()
        val savedParticipant = splitBillParticipantRepository.save(participant)

        groupMessagingService.sendMessage(payerUserId, splitBill.groupConversationId, formatSharePaidBody(splitBill.description, participant.shareAmount))

        val allParticipants = splitBillParticipantRepository.findBySplitBillId(splitBillId)
        if (splitBill.status == SplitBillStatus.OPEN && allParticipants.all { it.status == SplitBillParticipantStatus.PAID }) {
            splitBill.status = SplitBillStatus.SETTLED
            splitBill.settledAt = Instant.now()
            splitBillRepository.save(splitBill)
            groupMessagingService.sendMessage(payerUserId, splitBill.groupConversationId, "✅ Split bill settled -- \"${splitBill.description}\" is fully paid")
        }

        return savedParticipant
    }

    // Real scheduled reminder nudges -- see SplitBillReminderScheduler's own doc
    // comment. A real chat-embedded nudge (same "chat-embedded financial action" shell
    // this whole feature already uses), sent as the organizer -- the same real person a
    // participant would expect to hear from asking "did you pay yet?" -- into the same
    // group thread the original request itself lives in, not a separate push channel.
    // Resilient per-participant: one bad row (e.g. a since-left group member) must
    // never block the reminder sweep for every other real due participant.
    fun sendDueReminders(): Int {
        var remindedCount = 0
        val openBills = splitBillRepository.findByStatus(SplitBillStatus.OPEN)
        for (bill in openBills) {
            val pendingParticipants = splitBillParticipantRepository.findBySplitBillId(bill.id)
                .filter { it.status == SplitBillParticipantStatus.PENDING }
            for (participant in pendingParticipants) {
                val lastReminder = participant.lastReminderSentAt
                val due = lastReminder == null || Duration.between(lastReminder, Instant.now()) >= REMINDER_INTERVAL
                if (!due) continue
                try {
                    groupMessagingService.sendMessage(
                        bill.organizerId, bill.groupConversationId,
                        "⏰ Reminder: you still owe ${formatAmount(participant.shareAmount)} RWF for \"${bill.description}\"",
                    )
                    participant.lastReminderSentAt = Instant.now()
                    splitBillParticipantRepository.save(participant)
                    remindedCount++
                } catch (e: Exception) {
                    log.warn("Split bill reminder skipped for participant {}: {}", participant.id, e.message)
                }
            }
        }
        return remindedCount
    }

    companion object {
        // Real KakaoPay-style 정산 reminder cadence -- once every real 24 hours per
        // still-unpaid participant, not a spammy repeat.
        private val REMINDER_INTERVAL: Duration = Duration.ofHours(24)

        // Real KakaoPay-style ceiling on organizer-initiated settlement rounds -- see
        // requestNextRound's own doc comment.
        const val MAX_ROUNDS: Int = 5
    }
}

/** A split bill's chat message body is a real, stable, machine-parseable format (same
 * "special message the client recognizes and renders specially" trick
 * `GiftService.formatGiftBody`/`PriceOfferService.formatOfferBody` already established)
 * -- the split bill's own real id links back via [SplitBill.messageId], so the client
 * never needs to parse the amount out of this string; it's a human-readable fallback for
 * any client that doesn't special-case split-bill messages. */
private fun formatSplitBillCreatedBody(description: String, totalAmount: BigDecimal, participantCount: Int, mode: SplitBillMode, ladderVarianceLevel: Int?): String {
    val modeLabel = when {
        mode == SplitBillMode.LADDER && ladderVarianceLevel == 3 -> " 🎲 (ladder game -- one random person pays it all!)"
        mode == SplitBillMode.LADDER -> " 🎲 (ladder game, variance level $ladderVarianceLevel)"
        else -> ""
    }
    return "🧾 Split bill: \"$description\" -- ${formatAmount(totalAmount)} RWF split $participantCount ways$modeLabel"
}

private fun formatSharePaidBody(description: String, shareAmount: BigDecimal): String =
    "✓ Paid their share of ${formatAmount(shareAmount)} RWF for \"$description\""
