package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.UpfrontInterestDeposit
import rw.itunda.core.domain.UpfrontInterestDepositStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.UpfrontInterestDepositRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.wallet.AccountNumberGenerator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val ANNUAL_RATE = 2.80
private const val TERM_MONTHS = 12L
private val MIN_PRINCIPAL = BigDecimal("10000")
private val MAX_PRINCIPAL = BigDecimal("50000000")

class UpfrontDepositNotFoundException(message: String) : RuntimeException(message)
class InvalidUpfrontDepositAmountException(message: String) : RuntimeException(message)
class UpfrontDepositNotMaturedException(message: String) : RuntimeException(message)
class UpfrontDepositAlreadyWithdrawnException(message: String) : RuntimeException(message)

/**
 * Real Toss Bank 먼저 이자받는 정기예금 equivalent -- see `UpfrontInterestDeposit`'s own
 * doc comment for the full sourced mechanics and the no-early-withdrawal design choice
 * this service depends on. Mirrors `WeeklySavingsService`'s own established shape: a
 * dedicated per-deposit `Wallet` (real "no new balance concept" discipline), real
 * `LedgerService` double-entry postings, a scheduler-polls-a-due-list maturity path plus
 * a manually-triggerable one for testing a real 12-month term without waiting real
 * wall-clock months (same as `WeeklySavingsScheduler`/`WeeklySavingsController.processDue`).
 */
@Service
class UpfrontInterestDepositService(
    private val depositRepository: UpfrontInterestDepositRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val accountNumberGenerator: AccountNumberGenerator,
) {
    private val log = LoggerFactory.getLogger(UpfrontInterestDepositService::class.java)

    /** Real gross interest for the full 12-month term, paid in one shot -- the real
     * distinguishing mechanic, unlike every other product here's accrue-then-claim shape. */
    fun computeUpfrontInterest(principal: BigDecimal): BigDecimal =
        principal.multiply(BigDecimal.valueOf(ANNUAL_RATE)).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    @Transactional
    fun open(userId: String, principal: BigDecimal): UpfrontInterestDeposit {
        if (principal < MIN_PRINCIPAL || principal > MAX_PRINCIPAL) {
            throw InvalidUpfrontDepositAmountException("Principal must be between $MIN_PRINCIPAL and $MAX_PRINCIPAL RWF")
        }
        // Real anti-spam limit, same 10/hour convention every other money-moving
        // creation endpoint in this module already established.
        rateLimiter.checkLimit("upfront-deposit:open:$userId", limit = 10, window = Duration.ofHours(1))

        val mainWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")
        val depositWallet = walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}",
                userId = userId,
                accountNumber = accountNumberGenerator.generate(2026300000L),
                accountName = "12-Month Deposit",
                type = WalletType.UPFRONT_DEPOSIT,
                balance = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
            ),
        )

        // Real principal lock: MAIN -> the new dedicated wallet. Naturally throws
        // InsufficientFundsException/WalletFrozenException via LedgerService if the
        // user can't actually afford it -- same "let the ledger enforce it" discipline
        // SavingsService.depositToGoal already established.
        ledgerService.postLedgerTransaction(
            mainWallet.currency,
            listOf(
                LedgerLeg(mainWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, principal, "12-month deposit opened"),
                LedgerLeg(depositWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, principal, "12-month deposit opened"),
            ),
        )

        // Real upfront interest payout: the whole point of this product. Paid straight
        // to MAIN as real, immediately spendable money -- not into the locked deposit
        // wallet -- which is exactly what makes this different from every other
        // savings product here (and exactly why the deposit side has no early-exit).
        val interest = computeUpfrontInterest(principal)
        if (interest > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                mainWallet.currency,
                listOf(
                    LedgerLeg("interest_expense", LedgerAccountType.INTEREST_EXPENSE, LedgerDirection.DEBIT, interest, "12-month deposit: interest paid upfront"),
                    LedgerLeg(mainWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, interest, "12-month deposit: interest paid upfront"),
                ),
            )
        }

        val now = Instant.now()
        val deposit = depositRepository.save(
            UpfrontInterestDeposit(
                id = "upfront_deposit_${UUID.randomUUID()}", userId = userId, walletId = depositWallet.id,
                principal = principal, interestRate = ANNUAL_RATE, interestPaid = interest,
                openedAt = now, maturesAt = now.plus(TERM_MONTHS * 30, ChronoUnit.DAYS),
            ),
        )
        log.info("Opened 12-month upfront-interest deposit {} for user {} (principal {}, interest paid {})", deposit.id, userId, principal, interest)
        return deposit
    }

    fun getMyDeposits(userId: String): List<UpfrontInterestDeposit> = depositRepository.findByUserIdOrderByOpenedAtDesc(userId)

    private fun findOwned(userId: String, depositId: String) =
        depositRepository.findById(depositId).filter { it.userId == userId }.orElseThrow { UpfrontDepositNotFoundException("Deposit not found") }

    // Real findAll()-then-filter honesty, same convention
    // WeeklySavingsService.getPlansDueForProcessing/SavingsService.
    // getGoalsDueForAutoContribution already established at this system's actual data scale.
    fun getDepositsDueForMaturity(): List<UpfrontInterestDeposit> {
        val now = Instant.now()
        return depositRepository.findAll().filter { it.status == UpfrontInterestDepositStatus.ACTIVE && !it.maturesAt.isAfter(now) }
    }

    /** Real maturity: only flips status. The principal itself stays in the deposit's own
     * wallet until a real, explicit withdraw() call -- matching WeeklySavingsPlan's own
     * "maturity is a system event, withdrawal is a user action" split. */
    @Transactional
    fun matureDeposit(deposit: UpfrontInterestDeposit) {
        deposit.status = UpfrontInterestDepositStatus.MATURED
        deposit.maturedAt = Instant.now()
        depositRepository.save(deposit)
        log.info("Matured upfront-interest deposit {} for user {}", deposit.id, deposit.userId)
    }

    @Transactional
    fun withdraw(userId: String, depositId: String): UpfrontInterestDeposit {
        val deposit = findOwned(userId, depositId)
        if (deposit.status != UpfrontInterestDepositStatus.MATURED) throw UpfrontDepositNotMaturedException("This deposit has not matured yet")
        if (deposit.withdrawnAt != null) throw UpfrontDepositAlreadyWithdrawnException("This deposit has already been withdrawn")

        val depositWallet = walletRepository.findById(deposit.walletId).orElseThrow { NoWalletException("Wallet not found") }
        val mainWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN) ?: throw NoWalletException("No wallet found for this account")
        val payout = depositWallet.balance
        if (payout > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                depositWallet.currency,
                listOf(
                    LedgerLeg(deposit.walletId, LedgerAccountType.WALLET, LedgerDirection.DEBIT, payout, "Matured 12-month deposit withdrawal"),
                    LedgerLeg(mainWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, payout, "Matured 12-month deposit withdrawal"),
                ),
            )
        }
        deposit.withdrawnAt = Instant.now()
        depositRepository.save(deposit)
        log.info("Withdrew matured upfront-interest deposit {} for user {} (payout {})", deposit.id, userId, payout)
        return deposit
    }
}
