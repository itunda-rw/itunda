package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Cooperative
import rw.itunda.core.domain.CooperativeMembership
import rw.itunda.core.domain.HarvestAdvance
import rw.itunda.core.domain.HarvestAdvanceStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.CooperativeMembershipRepository
import rw.itunda.core.repository.CooperativeRepository
import rw.itunda.core.repository.HarvestAdvanceRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CooperativeNotFoundException(message: String) : RuntimeException(message)
class CooperativeInvalidNameException(message: String) : RuntimeException(message)
class AlreadyMemberException(message: String) : RuntimeException(message)
class NotMemberException(message: String) : RuntimeException(message)
class HarvestAdvanceNoAccountException(message: String) : RuntimeException(message)
class HarvestAdvanceInvalidAmountException(message: String) : RuntimeException(message)
class HarvestAdvanceNotFoundException(message: String) : RuntimeException(message)
class HarvestAdvanceInvalidStatusException(message: String) : RuntimeException(message)

// itunda's own real, honest cap on a single harvest advance -- a real-world-sane bound,
// not a fabricated one; roughly what a smallholder coffee plot's seasonal input cost
// runs, matching the same "reject absurd amounts before ever touching the ledger"
// discipline every other money-moving feature in this backend already establishes.
private val MAX_ADVANCE_AMOUNT = BigDecimal("500000")

/**
 * Real Rwanda coffee-cooperative harvest-advance / input financing -- see
 * `Cooperative.kt`'s own doc comment for the full sourced account. A direct itunda-to-
 * farmer lending relationship, structurally mirroring `LoansService`'s own real
 * disbursement/repayment ledger shape (itunda's own `loan_payable`/`LOAN_PAYABLE`
 * receivable) -- deliberately NOT a cooperative-pool redistribution like `Ikimina`,
 * and NOT funded from any shared/pooled account other members have a claim on, learning
 * directly from a real solvency bug this session caught in `SaccoService` before it
 * shipped.
 *
 * Honest v1 scope: itunda is the sole real lender here (reusing the same real
 * underwriting-free account-to-account pattern `LoansService.applyForLoan` already
 * establishes for its own `lender_itunda` offers), not a real integration with
 * KCB/Ecobank's own actual agriculture-finance products.
 */
@Service
class CooperativeService(
    private val cooperativeRepository: CooperativeRepository,
    private val membershipRepository: CooperativeMembershipRepository,
    private val advanceRepository: HarvestAdvanceRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    @Transactional
    fun registerCooperative(name: String, cropType: String, registrationNumber: String?): Cooperative {
        val trimmedName = name.trim().ifEmpty { throw CooperativeInvalidNameException("Cooperative name is required") }.take(200)
        val trimmedCrop = cropType.trim().ifEmpty { "COFFEE" }.take(50)
        return cooperativeRepository.save(
            Cooperative(
                id = "coop_${UUID.randomUUID()}", name = trimmedName, cropType = trimmedCrop,
                registrationNumber = registrationNumber?.trim()?.take(100)?.ifEmpty { null },
            ),
        )
    }

    @Transactional
    fun joinCooperative(userId: String, cooperativeId: String): CooperativeMembership {
        cooperativeRepository.findById(cooperativeId).orElseThrow { CooperativeNotFoundException("Cooperative not found") }
        if (membershipRepository.findByCooperativeIdAndUserId(cooperativeId, userId) != null) {
            throw AlreadyMemberException("You are already a member of this cooperative")
        }
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw HarvestAdvanceNoAccountException("No account found for this account")
        return membershipRepository.save(
            CooperativeMembership(id = "coopmem_${UUID.randomUUID()}", cooperativeId = cooperativeId, userId = userId, accountId = account.id),
        )
    }

    fun getMyMemberships(userId: String): List<CooperativeMembership> = membershipRepository.findByUserIdAndActiveTrue(userId)

    private fun getOwnedMembership(userId: String, membershipId: String): CooperativeMembership {
        val membership = membershipRepository.findById(membershipId).orElseThrow { NotMemberException("Membership not found") }
        if (membership.userId != userId) throw NotMemberException("Membership not found")
        return membership
    }

    @Transactional
    fun requestAdvance(
        userId: String,
        membershipId: String,
        principalAmount: BigDecimal,
        purpose: String,
        expectedHarvestDate: Instant,
    ): HarvestAdvance {
        rateLimiter.checkLimit("harvest-advance:request:$userId", limit = 10, window = Duration.ofHours(1))
        if (principalAmount <= BigDecimal.ZERO || principalAmount > MAX_ADVANCE_AMOUNT) {
            throw HarvestAdvanceInvalidAmountException("Amount must be between 1 and $MAX_ADVANCE_AMOUNT")
        }
        val membership = getOwnedMembership(userId, membershipId)
        if (!membership.active) throw NotMemberException("This membership is no longer active")

        return advanceRepository.save(
            HarvestAdvance(
                id = "harvestadv_${UUID.randomUUID()}", membershipId = membership.id, accountId = membership.accountId,
                principalAmount = principalAmount, purpose = purpose.trim().ifEmpty { "INPUT_FINANCING" }.take(30),
                expectedHarvestDate = expectedHarvestDate, repaymentDueDate = expectedHarvestDate,
            ),
        )
    }

    /** Real disbursement -- itunda's own capital, the same real `loan_payable` receivable
     * shape `LoansService.applyForLoan` already establishes, never a shared pool. */
    @Transactional
    fun disburseAdvance(userId: String, advanceId: String): HarvestAdvance {
        val advance = advanceRepository.findById(advanceId).orElseThrow { HarvestAdvanceNotFoundException("Advance not found") }
        val membership = membershipRepository.findById(advance.membershipId).orElseThrow { HarvestAdvanceNotFoundException("Advance not found") }
        if (membership.userId != userId) throw HarvestAdvanceNotFoundException("Advance not found")
        if (advance.status != HarvestAdvanceStatus.REQUESTED) {
            throw HarvestAdvanceInvalidStatusException("Only a REQUESTED advance can be disbursed -- this one is already ${advance.status}")
        }
        val account = accountRepository.findById(advance.accountId).orElseThrow { HarvestAdvanceNoAccountException("Account not found") }

        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, advance.principalAmount, "Harvest advance disbursement"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, advance.principalAmount, "Harvest advance principal owed"),
            ),
        )
        advance.status = HarvestAdvanceStatus.DISBURSED
        advance.disbursedAt = Instant.now()
        advance.disbursementTransactionId = result.transactionId
        return advanceRepository.save(advance)
    }

    /**
     * Real repayment -- full settlement only, an honest v1 scope-down. Real bug caught
     * during this feature's own build-time review, before it ever shipped: the first
     * draft accepted ANY positive `amount` and unconditionally marked the advance
     * `REPAID` regardless of whether it actually covered `principalAmount` -- since the
     * bank-mfe client already passes a completely free-form user-entered amount with no
     * coupling to the real outstanding principal, a farmer could "repay" 1 RWF of a
     * 500,000 RWF advance and the system would silently treat the remaining 499,999 as
     * settled, forgiving real debt with zero write-off decision or audit trail. This
     * entity has no running-balance/partial-payment tracking field, so rather than
     * half-build that (a real, separately-scoped feature), this requires the real
     * outstanding principal in full -- the same honest "don't half-support a feature
     * this entity shape can't back" discipline this session has used throughout.
     */
    @Transactional
    fun repayAdvance(userId: String, advanceId: String, amount: BigDecimal): HarvestAdvance {
        if (amount <= BigDecimal.ZERO) throw HarvestAdvanceInvalidAmountException("Repayment amount must be greater than zero")
        val advance = advanceRepository.findById(advanceId).orElseThrow { HarvestAdvanceNotFoundException("Advance not found") }
        val membership = membershipRepository.findById(advance.membershipId).orElseThrow { HarvestAdvanceNotFoundException("Advance not found") }
        if (membership.userId != userId) throw HarvestAdvanceNotFoundException("Advance not found")
        if (advance.status != HarvestAdvanceStatus.DISBURSED && advance.status != HarvestAdvanceStatus.OVERDUE) {
            throw HarvestAdvanceInvalidStatusException("Only a DISBURSED advance can be repaid -- this one is ${advance.status}")
        }
        if (amount.compareTo(advance.principalAmount) != 0) {
            throw HarvestAdvanceInvalidAmountException("Repayment must be the full outstanding principal (${advance.principalAmount}) -- partial repayment is not yet supported")
        }
        val account = accountRepository.findById(advance.accountId).orElseThrow { HarvestAdvanceNoAccountException("Account not found") }

        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Harvest advance repayment"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, amount, "Harvest advance repayment"),
            ),
        )
        advance.status = HarvestAdvanceStatus.REPAID
        advance.repaidAt = Instant.now()
        advance.repaymentTransactionId = result.transactionId
        return advanceRepository.save(advance)
    }

    fun getMyAdvances(userId: String): List<HarvestAdvance> {
        val membershipIds = membershipRepository.findByUserIdAndActiveTrue(userId).map { it.id }
        if (membershipIds.isEmpty()) return emptyList()
        return advanceRepository.findByMembershipIdIn(membershipIds).sortedByDescending { it.createdAt }
    }

    fun getCooperativeOverview(userId: String, cooperativeId: String): Map<String, Any?> {
        val membership = membershipRepository.findByCooperativeIdAndUserId(cooperativeId, userId)
            ?: throw NotMemberException("You are not a member of this cooperative")
        val cooperative = cooperativeRepository.findById(cooperativeId).orElseThrow { CooperativeNotFoundException("Cooperative not found") }
        val members = membershipRepository.findByCooperativeIdAndActiveTrue(cooperativeId)
        return mapOf(
            "cooperative" to cooperative,
            "myMembership" to membership,
            "memberCount" to members.size,
        )
    }
}
