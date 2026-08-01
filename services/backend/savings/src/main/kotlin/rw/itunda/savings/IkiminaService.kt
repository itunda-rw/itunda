package rw.itunda.savings

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Ikimina
import rw.itunda.core.domain.IkiminaContribution
import rw.itunda.core.domain.IkiminaMember
import rw.itunda.core.domain.IkiminaStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.IkiminaContributionRepository
import rw.itunda.core.repository.IkiminaMemberRepository
import rw.itunda.core.repository.IkiminaRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class IkiminaNotFoundException(message: String) : RuntimeException(message)
class IkiminaNotOrganizerException(message: String) : RuntimeException(message)
class IkiminaNotMemberException(message: String) : RuntimeException(message)
class IkiminaMemberNotFoundException(message: String) : RuntimeException(message)
class IkiminaAlreadyMemberException(message: String) : RuntimeException(message)
class IkiminaFullException(message: String) : RuntimeException(message)
class IkiminaNoWalletException(message: String) : RuntimeException(message)
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

private const val MIN_MEMBERS_TO_START = 2
private const val MAX_MEMBERS = 15

/**
 * Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See
 * Ikimina.kt's own doc comment for the full sourced account (real ROSCA literature +
 * a real existing Rwandan startup, smartikimina.rw, already digitizing this exact
 * mechanic). Distinct from GroupAccountService (Kakao Bank 모임통장): that feature has
 * one permanent owner and no rotation; this one has a rotating payout recipient and no
 * permanent single beneficiary. Reuses the exact real ledger-movement shape every
 * other money-moving feature in this backend already uses -- a real Wallet(type=GROUP)
 * per ikimina, contributions/payouts are real WALLET-to-WALLET ledger transactions.
 */
@Service
class IkiminaService(
    private val ikiminaRepository: IkiminaRepository,
    private val ikiminaMemberRepository: IkiminaMemberRepository,
    private val ikiminaContributionRepository: IkiminaContributionRepository,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    private fun generateAccountNumber(): String = (2024200000L + (Math.random() * 900000).toLong()).toString()

    @Transactional
    fun createIkimina(organizerId: String, name: String, contributionAmount: BigDecimal, cycleFrequencyDays: Int, memberCap: Int): Ikimina {
        rateLimiter.checkLimit("ikimina:create:$organizerId", limit = 10, window = Duration.ofHours(1))
        require(name.trim().isNotEmpty()) { "Name is required" }
        require(contributionAmount > BigDecimal.ZERO) { "Contribution amount must be greater than zero" }
        require(cycleFrequencyDays > 0) { "Cycle frequency must be greater than zero days" }
        require(memberCap in MIN_MEMBERS_TO_START..MAX_MEMBERS) { "Member cap must be between $MIN_MEMBERS_TO_START and $MAX_MEMBERS" }
        userRepository.findById(organizerId).orElseThrow { IkiminaNotFoundException("Account not found") }

        val wallet = walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = organizerId,
                accountNumber = generateAccountNumber(),
                accountName = "$name (Ikimina)",
                type = WalletType.GROUP,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )
        val ikimina = ikiminaRepository.save(
            Ikimina(
                id = "ikimina_${UUID.randomUUID()}", name = name.trim(), organizerId = organizerId, walletId = wallet.id,
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
            ?: throw IkiminaNotMemberException("You are not a member of this ikimina")
        val wallet = walletRepository.findById(ikimina.walletId).orElseThrow { IkiminaNoWalletException("Wallet not found") }

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
        return IkiminaView(ikimina = ikimina, balance = wallet.balance, members = memberViews, currentRoundContributions = contributionStatus)
    }

    @Transactional
    fun inviteMember(organizerId: String, ikiminaId: String, phoneNumber: String): IkiminaMemberView {
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        if (ikimina.organizerId != organizerId) throw IkiminaNotOrganizerException("Only the ikimina's organizer can invite members")
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
        if (ikimina.organizerId != organizerId) throw IkiminaNotOrganizerException("Only the ikimina's organizer can start the cycle")
        if (ikimina.status != IkiminaStatus.FORMING) throw IkiminaNotFormingException("This ikimina's cycle has already started")
        val memberCount = ikiminaMemberRepository.countByIkiminaId(ikiminaId)
        if (memberCount < MIN_MEMBERS_TO_START) {
            throw IkiminaTooFewMembersException("At least $MIN_MEMBERS_TO_START members are required to start a cycle")
        }
        ikimina.status = IkiminaStatus.ACTIVE
        return ikiminaRepository.save(ikimina)
    }

    @Transactional
    fun contributeThisRound(userId: String, ikiminaId: String): Ikimina {
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        if (ikimina.status != IkiminaStatus.ACTIVE) throw IkiminaNotActiveException("This ikimina's cycle is not currently active")
        val member = ikiminaMemberRepository.findByIkiminaIdAndUserId(ikiminaId, userId)
            ?: throw IkiminaNotMemberException("You are not a member of this ikimina")
        if (ikiminaContributionRepository.findByIkiminaIdAndMemberIdAndRound(ikiminaId, member.id, ikimina.currentRound) != null) {
            throw IkiminaAlreadyContributedException("You have already contributed for round ${ikimina.currentRound}")
        }

        val sourceWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw IkiminaNoWalletException("No wallet found for this account")
        val groupWallet = walletRepository.findById(ikimina.walletId).orElseThrow { IkiminaNoWalletException("Wallet not found") }

        ledgerService.postLedgerTransaction(
            sourceWallet.currency,
            listOf(
                LedgerLeg(sourceWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, ikimina.contributionAmount, "Ikimina contribution (\"${ikimina.name}\", round ${ikimina.currentRound})"),
                LedgerLeg(groupWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, ikimina.contributionAmount, "Ikimina contribution (\"${ikimina.name}\", round ${ikimina.currentRound})"),
            ),
        )
        // Real DB unique constraint on (ikimina_id, member_id, round) backs this --
        // the AlreadyContributed check above is a fast-path, not the sole guard.
        ikiminaContributionRepository.save(
            IkiminaContribution(id = "ikiminacontrib_${UUID.randomUUID()}", ikiminaId = ikiminaId, memberId = member.id, round = ikimina.currentRound, amount = ikimina.contributionAmount),
        )
        return ikimina
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
     * session's Bike/Parking/Knowledge/SupportTicket fixes all rely on.
     */
    @Transactional
    fun checkAndTriggerPayout(userId: String, ikiminaId: String): IkiminaPayoutResult {
        val ikimina = ikiminaRepository.findById(ikiminaId).orElseThrow { IkiminaNotFoundException("Ikimina not found") }
        ikiminaMemberRepository.findByIkiminaIdAndUserId(ikiminaId, userId)
            ?: throw IkiminaNotMemberException("You are not a member of this ikimina")
        if (ikimina.status != IkiminaStatus.ACTIVE) throw IkiminaNotActiveException("This ikimina's cycle is not currently active")

        val members = ikiminaMemberRepository.findByIkiminaId(ikiminaId)
        val contributedMemberIds = ikiminaContributionRepository.findByIkiminaIdAndRound(ikiminaId, ikimina.currentRound).map { it.memberId }.toSet()
        if (!members.all { it.id in contributedMemberIds }) {
            throw IkiminaContributionsIncompleteException("Not every member has contributed for round ${ikimina.currentRound} yet")
        }

        val recipientPayoutOrder = ((ikimina.currentRound - 1) % members.size) + 1
        val recipient = ikiminaMemberRepository.findByIkiminaIdAndPayoutOrder(ikiminaId, recipientPayoutOrder)
            ?: throw IkiminaMemberNotFoundException("No member found for this round's payout order")

        val groupWallet = walletRepository.findById(ikimina.walletId).orElseThrow { IkiminaNoWalletException("Wallet not found") }
        val recipientWallet = walletRepository.findByUserIdAndType(recipient.userId, WalletType.MAIN)
            ?: throw IkiminaNoWalletException("Recipient has no wallet to receive the payout")
        val potAmount = ikimina.contributionAmount.multiply(BigDecimal(members.size))

        ledgerService.postLedgerTransaction(
            groupWallet.currency,
            listOf(
                LedgerLeg(groupWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, potAmount, "Ikimina payout (\"${ikimina.name}\", round ${ikimina.currentRound})"),
                LedgerLeg(recipientWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, potAmount, "Ikimina payout (\"${ikimina.name}\", round ${ikimina.currentRound})"),
            ),
        )
        recipient.hasReceivedPayout = true
        ikiminaMemberRepository.save(recipient)

        ikimina.currentRound += 1
        if (members.all { it.hasReceivedPayout || it.id == recipient.id }) {
            ikimina.status = IkiminaStatus.COMPLETED
        }
        val saved = ikiminaRepository.save(ikimina)
        return IkiminaPayoutResult(ikimina = saved, recipientUserId = recipient.userId, amount = potAmount)
    }
}
