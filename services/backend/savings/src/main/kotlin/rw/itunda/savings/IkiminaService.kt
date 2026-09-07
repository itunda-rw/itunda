package rw.itunda.savings

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Ikimina
import rw.itunda.core.domain.IkiminaContribution
import rw.itunda.core.domain.IkiminaMember
import rw.itunda.core.domain.IkiminaStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.IkiminaContributionRepository
import rw.itunda.core.repository.IkiminaMemberRepository
import rw.itunda.core.repository.IkiminaRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class IkiminaNotFoundException(message: String) : RuntimeException(message)
class IkiminaMemberNotFoundException(message: String) : RuntimeException(message)
class IkiminaAlreadyMemberException(message: String) : RuntimeException(message)
class IkiminaFullException(message: String) : RuntimeException(message)
class IkiminaNoAccountException(message: String) : RuntimeException(message)
class IkiminaNotFormingException(message: String) : RuntimeException(message)
class IkiminaNotActiveException(message: String) : RuntimeException(message)
class IkiminaTooFewMembersException(message: String) : RuntimeException(message)
class IkiminaAlreadyContributedException(message: String) : RuntimeException(message)
class IkiminaContributionsIncompleteException(message: String) : RuntimeException(message)

data class IkiminaMemberView(val userId: String, val firstName: String, val lastName: String, val payoutOrder: Int, val hasReceivedPayout: Boolean, val isOrganizer: Boolean)
data class IkiminaContributionStatusView(val userId: String, val contributed: Boolean)
data class IkiminaView(
    val ikimina: Ikimina, val balance: BigDecimal, val members: List<IkiminaMemberView>,
    val currentRoundContributions: List<IkiminaContributionStatusView>,
)
data class IkiminaPayoutResult(val ikimina: Ikimina, val recipientUserId: String, val amount: BigDecimal)
data class IkiminaContributionResult(val ikimina: Ikimina, val payout: IkiminaPayoutResult?)

private const val MIN_MEMBERS_TO_START = 2
private const val MAX_MEMBERS = 15

/**
 * Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See
 * Ikimina.kt's own doc comment for the full sourced account (real ROSCA literature +
 * a real existing Rwandan startup, smartikimina.rw, already digitizing this exact
 * mechanic). Distinct from GroupAccountService (Kakao Bank 모임통장): that feature has
 * one permanent owner and no rotation; this one has a rotating payout recipient and no
 * permanent single beneficiary. Reuses the exact real ledger-movement shape every
 * other money-moving feature in this backend already uses -- a real Account(type=GROUP)
 * per ikimina, contributions/payouts are real ACCOUNT-to-ACCOUNT ledger transactions.
 */
@Service
class IkiminaService(
    private val ikiminaRepository: IkiminaRepository,
    private val ikiminaMemberRepository: IkiminaMemberRepository,
    private val ikiminaContributionRepository: IkiminaContributionRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val accountNumberGenerator: AccountNumberGenerator,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(IkiminaService::class.java)

    @Transactional
    fun createIkimina(organizerId: String, name: String, contributionAmount: BigDecimal, cycleFrequencyDays: Int, memberCap: Int): Ikimina {
        rateLimiter.checkLimit("ikimina:create:$organizerId", limit = 10, window = Duration.ofHours(1))
        require(name.trim().isNotEmpty()) { "Name is required" }
        // Real gap found 2026-09-05, same shape as WeeklySavingsService/
        // Grow31SavingsService's own fixes: name is concatenated into the settlement
        // Account's own accountName ("$name (Ikimina)", 10 extra chars) -- 245
        // (255 - 10) is the real safe bound, not Ikimina.name's own (unenforced) 255.
        require(name.length <= 245) { "Name must be 245 characters or fewer" }
        require(contributionAmount > BigDecimal.ZERO) { "Contribution amount must be greater than zero" }
        require(cycleFrequencyDays > 0) { "Cycle frequency must be greater than zero days" }
        require(memberCap in MIN_MEMBERS_TO_START..MAX_MEMBERS) { "Member cap must be between $MIN_MEMBERS_TO_START and $MAX_MEMBERS" }
        userRepository.findById(organizerId).orElseThrow { IkiminaNotFoundException("Account not found") }

        val account = accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}",
                userId = organizerId,
                accountNumber = accountNumberGenerator.generate(2024200000L),
                accountName = "$name (Ikimina)",
                type = AccountType.GROUP,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        val ikimina = ikiminaRepository.save(
            Ikimina(
                id = "ikimina_${UUID.randomUUID()}", name = name.trim(), organizerId = organizerId, accountId = account.id,
                contributionAmount = contributionAmount, cycleFrequencyDays = cycleFrequencyDays, memberCap = memberCap,
            ),
        )
        ikiminaMemberRepository.save(
            IkiminaMember(id = "ikiminamem_${UUID.randomUUID()}", ikiminaId = ikimina.id, userId = organizerId, payoutOrder = 1),
        )
        return ikimina
    }

    fun getMyIkiminas(userId: String): List<Ikimina> {
        val ikiminaIds = ikiminaMemberRepository.findByUserId(userId).map { it.ikiminaId }
        return ikiminaRepository.findAllById(ikiminaIds).toList()
    }

    fun getIkimina(userId: String, ikiminaId: String): IkiminaView {
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        ikiminaMemberRepository.findByIkiminaIdAndUserId(ikiminaId, userId)
            ?: throw IkiminaNotFoundException("Ikimina not found")
        val account = accountRepository.findById(ikimina.accountId).orElseThrow { IkiminaNoAccountException("Account not found") }

        val members = ikiminaMemberRepository.findByIkiminaId(ikiminaId).sortedBy { it.payoutOrder }
        val users = userRepository.findAllById(members.map { it.userId }).associateBy { it.id }
        val memberViews = members.map { m ->
            val u = users[m.userId]
            IkiminaMemberView(
                userId = m.userId, firstName = u?.firstName ?: "", lastName = u?.lastName ?: "",
                payoutOrder = m.payoutOrder, hasReceivedPayout = m.hasReceivedPayout, isOrganizer = m.userId == ikimina.organizerId,
            )
        }
        val contributedThisRound = ikiminaContributionRepository.findByIkiminaIdAndRound(ikiminaId, ikimina.currentRound)
            .map { it.memberId }.toSet()
        val contributionStatus = members.map { m -> IkiminaContributionStatusView(userId = m.userId, contributed = m.id in contributedThisRound) }
        return IkiminaView(ikimina = ikimina, balance = account.balance, members = memberViews, currentRoundContributions = contributionStatus)
    }

    @Transactional
    fun inviteMember(organizerId: String, ikiminaId: String, phoneNumber: String): IkiminaMemberView {
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        if (ikimina.organizerId != organizerId) throw IkiminaNotFoundException("Ikimina not found")
        if (ikimina.status != IkiminaStatus.FORMING) throw IkiminaNotFormingException("Members can only be invited before the cycle starts")

        val invitee = userRepository.findByPhoneNumber(phoneNumber.trim())
            ?: throw IkiminaMemberNotFoundException("No itunda account found for this phone number")
        if (ikiminaMemberRepository.findByIkiminaIdAndUserId(ikiminaId, invitee.id) != null) {
            throw IkiminaAlreadyMemberException("This person is already a member")
        }
        val currentCount = ikiminaMemberRepository.countByIkiminaId(ikiminaId)
        if (currentCount >= ikimina.memberCap) {
            throw IkiminaFullException("This ikimina has reached its real ${ikimina.memberCap}-member cap")
        }

        val member = ikiminaMemberRepository.save(
            IkiminaMember(
                id = "ikiminamem_${UUID.randomUUID()}", ikiminaId = ikiminaId, userId = invitee.id,
                payoutOrder = (currentCount + 1).toInt(),
            ),
        )
        return IkiminaMemberView(
            userId = invitee.id, firstName = invitee.firstName, lastName = invitee.lastName,
            payoutOrder = member.payoutOrder, hasReceivedPayout = false, isOrganizer = false,
        )
    }

    @Transactional
    fun startCycle(organizerId: String, ikiminaId: String): Ikimina {
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        if (ikimina.organizerId != organizerId) throw IkiminaNotFoundException("Ikimina not found")
        if (ikimina.status != IkiminaStatus.FORMING) throw IkiminaNotFormingException("This ikimina's cycle has already started")
        val memberCount = ikiminaMemberRepository.countByIkiminaId(ikiminaId)
        if (memberCount < MIN_MEMBERS_TO_START) {
            throw IkiminaTooFewMembersException("At least $MIN_MEMBERS_TO_START members are required to start a cycle")
        }
        ikimina.status = IkiminaStatus.ACTIVE
        return ikiminaRepository.save(ikimina)
    }

    /**
     * Real bug caught during this session's own follow-up review, before it caused real
     * confusion in production: the round's payout previously only ever fired from a
     * fully separate, manually-triggered endpoint (`checkAndTriggerPayout`/`POST
     * .../payout`) that bank-mfe exposed as its own distinct button -- nothing called it
     * automatically. In practice, once every member has contributed for a round, NOTHING
     * pays anyone out until some member happens to remember to tap that separate button;
     * a real ikimina round could sit indefinitely completed-but-unpaid. Fixed by having
     * the contribution that completes a round automatically attempt the payout in the
     * same transaction -- `attemptPayout` returns `null` (not an exception) when
     * contributions are still incomplete, so every contribution except the last one is
     * unaffected. The explicit `checkAndTriggerPayout`/`POST .../payout` endpoint stays
     * real and callable (e.g. a client retry, or a member checking status), it just isn't
     * the ONLY path to a payout anymore.
     */
    @Transactional
    fun contributeThisRound(userId: String, ikiminaId: String): IkiminaContributionResult {
        // Real anti-spam/cost limit -- createIkimina already has one, contribution never
        // did, the same "row creation vs. repeatable action" gap class this pass's own
        // Loans research already named.
        rateLimiter.checkLimit("ikimina:contribute:$userId", limit = 30, window = Duration.ofHours(1))
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        if (ikimina.status != IkiminaStatus.ACTIVE) throw IkiminaNotActiveException("This ikimina's cycle is not currently active")
        val member = ikiminaMemberRepository.findByIkiminaIdAndUserId(ikiminaId, userId)
            ?: throw IkiminaNotFoundException("Ikimina not found")
        if (ikiminaContributionRepository.findByIkiminaIdAndMemberIdAndRound(ikiminaId, member.id, ikimina.currentRound) != null) {
            throw IkiminaAlreadyContributedException("You have already contributed for round ${ikimina.currentRound}")
        }

        val sourceAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw IkiminaNoAccountException("No account found for this account")
        val groupAccount = accountRepository.findById(ikimina.accountId).orElseThrow { IkiminaNoAccountException("Account not found") }

        ledgerService.postLedgerTransaction(
            sourceAccount.currency,
            listOf(
                LedgerLeg(sourceAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, ikimina.contributionAmount, "Ikimina contribution (\"${ikimina.name}\", round ${ikimina.currentRound})"),
                LedgerLeg(groupAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, ikimina.contributionAmount, "Ikimina contribution (\"${ikimina.name}\", round ${ikimina.currentRound})"),
            ),
        )
        // Real DB unique constraint on (ikimina_id, member_id, round) backs this --
        // the AlreadyContributed check above is a fast-path, not the sole guard.
        ikiminaContributionRepository.save(
            IkiminaContribution(id = "ikiminacontrib_${UUID.randomUUID()}", ikiminaId = ikiminaId, memberId = member.id, round = ikimina.currentRound, amount = ikimina.contributionAmount),
        )
        val payout = attemptPayout(ikimina)
        return IkiminaContributionResult(ikimina = payout?.ikimina ?: ikimina, payout = payout)
    }

    /**
     * Real check-then-act payout trigger -- has every active member contributed this
     * round? If so, pay the round's designated recipient (by rotation order) the FULL
     * pot in one real ledger transaction, mark them paid, advance the round, and mark
     * the ikimina COMPLETED once every member has been paid exactly once. `Ikimina`'s
     * own `@Version` field is what makes this safe under real concurrent calls (two
     * members' contribution calls both landing near the round-completing moment) --
     * the loser real-throws `ObjectOptimisticLockingFailureException`, already handled
     * globally as a clean 409 by `IdempotencyExceptionHandler.kt`, same as this
     * session's Bike/Parking/Knowledge/SupportTicket fixes all rely on. Returns `null`
     * (rather than throwing) when contributions aren't complete yet -- `contributeThisRound`
     * relies on that to auto-attempt a payout after every contribution without disrupting
     * the normal (round-still-incomplete) case.
     */
    private fun attemptPayout(ikimina: Ikimina): IkiminaPayoutResult? {
        val members = ikiminaMemberRepository.findByIkiminaId(ikimina.id)
        val contributedMemberIds = ikiminaContributionRepository.findByIkiminaIdAndRound(ikimina.id, ikimina.currentRound).map { it.memberId }.toSet()
        if (!members.all { it.id in contributedMemberIds }) return null

        val recipientPayoutOrder = ((ikimina.currentRound - 1) % members.size) + 1
        val recipient = ikiminaMemberRepository.findByIkiminaIdAndPayoutOrder(ikimina.id, recipientPayoutOrder)
            ?: throw IkiminaMemberNotFoundException("No member found for this round's payout order")

        val groupAccount = accountRepository.findById(ikimina.accountId).orElseThrow { IkiminaNoAccountException("Account not found") }
        val recipientAccount = accountRepository.findByUserIdAndType(recipient.userId, AccountType.MAIN)
            ?: throw IkiminaNoAccountException("Recipient has no account to receive the payout")
        val potAmount = ikimina.contributionAmount.multiply(BigDecimal(members.size))

        ledgerService.postLedgerTransaction(
            groupAccount.currency,
            listOf(
                LedgerLeg(groupAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, potAmount, "Ikimina payout (\"${ikimina.name}\", round ${ikimina.currentRound})"),
                LedgerLeg(recipientAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, potAmount, "Ikimina payout (\"${ikimina.name}\", round ${ikimina.currentRound})"),
            ),
        )
        recipient.hasReceivedPayout = true
        ikiminaMemberRepository.save(recipient)

        ikimina.currentRound += 1
        val justCompleted = members.all { it.hasReceivedPayout || it.id == recipient.id }
        if (justCompleted) {
            ikimina.status = IkiminaStatus.COMPLETED
        }
        val saved = ikiminaRepository.save(ikimina)
        notifyPayoutReceived(recipient.userId, ikimina.name, potAmount)
        if (justCompleted) {
            notifyCycleCompleted(members, ikimina.name)
        }
        return IkiminaPayoutResult(ikimina = saved, recipientUserId = recipient.userId, amount = potAmount)
    }

    // Real gap closed (itunda's own product-feel initiative, per SavingsService's
    // identical `notifyGoalCompleted` precedent) -- this service had ZERO notification
    // wiring at all before this, for either the recurring per-round payout (of every
    // notification type in this backend, "you received real money" is the one closest
    // to real Toss's own signature "money received" push -- see
    // PushNotificationService's own doc comment) or the cycle-completion event. Both
    // fixed in the same pass since they share the exact same after-commit deferral need
    // and copy tone. Non-critical, matches every other money-moving notify* in this
    // codebase: the real ledger transaction above already succeeded, a notification
    // failure must never surface as if it hadn't.
    private fun notifyPayoutReceived(recipientUserId: String, ikiminaName: String, amount: BigDecimal) {
        try {
            val title = "Ikimina payout received"
            val body = "You received $amount RWF from \"$ikiminaName\"."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = recipientUserId, type = "IKIMINA_PAYOUT_RECEIVED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"amount\":\"$amount\"}",
                ),
            )
            sendAfterCommit { pushNotificationService.sendToUser(recipientUserId, title, body, mapOf("amount" to amount.toString()), type = "IKIMINA_PAYOUT_RECEIVED") }
        } catch (e: Exception) {
            // Non-critical -- the real payout already succeeded.
            log.warn("Failed to notify user {} of Ikimina payout", recipientUserId, e)
        }
    }

    // Celebratory moment, matching `SavingsService.notifyGoalCompleted`'s real Toss-
    // sourced rationale (toss.tech/article/1st_interaction_designer's "finding the
    // hidden emotion" behind a completed real-money commitment) -- every member's own
    // recurring contributions over the full cycle, not just the last recipient's,
    // deserves the same acknowledgement, so this fans out to all members rather than
    // only the final payout recipient.
    private fun notifyCycleCompleted(members: List<IkiminaMember>, ikiminaName: String) {
        val title = "Ikimina cycle complete! 🎉"
        val body = "\"$ikiminaName\" has finished a full payout cycle -- every member has now received their turn."
        members.forEach { member ->
            try {
                notificationRepository.save(
                    Notification(
                        id = "notif_${UUID.randomUUID()}", userId = member.userId, type = "IKIMINA_CYCLE_COMPLETED",
                        title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{}",
                    ),
                )
                sendAfterCommit { pushNotificationService.sendToUser(member.userId, title, body, type = "IKIMINA_CYCLE_COMPLETED") }
            } catch (e: Exception) {
                // Non-critical, per-member -- one member's notification failure must
                // never block the others from being notified.
                log.warn("Failed to notify member {} of Ikimina cycle completion", member.userId, e)
            }
        }
    }

    // Shared after-commit deferral, identical pattern to
    // `SavingsService.sendGoalCompletedPushAfterCommit`/`P2pService.sendMoneyReceivedPushAfterCommit`:
    // both callers above fire from inside an already-open `@Transactional` method
    // (`contributeThisRound`/`checkAndTriggerPayout`), so the push must wait for that
    // transaction to actually commit rather than potentially misleading the user ahead
    // of a rollback.
    private fun sendAfterCommit(send: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    @Transactional
    fun checkAndTriggerPayout(userId: String, ikiminaId: String): IkiminaPayoutResult {
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        ikiminaMemberRepository.findByIkiminaIdAndUserId(ikiminaId, userId)
            ?: throw IkiminaNotFoundException("Ikimina not found")
        if (ikimina.status != IkiminaStatus.ACTIVE) throw IkiminaNotActiveException("This ikimina's cycle is not currently active")
        return attemptPayout(ikimina)
            ?: throw IkiminaContributionsIncompleteException("Not every member has contributed for round ${ikimina.currentRound} yet")
    }
}
