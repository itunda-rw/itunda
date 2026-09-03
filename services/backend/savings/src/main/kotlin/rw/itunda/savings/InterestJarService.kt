package rw.itunda.savings

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val INTEREST_ACCRUAL_INTERVAL_DAYS = 1L

/**
 * Extracted out of SavingsService.kt (2026-09-03, first crossing of the 500-line
 * file-size guideline) -- the real gap this used to be bundled with (Savings Goals) is
 * a distinct product from the Interest Jar's real Toss Bank passbook-interest semantics
 * (see accrueInterest's own doc comment), and neither shares state with the other beyond
 * AccountRepository, which both already inject independently.
 */
@Service
class InterestJarService(
    private val accountRepository: AccountRepository,
    private val interestJarRepository: InterestJarRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {
    fun getInterestJar(userId: String) = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }

    fun getInterestJarTransactions(userId: String): List<BucketTransactionDto> {
        val jar = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(jar.accountId).map { it.toBucketTransactionDto() }
    }

    @Transactional
    fun claimInterest(userId: String): Map<String, Any?> {
        val jar = interestJarRepository.findById(userId).orElseThrow { NoInterestJarException("No interest jar found for this account") }
        if (jar.earnedThisMonth <= BigDecimal.ZERO) throw NoInterestAvailableException("No interest available to claim")

        // Real fix (2026-08-11): interest now auto-credits the real account balance
        // the instant it accrues (see accrueInterest's own doc comment, matching real
        // Toss Bank passbook interest -- "통장 이자" posts directly, no manual claim
        // step exists in a real bank). Posting a SECOND ledger credit here for the
        // same already-arrived money would be a real double-credit bug -- this now
        // just clears the running "earned this month" display counter, the same
        // "mark as seen" shape a notification-read flag has, not a real second
        // transfer. The account balance genuinely doesn't change here anymore.
        val claimed = jar.earnedThisMonth
        val account = accountRepository.findById(jar.accountId).orElseThrow { NoAccountException("Account not found") }
        jar.earnedThisMonth = BigDecimal.ZERO
        jar.lastPaidAt = Instant.now()
        interestJarRepository.save(jar)

        return mapOf("claimed" to claimed, "newBalance" to account.balance)
    }

    // Real daily interest accrual (2026-07-20) -- found live: earnedThisMonth/earnedTotal
    // were only ever written by claimInterest (reading or zeroing, never incrementing) and
    // by SeedDataRunner's one hardcoded demo row. Every real jar's balance sat frozen at
    // zero forever -- claimInterest always 404'd/NoInterestAvailable for a real account.
    // Same findAll()-then-filter honesty as SavingsService.getGoalsDueForAutoContribution --
    // real data scale here doesn't yet justify an indexed query.
    fun getJarsDueForAccrual(): List<InterestJar> {
        val now = Instant.now()
        return interestJarRepository.findAll().filter { it.nextPayoutAt.isBefore(now) || it.nextPayoutAt == now }
    }

    // Real Toss Bank passbook interest semantics (user-provided screenshots,
    // 2026-08-11 -- "통장 이자" +36원/+19원 posting directly into the real transaction
    // history the moment it accrues): interest now credits the real account balance
    // and creates a real Transaction row on EVERY accrual, not just a display-only
    // `earnedThisMonth` counter requiring a separate manual claim. This replaces the
    // previous "accrue into a jar, then claim into the account" two-step flow --
    // real bank passbook interest has no manual claim step at all, it just appears.
    // `earnedThisMonth`/`earnedTotal` are kept as running display totals of interest
    // ALREADY credited (not pending), still useful for the Interest jar summary card.
    // jar.balance stays a synced display cache of the real account balance, never the
    // source of truth. nextPayoutAt advances by exactly one real day (not "now + 1
    // day") so a scheduler catch-up after downtime doesn't silently shrink the
    // accrual window.
    @Transactional
    fun accrueInterest(jar: InterestJar) {
        val account = accountRepository.findById(jar.accountId).orElse(null) ?: return
        val dailyRate = BigDecimal.valueOf(jar.rate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
        val accrued = account.balance.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP)
        // Real celebratory moment (2026-08-23) -- see SavingsService.notifyGoalCompleted/
        // notifyLoanPaidOff's own doc comments for the sourced Toss rationale this
        // continues. Deliberately a ONE-TIME signal, not fired on every accrual --
        // this scheduler runs daily per jar, so a push on every single accrual would be
        // real notification fatigue, exactly what the interaction-philosophy research
        // this whole initiative is grounded in (toss.tech/article/interaction) names as
        // a reason a design gets discarded, not shipped. earnedTotal is a lifetime
        // running total that's never reset (unlike earnedThisMonth), so "was zero,
        // about to become positive" is a safe, genuinely once-ever signal per jar.
        val isFirstAccrualEver = jar.earnedTotal == BigDecimal.ZERO && accrued > BigDecimal.ZERO
        if (accrued > BigDecimal.ZERO) {
            val ledger = ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, accrued, "Savings interest"),
                    LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, accrued, "Savings interest"),
                ),
            )
            transactionRepository.save(
                Transaction(
                    id = ledger.transactionId,
                    referenceNumber = "INTEREST${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                    senderId = "system_interest",
                    recipientId = jar.userId,
                    toAccountId = account.id,
                    amount = accrued,
                    fee = BigDecimal.ZERO,
                    currency = "RWF",
                    type = TransactionType.INTEREST,
                    status = TransactionStatus.COMPLETED,
                    description = "Savings interest",
                    completedAt = Instant.now(),
                ),
            )
            jar.earnedThisMonth = jar.earnedThisMonth.add(accrued)
            jar.earnedTotal = jar.earnedTotal.add(accrued)
        }
        val updatedAccount = accountRepository.findById(jar.accountId).orElse(account)
        jar.balance = updatedAccount.balance
        jar.nextPayoutAt = jar.nextPayoutAt.plus(INTEREST_ACCRUAL_INTERVAL_DAYS, ChronoUnit.DAYS)
        val saved = interestJarRepository.save(jar)
        if (isFirstAccrualEver) notifyFirstInterestAccrual(saved, accrued)
    }

    private fun notifyFirstInterestAccrual(jar: InterestJar, accrued: BigDecimal) {
        try {
            val title = "Your money started earning 🎉"
            val body = "You just earned your first $accrued RWF in savings interest -- it'll keep adding up automatically."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = jar.userId, type = "FIRST_INTEREST_ACCRUAL",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"accountId\":\"${jar.accountId}\"}",
                ),
            )
            sendFirstInterestAccrualPushAfterCommit(jar.userId, title, body, jar.accountId)
        } catch (e: Exception) {
            // Non-critical -- the real interest accrual already succeeded.
        }
    }

    private fun sendFirstInterestAccrualPushAfterCommit(userId: String, title: String, body: String, accountId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("accountId" to accountId), type = "FIRST_INTEREST_ACCRUAL") }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
