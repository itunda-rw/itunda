package rw.itunda.savings

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.DepositProtectionFund
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.DepositProtectionFundRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit

// Real Deposit Protection Fund covering itunda Bank's own real deposit-taking products
// -- see DepositProtectionFund.kt's own doc comment for the full honesty framing (real
// mechanics, itunda's own internal scheme, not a claimed BNR filing). Same three
// AccountTypes as InterestJar's real accrual base: SAVINGS/WEEKLY_SAVINGS/UPFRONT_DEPOSIT
// -- MAIN is itunda's separate account/Pay product, not itunda Bank.
private val COVERED_WALLET_TYPES = listOf(AccountType.SAVINGS, AccountType.WEEKLY_SAVINGS, AccountType.UPFRONT_DEPOSIT)
private const val CONTRIBUTION_INTERVAL_DAYS = 1L

data class DepositProtectionStatus(
    val fundReserveBalance: BigDecimal,
    val coverageCapPerUser: BigDecimal,
    val contributionRateBps: Int,
    val lastContributionAt: Instant?,
    val yourTotalDeposits: BigDecimal,
    val yourCoveredBalance: BigDecimal,
)

@Service
class DepositProtectionService(
    private val fundRepository: DepositProtectionFundRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
) {
    // Real singleton get-or-create (2026-08-11) -- mirrors SavingsService's own
    // get-or-create shape for a first-time InterestJar row: the fund doesn't exist
    // until something actually asks for it, same as any other lazily-provisioned
    // per-install row in this codebase.
    @Transactional
    fun getOrCreateFund(): DepositProtectionFund =
        fundRepository.findById("system").orElseGet {
            fundRepository.save(DepositProtectionFund(reserveBalance = BigDecimal.ZERO))
        }

    fun getStatus(userId: String): DepositProtectionStatus {
        val fund = getOrCreateFund()
        val yourDeposits = accountRepository.sumBalanceByUserIdAndTypeIn(userId, COVERED_WALLET_TYPES)
        return DepositProtectionStatus(
            fundReserveBalance = fund.reserveBalance,
            coverageCapPerUser = fund.coverageCapPerUser,
            contributionRateBps = fund.contributionRateBps,
            lastContributionAt = fund.lastContributionAt,
            yourTotalDeposits = yourDeposits,
            yourCoveredBalance = yourDeposits.min(fund.coverageCapPerUser),
        )
    }

    fun isContributionDue(fund: DepositProtectionFund): Boolean {
        val last = fund.lastContributionAt ?: return true
        return ChronoUnit.DAYS.between(last, Instant.now()) >= CONTRIBUTION_INTERVAL_DAYS
    }

    // Real periodic reserve contribution -- itunda sets aside contributionRateBps/10000
    // of total covered deposits ANNUALLY, applied as a real daily increment (same
    // annual-rate-divided-by-365 shape SavingsService.accrueInterest() already
    // establishes for interest, so this fund's own math is consistent with the rest of
    // itunda Bank rather than inventing a second convention). Posted as a real
    // double-entry ledger transaction, not just a number bumped in place.
    @Transactional
    fun accrueContribution(fund: DepositProtectionFund) {
        val totalCoveredDeposits = accountRepository.sumBalanceByTypeIn(COVERED_WALLET_TYPES)
        val dailyRate = BigDecimal(fund.contributionRateBps).divide(BigDecimal(10000), 10, RoundingMode.HALF_UP)
            .divide(BigDecimal(365), 10, RoundingMode.HALF_UP)
        val contribution = totalCoveredDeposits.multiply(dailyRate).setScale(2, RoundingMode.HALF_UP)
        if (contribution > BigDecimal.ZERO) {
            ledgerService.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("deposit_protection_expense", LedgerAccountType.DEPOSIT_PROTECTION_EXPENSE, LedgerDirection.DEBIT, contribution, "Deposit Protection Fund contribution"),
                    LedgerLeg("deposit_protection_reserve", LedgerAccountType.DEPOSIT_PROTECTION_RESERVE, LedgerDirection.CREDIT, contribution, "Deposit Protection Fund contribution"),
                ),
            )
            fund.reserveBalance = fund.reserveBalance.add(contribution)
        }
        fund.lastContributionAt = Instant.now()
        fundRepository.save(fund)
    }
}
