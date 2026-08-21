package rw.itunda.account

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class InvalidMiniAccountDepositAmountException(message: String) : RuntimeException(message)
class MiniAccountBalanceCapExceededException(message: String) : RuntimeException(message)
class MiniAccountDailyLimitExceededException(message: String) : RuntimeException(message)
class MiniAccountMonthlyLimitExceededException(message: String) : RuntimeException(message)
class MiniAccountBirthDateRequiredException(message: String) : RuntimeException(message)
class MiniAccountAgeIneligibleException(message: String) : RuntimeException(message)

/**
 * Real KakaoBank 카카오뱅크 mini-style limited youth/starter account -- Card Gorilla's own
 * coverage (card-gorilla.com's "카카오뱅크에서 만든 청소년 전용 'mini 카드'의 장점은?" and
 * the launch/age-lowering pieces at heraldcorp.com/sisajournal-e.com) confirms the real
 * product's own three hard caps: a 500,000 KRW max balance, a 300,000 KRW daily deposit
 * limit, and a 2,000,000 KRW monthly deposit limit -- adopted directly as itunda's own
 * RWF thresholds (`MAX_BALANCE`/`DAILY_DEPOSIT_LIMIT`/`MONTHLY_DEPOSIT_LIMIT` below), the
 * same "reuse the sourced structure, itunda's own numbers" discipline this codebase
 * already established elsewhere (`AgentCommissionSchedule`'s bands, `HIGH_VALUE_THRESHOLD`)
 * rather than a fabricated currency conversion.
 *
 * **Real age-eligibility gate added 2026-07-28**, closing this row's own previously-named
 * gap: KakaoBank's real mini is 만 7세~18세 (age 7-18 inclusive) -- confirmed via
 * hankyung.com's and sedaily.com's own coverage of the real 2023 age-lowering ("가입 연령
 * 7∼18세로 확대"), the same articles' family this feature's other caps were sourced from.
 * `User.birthDate` (opt-in, set once via `AuthService.setBirthDate`) backs this: an
 * account with no birth date on file, or an age outside 7-18, real-422s opening a Mini
 * account rather than silently allowing it. **Still honestly out of scope**: the real
 * product's under-14 legal-guardian-consent requirement (a genuine guardian-approval
 * workflow this codebase has no concept of yet -- named here as a real, separate,
 * not-yet-started follow-up), and merchant-category restriction (blocking specific
 * merchant types needs a real merchant-category classification this codebase doesn't
 * have for card transactions yet). What's real here beyond the caps themselves: a
 * dedicated, real, capped account type any AGE-ELIGIBLE account can open and self-fund
 * from their own `MAIN` account -- the same real `WALLET`-to-`WALLET` ledger movement
 * `StocksService.fundInvestmentAccount` already proved live, just with real spending caps
 * a normal `MAIN`/`SAVINGS` account doesn't carry.
 */
@Service
class MiniAccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val userRepository: UserRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
) {
    companion object {
        val MAX_BALANCE: BigDecimal = BigDecimal("500000")
        val DAILY_DEPOSIT_LIMIT: BigDecimal = BigDecimal("300000")
        val MONTHLY_DEPOSIT_LIMIT: BigDecimal = BigDecimal("2000000")
        const val MIN_AGE: Int = 7
        const val MAX_AGE: Int = 18
        private val RWANDA_ZONE: ZoneId = ZoneId.of("Africa/Kigali")
    }

    /** Idempotent -- opening an already-open Mini account just returns the existing one,
     * same "the end state is what the caller actually wants" discipline
     * `KeywordAlertService.addAlert` already establishes, rather than a real 409. */
    @Transactional
    fun openMiniAccount(userId: String): Account {
        accountRepository.findByUserIdAndType(userId, AccountType.MINI)?.let { return it }
        val user = userRepository.findById(userId).orElseThrow { AccountNotFoundException("No account found for this account") }
        val birthDate = user.birthDate
            ?: throw MiniAccountBirthDateRequiredException("Set your birth date before opening a Mini account")
        // Age eligibility is a civil-date rule. Use the product's local time zone so a
        // customer is not temporarily treated as one year younger around a UTC date
        // boundary (which is especially visible from Rwanda's UTC+2 time zone).
        val age = Period.between(birthDate, LocalDate.now(RWANDA_ZONE)).years
        if (age < MIN_AGE || age > MAX_AGE) {
            throw MiniAccountAgeIneligibleException("Mini account is only available for ages $MIN_AGE-$MAX_AGE")
        }
        val mainAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw AccountNotFoundException("No account found for this account")

        // Real bug found live (2026-08-02): the plain `findByUserIdAndType(..., MINI)`
        // check at the top of this method reads-then-CREATES a brand-new row -- there's
        // no existing MINI row to put an `@Version` guard on yet, and `accounts` has no
        // unique constraint on (user_id, type) either, so two concurrent openMiniAccount
        // calls for the same user could both pass that check before either committed
        // and both create a real MINI account, silently doubling this user's effective
        // real 500,000 RWF balance cap across two rows. Fixed the same way this
        // codebase's own precedent for this exact shape works: lock a DIFFERENT
        // already-existing row (the user's own real MAIN account, which always exists
        // for a real registered user) via `findByIdForUpdate` to serialize the two
        // concurrent creates, then re-check MINI existence under that lock -- the
        // second caller's re-check now real-sees the first caller's already-committed
        // MINI row and idempotently returns it instead of creating a duplicate.
        accountRepository.findByIdForUpdate(mainAccount.id)
        accountRepository.findByUserIdAndType(userId, AccountType.MINI)?.let { return it }

        return accountRepository.save(
            Account(
                id = "account_${UUID.randomUUID()}", userId = userId, accountNumber = accountNumberGenerator.generate(2024100000L),
                accountName = "Mini Account", type = AccountType.MINI,
                balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO, currency = mainAccount.currency,
            ),
        )
    }

    /**
     * Real self-funded top-up from the caller's own `MAIN` account, enforcing all three
     * real Mini caps before ever posting the ledger movement -- a rejected deposit never
     * touches the ledger at all, same "validate everything, then post exactly once"
     * discipline every other real ledger-moving method in this codebase already follows.
     */
    @Transactional
    fun deposit(userId: String, amount: BigDecimal): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) throw InvalidMiniAccountDepositAmountException("Amount must be greater than zero")
        val mainAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw AccountNotFoundException("No account found for this account")
        val miniAccount = accountRepository.findByUserIdAndType(userId, AccountType.MINI)
            ?: throw AccountNotFoundException("No Mini account found for this account -- open one first")

        if (miniAccount.balance.add(amount) > MAX_BALANCE) {
            throw MiniAccountBalanceCapExceededException("This deposit would push the Mini account balance over the real $MAX_BALANCE RWF cap")
        }
        val today = LocalDate.now(ZoneOffset.UTC)
        val dayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        val todayDeposited = transactionRepository.sumAmountByToAccountIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
            miniAccount.id, TransactionType.TRANSFER, TransactionStatus.COMPLETED, dayStart,
        )
        if (todayDeposited.add(amount) > DAILY_DEPOSIT_LIMIT) {
            throw MiniAccountDailyLimitExceededException("This deposit would exceed the real $DAILY_DEPOSIT_LIMIT RWF daily deposit limit")
        }
        val monthStart = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val monthDeposited = transactionRepository.sumAmountByToAccountIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
            miniAccount.id, TransactionType.TRANSFER, TransactionStatus.COMPLETED, monthStart,
        )
        if (monthDeposited.add(amount) > MONTHLY_DEPOSIT_LIMIT) {
            throw MiniAccountMonthlyLimitExceededException("This deposit would exceed the real $MONTHLY_DEPOSIT_LIMIT RWF monthly deposit limit")
        }

        val result = ledgerService.postLedgerTransaction(
            mainAccount.currency,
            listOf(
                LedgerLeg(mainAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Transfer to Mini account"),
                LedgerLeg(miniAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Transfer to Mini account"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "MINI${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = userId, recipientId = userId, fromAccountId = mainAccount.id, toAccountId = miniAccount.id,
                amount = amount, fee = BigDecimal.ZERO, currency = mainAccount.currency, type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED, description = "Transfer to Mini account", channel = "MINI_WALLET_DEPOSIT",
                completedAt = Instant.now(),
            ),
        )
        return mapOf("id" to result.transactionId, "amount" to amount, "completedAt" to Instant.now().toString())
    }
}
