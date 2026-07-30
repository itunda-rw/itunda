package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.OverdraftAccount
import rw.itunda.core.domain.OverdraftAccountStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OverdraftAccountRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OverdraftAlreadyActiveException(message: String) : RuntimeException(message)
class OverdraftLimitInvalidException(message: String) : RuntimeException(message)
class OverdraftApplicationDeclinedException(message: String) : RuntimeException(message)
class OverdraftNotActiveException(message: String) : RuntimeException(message)
class OverdraftLimitExceededException(message: String) : RuntimeException(message)
class OverdraftInvalidAmountException(message: String) : RuntimeException(message)
class OverdraftNoWalletException(message: String) : RuntimeException(message)

/**
 * Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
 * OverdraftAccount.kt's own doc comment for the full sourced account and its own
 * structural distinction from the existing lump-sum `LoansService`.
 */
@Service
class OverdraftService(
    private val overdraftAccountRepository: OverdraftAccountRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val creditScoreService: CreditScoreService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        // Real underwriting reuses LoansService's own exact real gate (same score a
        // user sees at GET /api/v1/credit-score) -- one shared, not-duplicated
        // real risk model across every itunda credit product.
        const val MIN_SCORE_TO_QUALIFY = 400
        val MAX_CREDIT_LIMIT: BigDecimal = BigDecimal("500000")
        // A real overdraft carries more risk than a scheduled-repayment term loan
        // (flexible, unsecured, no fixed payoff date) -- itunda's own honest choice of
        // annual rate, priced above every LoanCatalog term-loan offer's own rate
        // (2.5-5.0), not a number claimed to be sourced from either bank's own real,
        // non-public pricing.
        const val ANNUAL_INTEREST_RATE = 8.0
    }

    @Transactional
    fun openOverdraft(userId: String, requestedLimit: BigDecimal): OverdraftAccount {
        if (requestedLimit <= BigDecimal.ZERO || requestedLimit > MAX_CREDIT_LIMIT) {
            throw OverdraftLimitInvalidException("Requested limit must be between 1 and $MAX_CREDIT_LIMIT RWF")
        }
        if (overdraftAccountRepository.findByUserIdAndStatus(userId, OverdraftAccountStatus.ACTIVE) != null) {
            throw OverdraftAlreadyActiveException("You already have a real active overdraft account")
        }

        val score = creditScoreService.computeScore(userId).score
        if (score < MIN_SCORE_TO_QUALIFY) {
            throw OverdraftApplicationDeclinedException("Credit score $score is below the minimum $MIN_SCORE_TO_QUALIFY required")
        }

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw OverdraftNoWalletException("No wallet found for this account")

        val account = overdraftAccountRepository.save(
            OverdraftAccount(
                id = "overdraft_${UUID.randomUUID()}", userId = userId, walletId = wallet.id,
                creditLimit = requestedLimit, interestRate = ANNUAL_INTEREST_RATE,
            ),
        )

        // Real Toss-style 자산 보호 알림 equivalent -- see LoansService.applyForLoan's own
        // doc comment for the full sourced rationale, same real notification here since
        // opening a credit line is exactly the kind of new-financial-product event that
        // discipline already covers.
        val title = "Overdraft account opened"
        val body = "A real ${requestedLimit} RWF overdraft line was just opened in your name. If this wasn't you, secure your account immediately."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "OVERDRAFT_OPENED",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"overdraftId\":\"${account.id}\"}",
            ),
        )
        sendOverdraftOpenedPushAfterCommit(userId, title, body, account.id)

        return account
    }

    /** Do not send a credit-product security alert until its account creation commits. */
    private fun sendOverdraftOpenedPushAfterCommit(userId: String, title: String, body: String, overdraftId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("overdraftId" to overdraftId)) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    @Transactional
    fun draw(userId: String, amount: BigDecimal): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) throw OverdraftInvalidAmountException("Amount must be greater than zero")
        val account = overdraftAccountRepository.findByUserIdAndStatus(userId, OverdraftAccountStatus.ACTIVE)
            ?: throw OverdraftNotActiveException("No active overdraft account found")

        val availableCredit = account.creditLimit.subtract(account.drawnBalance)
        if (amount > availableCredit) {
            throw OverdraftLimitExceededException("Requested amount exceeds your real available credit of $availableCredit RWF")
        }

        val wallet = walletRepository.findById(account.walletId).orElseThrow { OverdraftNoWalletException("Wallet not found") }
        val result = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Overdraft draw ${account.id}"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, amount, "Overdraft draw ${account.id}"),
            ),
        )

        account.drawnBalance = account.drawnBalance.add(amount)
        account.updatedAt = Instant.now()
        overdraftAccountRepository.save(account)

        return mapOf(
            "transactionId" to result.transactionId, "amount" to amount,
            "drawnBalance" to account.drawnBalance, "availableCredit" to account.creditLimit.subtract(account.drawnBalance),
        )
    }

    @Transactional
    fun repay(userId: String, amount: BigDecimal): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) throw OverdraftInvalidAmountException("Amount must be greater than zero")
        val account = overdraftAccountRepository.findByUserIdAndStatus(userId, OverdraftAccountStatus.ACTIVE)
            ?: throw OverdraftNotActiveException("No active overdraft account found")

        val repayAmount = amount.min(account.drawnBalance)
        val wallet = walletRepository.findById(account.walletId).orElseThrow { OverdraftNoWalletException("Wallet not found") }
        val result = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, repayAmount, "Overdraft repayment ${account.id}"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, repayAmount, "Overdraft repayment ${account.id}"),
            ),
        )

        // Deliberately, structurally distinct from LoansService.repayLoan: a real
        // repayment here never closes the account or changes its status, even at a
        // real zero drawn balance -- it just frees up real available credit
        // (creditLimit - drawnBalance) to draw again, the defining real "revolving"
        // behavior neither bank's own real term-loan product has.
        account.drawnBalance = account.drawnBalance.subtract(repayAmount)
        account.updatedAt = Instant.now()
        overdraftAccountRepository.save(account)
        val updatedWallet = walletRepository.findById(account.walletId).orElseThrow { OverdraftNoWalletException("Wallet not found") }

        return mapOf(
            "transactionId" to result.transactionId, "amount" to repayAmount,
            "drawnBalance" to account.drawnBalance, "availableCredit" to account.creditLimit.subtract(account.drawnBalance),
            "newBalance" to updatedWallet.balance,
        )
    }

    fun getMyOverdraft(userId: String): OverdraftAccount? = overdraftAccountRepository.findByUserIdAndStatus(userId, OverdraftAccountStatus.ACTIVE)

    // Real daily interest accrual -- see OverdraftInterestAccrualScheduler's own doc
    // comment. A real zero-balance account (fully repaid, still open) is honestly
    // skipped -- matches the sourced "pay interest only on what you actually used"
    // behavior exactly, not a fabricated minimum charge.
    fun getAccountsDueForAccrual(): List<OverdraftAccount> =
        overdraftAccountRepository.findByStatus(OverdraftAccountStatus.ACTIVE).filter { account ->
            account.drawnBalance > BigDecimal.ZERO &&
                (account.lastAccrualAt == null || Duration.between(account.lastAccrualAt, Instant.now()) >= Duration.ofHours(24))
        }

    @Transactional
    fun accrueInterest(account: OverdraftAccount) {
        val dailyRate = BigDecimal.valueOf(account.interestRate).divide(BigDecimal(100), 10, RoundingMode.HALF_UP).divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
        val interest = account.drawnBalance.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP)
        if (interest <= BigDecimal.ZERO) return

        ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, interest, "Overdraft interest accrual ${account.id}"),
                LedgerLeg("interest_income", LedgerAccountType.INTEREST_INCOME, LedgerDirection.CREDIT, interest, "Overdraft interest accrual ${account.id}"),
            ),
        )

        account.drawnBalance = account.drawnBalance.add(interest)
        account.lastAccrualAt = Instant.now()
        account.updatedAt = Instant.now()
        overdraftAccountRepository.save(account)
    }
}
