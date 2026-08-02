package rw.itunda.wallet

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class InvalidMiniWalletDepositAmountException(message: String) : RuntimeException(message)
class MiniWalletBalanceCapExceededException(message: String) : RuntimeException(message)
class MiniWalletDailyLimitExceededException(message: String) : RuntimeException(message)
class MiniWalletMonthlyLimitExceededException(message: String) : RuntimeException(message)
class MiniWalletBirthDateRequiredException(message: String) : RuntimeException(message)
class MiniWalletAgeIneligibleException(message: String) : RuntimeException(message)

/**
 * Real KakaoBank 카카오뱅크 mini-style limited youth/starter wallet -- Card Gorilla's own
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
 * wallet rather than silently allowing it. **Still honestly out of scope**: the real
 * product's under-14 legal-guardian-consent requirement (a genuine guardian-approval
 * workflow this codebase has no concept of yet -- named here as a real, separate,
 * not-yet-started follow-up), and merchant-category restriction (blocking specific
 * merchant types needs a real merchant-category classification this codebase doesn't
 * have for card transactions yet). What's real here beyond the caps themselves: a
 * dedicated, real, capped wallet type any AGE-ELIGIBLE account can open and self-fund
 * from their own `MAIN` wallet -- the same real `WALLET`-to-`WALLET` ledger movement
 * `StocksService.fundInvestmentWallet` already proved live, just with real spending caps
 * a normal `MAIN`/`SAVINGS` wallet doesn't carry.
 */
@Service
class MiniWalletService(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val ledgerService: LedgerService,
    private val userRepository: UserRepository,
) {
    companion object {
        val MAX_BALANCE: BigDecimal = BigDecimal("500000")
        val DAILY_DEPOSIT_LIMIT: BigDecimal = BigDecimal("300000")
        val MONTHLY_DEPOSIT_LIMIT: BigDecimal = BigDecimal("2000000")
        const val MIN_AGE: Int = 7
        const val MAX_AGE: Int = 18
        private val RWANDA_ZONE: ZoneId = ZoneId.of("Africa/Kigali")
    }

    // No collision-avoidance loop, same accepted-risk precedent
    // AuthService.generateAccountNumber already establishes for this codebase.
    private fun generateAccountNumber(): String = (2024100000L + (Math.random() * 900000).toLong()).toString()

    /** Idempotent -- opening an already-open Mini wallet just returns the existing one,
     * same "the end state is what the caller actually wants" discipline
     * `KeywordAlertService.addAlert` already establishes, rather than a real 409. */
    @Transactional
    fun openMiniWallet(userId: String): Wallet {
        walletRepository.findByUserIdAndType(userId, WalletType.MINI)?.let { return it }
        val user = userRepository.findById(userId).orElseThrow { WalletNotFoundException("No wallet found for this account") }
        val birthDate = user.birthDate
            ?: throw MiniWalletBirthDateRequiredException("Set your birth date before opening a Mini wallet")
        // Age eligibility is a civil-date rule. Use the product's local time zone so a
        // customer is not temporarily treated as one year younger around a UTC date
        // boundary (which is especially visible from Rwanda's UTC+2 time zone).
        val age = Period.between(birthDate, LocalDate.now(RWANDA_ZONE)).years
        if (age < MIN_AGE || age > MAX_AGE) {
            throw MiniWalletAgeIneligibleException("Mini wallet is only available for ages $MIN_AGE-$MAX_AGE")
        }
        val mainWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw WalletNotFoundException("No wallet found for this account")

        // Real bug found live (2026-08-02): the plain `findByUserIdAndType(..., MINI)`
        // check at the top of this method reads-then-CREATES a brand-new row -- there's
        // no existing MINI row to put an `@Version` guard on yet, and `wallets` has no
        // unique constraint on (user_id, type) either, so two concurrent openMiniWallet
        // calls for the same user could both pass that check before either committed
        // and both create a real MINI wallet, silently doubling this user's effective
        // real 500,000 RWF balance cap across two rows. Fixed the same way this
        // codebase's own precedent for this exact shape works: lock a DIFFERENT
        // already-existing row (the user's own real MAIN wallet, which always exists
        // for a real registered user) via `findByIdForUpdate` to serialize the two
        // concurrent creates, then re-check MINI existence under that lock -- the
        // second caller's re-check now real-sees the first caller's already-committed
        // MINI row and idempotently returns it instead of creating a duplicate.
        walletRepository.findByIdForUpdate(mainWallet.id)
        walletRepository.findByUserIdAndType(userId, WalletType.MINI)?.let { return it }

        return walletRepository.save(
            Wallet(
                id = "wallet_${UUID.randomUUID()}", userId = userId, accountNumber = generateAccountNumber(),
                accountName = "Mini Account", type = WalletType.MINI,
                balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO, currency = mainWallet.currency,
            ),
        )
    }

    /**
     * Real self-funded top-up from the caller's own `MAIN` wallet, enforcing all three
     * real Mini caps before ever posting the ledger movement -- a rejected deposit never
     * touches the ledger at all, same "validate everything, then post exactly once"
     * discipline every other real ledger-moving method in this codebase already follows.
     */
    @Transactional
    fun deposit(userId: String, amount: BigDecimal): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) throw InvalidMiniWalletDepositAmountException("Amount must be greater than zero")
        val mainWallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw WalletNotFoundException("No wallet found for this account")
        val miniWallet = walletRepository.findByUserIdAndType(userId, WalletType.MINI)
            ?: throw WalletNotFoundException("No Mini wallet found for this account -- open one first")

        if (miniWallet.balance.add(amount) > MAX_BALANCE) {
            throw MiniWalletBalanceCapExceededException("This deposit would push the Mini wallet balance over the real $MAX_BALANCE RWF cap")
        }
        val today = LocalDate.now(ZoneOffset.UTC)
        val dayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        val todayDeposited = transactionRepository.sumAmountByToWalletIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
            miniWallet.id, TransactionType.TRANSFER, TransactionStatus.COMPLETED, dayStart,
        )
        if (todayDeposited.add(amount) > DAILY_DEPOSIT_LIMIT) {
            throw MiniWalletDailyLimitExceededException("This deposit would exceed the real $DAILY_DEPOSIT_LIMIT RWF daily deposit limit")
        }
        val monthStart = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val monthDeposited = transactionRepository.sumAmountByToWalletIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
            miniWallet.id, TransactionType.TRANSFER, TransactionStatus.COMPLETED, monthStart,
        )
        if (monthDeposited.add(amount) > MONTHLY_DEPOSIT_LIMIT) {
            throw MiniWalletMonthlyLimitExceededException("This deposit would exceed the real $MONTHLY_DEPOSIT_LIMIT RWF monthly deposit limit")
        }

        val result = ledgerService.postLedgerTransaction(
            mainWallet.currency,
            listOf(
                LedgerLeg(mainWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Transfer to Mini account"),
                LedgerLeg(miniWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Transfer to Mini account"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "MINI${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = userId, recipientId = userId, fromWalletId = mainWallet.id, toWalletId = miniWallet.id,
                amount = amount, fee = BigDecimal.ZERO, currency = mainWallet.currency, type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED, description = "Transfer to Mini account", channel = "MINI_WALLET_DEPOSIT",
                completedAt = Instant.now(),
            ),
        )
        return mapOf("id" to result.transactionId, "amount" to amount, "completedAt" to Instant.now().toString())
    }
}
