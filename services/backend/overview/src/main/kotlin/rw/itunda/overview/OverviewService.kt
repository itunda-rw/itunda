package rw.itunda.overview

import org.springframework.stereotype.Service
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.NetWorthSnapshot
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.NetWorthSnapshotRepository
import rw.itunda.core.repository.RewardClaimRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.VehicleRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AccountSummary(val id: String, val type: String, val name: String, val balance: BigDecimal, val currency: String)
data class SavingsSummary(val totalSaved: BigDecimal, val goalCount: Int)
data class LoansSummary(val totalOutstanding: BigDecimal, val activeCount: Int)
data class InvestmentsSummary(val totalCostBasis: BigDecimal, val holdingCount: Int)
data class InsuranceSummary(val activePolicyCount: Int, val totalMonthlyPremium: BigDecimal)
// demoBalance (2026-07-17) is a real, honestly-labeled demo value -- see
// LinkedAccount.kt's own doc comment. isDemoBalance is always true when present,
// carried explicitly (not left for a client to infer) so no UI can accidentally
// present it as a real live balance.
data class LinkedAccountSummary(
    val id: String, val provider: String, val maskedAccountNumber: String, val status: String,
    val demoBalance: BigDecimal?, val demoBalanceCurrency: String?, val isDemoBalance: Boolean,
)
// Real "My assets" tab-by-tab redesign (2026-08-27, direct user reference: 3 real
// Toss "총자산" screenshots, "this is how my asset screen should look like"). Each of
// these 4 mirrors Toss's own real-card-vs-teaser-card pattern for a category
// OverviewService didn't previously surface at all -- see this file's own git history
// / PR description for the full sourced account of why each is shaped the way it is.
data class CardsSummary(val hasCard: Boolean, val last4: String?, val design: String?, val frozen: Boolean?)
// Real count + real purchase-price total ONLY -- the live depreciated market value is
// VehicleValuationService's own private algorithm, in the :vehicle module, which
// :overview deliberately does not depend on (see this class's own header comment).
// Duplicating that math here would risk drifting out of sync with the real one; the
// client routes to the real Vehicle screen for the actual valuation number instead.
data class VehicleSummary(val vehicleCount: Int, val totalPurchasePrice: BigDecimal)
// Real count + real total of this user's own RRA tax-biller payments (b8/b9/b10,
// BillsCatalog.providers) -- always a real card, even at zero, matching Toss's own
// "₩0 / 8월 환급액" always-populated Tax tab (no "link a tax account" step exists;
// paying a real RRA bill through Bills IS the real activity this reflects).
data class TaxSummary(val totalPaid: BigDecimal, val paymentCount: Int)
// rewardsTotal mirrors RewardsService.getTasks' own real fold over RewardClaim rows
// exactly (not a new algorithm, just reproduced via :core to respect the same
// module-boundary :overview already holds everywhere else in this file).
// payMoneyBalance is the user's real AccountType.PAY balance -- itunda's own real
// "itunda Pay money" analog to Toss Pay Money, already present in `accounts` above,
// just also broken out here since Toss's own Points tab shows it alongside Points.
data class PointsSummary(val rewardsTotal: BigDecimal, val payMoneyBalance: BigDecimal)

// Real Toss "자산 변화" (asset change over time) reference -- see NetWorthSnapshot's
// own doc comment for why `liquidTotal` deliberately excludes savings/investments/
// loans. `month` is an ISO "yyyy-MM" label the client formats for display, matching
// this codebase's own convention of sending a raw, unambiguous value rather than a
// pre-localized string (see e.g. SubscriptionDetectionService's `nextExpectedAt`).
data class NetWorthHistoryPoint(val month: String, val liquidTotal: BigDecimal)

data class OverviewResult(
    val netWorth: BigDecimal,
    val accounts: List<AccountSummary>,
    val savings: SavingsSummary,
    val loans: LoansSummary,
    val investments: InvestmentsSummary,
    val insurance: InsuranceSummary,
    val linkedAccounts: List<LinkedAccountSummary>,
    val cards: CardsSummary,
    val vehicles: VehicleSummary,
    val tax: TaxSummary,
    val points: PointsSummary,
)

/**
 * Real "money state first" aggregation across every real itunda product — previously only
 * accounts showed on Home (see docs/TOSS_PARITY_MATRIX.md's Account aggregation row). External
 * bank/MoMo account linking remains target/blocked (no real consent registry, no provider
 * access) — this closes the *internal* aggregation gap only: itunda's own accounts, savings
 * goals, loans, stock holdings, and insurance policies, which were already each real and
 * ledger-backed individually but never summarized in one place.
 *
 * Investments use cost basis (shares * avgPrice), not live market value — `StockCatalog`
 * (which prices holdings) lives in the `:stocks` module, and no module in this backend
 * depends on another feature module, only `:core` (checked before writing this). Cost basis
 * is still a real number computed from real stored data, just not mark-to-market.
 *
 * netWorth = account balances + savings + investment cost basis - loan outstanding. Verified
 * these don't double-count: depositing to savings, buying stock, and disbursing/repaying a
 * loan all move real money between a account and a dedicated ledger clearing account (checked
 * SavingsService/StocksService/LoansService directly) — never both counted as account balance
 * and product balance at once. Insurance is excluded from netWorth entirely (paid premiums
 * are a sunk expense, not an asset, same as real personal-finance accounting) and reported as
 * a coverage summary instead.
 */
@Service
class OverviewService(
    private val accountRepository: AccountRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val loanAccountRepository: LoanAccountRepository,
    private val holdingRepository: HoldingRepository,
    private val insurancePolicyRepository: InsurancePolicyRepository,
    private val linkedAccountRepository: LinkedAccountRepository,
    private val debitCardRepository: DebitCardRepository,
    private val vehicleRepository: VehicleRepository,
    private val transactionRepository: TransactionRepository,
    private val rewardClaimRepository: RewardClaimRepository,
    private val netWorthSnapshotRepository: NetWorthSnapshotRepository,
) {
    companion object {
        // Matches the reference screenshot's own 6-month window (3월 through 8월).
        private const val NET_WORTH_HISTORY_MONTHS = 6L
        // Real RRA tax billers (BillsCatalog.providers, b8/b9/b10) -- a bill payment's
        // real description is always "Bill payment - <billId>(...)" (BillsService.payBill),
        // so an exact prefix match on "Bill payment - b8" etc. is required, NOT a bare
        // .contains("b1") -- "b1" (REG - Electricity) is a literal substring of "b10"
        // (RRA - Trading License), which would silently misclassify a real electricity
        // payment as a tax payment.
        private val TAX_BILLER_DESCRIPTION_PREFIXES = listOf("Bill payment - b8", "Bill payment - b9", "Bill payment - b10")
    }

    fun getOverview(userId: String): OverviewResult {
        val userAccounts = accountRepository.findByUserId(userId)
        val accounts = userAccounts.map { AccountSummary(it.id, it.type.name, it.accountName, it.balance, it.currency) }
        val accountTotal = accounts.fold(BigDecimal.ZERO) { acc, w -> acc + w.balance }

        val goals = savingsGoalRepository.findByUserId(userId)
        val savingsTotal = goals.fold(BigDecimal.ZERO) { acc, g -> acc + g.currentAmount }
        val savings = SavingsSummary(savingsTotal, goals.size)

        val activeLoans = loanAccountRepository.findByUserId(userId).filter { it.status == LoanStatus.ACTIVE }
        val outstandingTotal = activeLoans.fold(BigDecimal.ZERO) { acc, l -> acc + l.outstanding }
        val loans = LoansSummary(outstandingTotal, activeLoans.size)

        val holdings = holdingRepository.findByUserId(userId).filter { it.shares.signum() > 0 }
        val costBasisTotal = holdings.fold(BigDecimal.ZERO) { acc, h -> acc + (h.shares * h.avgPrice) }
        val investments = InvestmentsSummary(costBasisTotal, holdings.size)

        val activePolicies = insurancePolicyRepository.findByUserId(userId).filter { it.status == "active" }
        val premiumTotal = activePolicies.fold(BigDecimal.ZERO) { acc, p -> acc + p.monthlyPremium }
        val insurance = InsuranceSummary(activePolicies.size, premiumTotal)

        // Real external bank/MoMo linking (2026-07-13) -- closes this file's own
        // header comment's named gap. Deliberately excluded from netWorth, same
        // reasoning as insurance being excluded above: there's no real balance to
        // add. Only genuinely LINKED accounts are surfaced -- VERIFICATION_FAILED
        // never got real consent, UNLINKED has been revoked.
        val linkedAccounts = linkedAccountRepository.findByUserIdOrderByLinkedAtDesc(userId)
            .filter { it.status == LinkedAccountStatus.LINKED }
            .map {
                LinkedAccountSummary(
                    it.id, it.provider, it.externalAccountNumberMasked, it.status.name,
                    it.demoBalance, it.demoBalanceCurrency, isDemoBalance = it.demoBalance != null,
                )
            }

        val netWorth = accountTotal + savingsTotal + costBasisTotal - outstandingTotal

        val card = debitCardRepository.findByUserId(userId)
        val cards = CardsSummary(hasCard = card != null, last4 = card?.last4, design = card?.design, frozen = card?.frozen)

        val vehicles = vehicleRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val vehicleSummary = VehicleSummary(
            vehicleCount = vehicles.size,
            totalPurchasePrice = vehicles.fold(BigDecimal.ZERO) { acc, v -> acc + v.purchasePrice },
        )

        val billTransactions = transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(
            userId, TransactionType.BILL, TransactionStatus.COMPLETED, Instant.EPOCH,
        )
        val taxTransactions = billTransactions.filter { tx -> TAX_BILLER_DESCRIPTION_PREFIXES.any { tx.description.startsWith(it) } }
        val tax = TaxSummary(
            totalPaid = taxTransactions.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount },
            paymentCount = taxTransactions.size,
        )

        val rewardsTotal = rewardClaimRepository.findByUserId(userId).fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
        val payMoneyBalance = userAccounts.find { it.type == AccountType.PAY }?.balance ?: BigDecimal.ZERO
        val points = PointsSummary(rewardsTotal = rewardsTotal, payMoneyBalance = payMoneyBalance)

        return OverviewResult(netWorth, accounts, savings, loans, investments, insurance, linkedAccounts, cards, vehicleSummary, tax, points)
    }

    // Real Toss "자산 변화" (asset change over time) reference -- see
    // NetWorthSnapshot's own doc comment for the full scope rationale.
    // Deliberately independent of getOverview's own accountTotal/rewardsTotal
    // (which are shaped for the wider OverviewResult, savings/investments/loans
    // included) -- this is the narrower, trend-tracked figure alone.
    private fun computeLiquidTotal(userId: String): BigDecimal {
        val accountTotal = accountRepository.findByUserId(userId).fold(BigDecimal.ZERO) { acc, w -> acc + w.balance }
        val rewardsTotal = rewardClaimRepository.findByUserId(userId).fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
        return accountTotal + rewardsTotal
    }

    // Called once daily per real user by NetWorthSnapshotScheduler -- also directly
    // callable so a manual trigger (or a test) doesn't need to wait a real day for a
    // fresh snapshot, same "processDue is also a real business action, not scheduler-
    // only" convention SavingsMaturityReminderScheduler's own doc comment establishes.
    fun captureSnapshot(userId: String): NetWorthSnapshot {
        val snapshot = NetWorthSnapshot(id = "networthsnap_${UUID.randomUUID()}", userId = userId, liquidTotal = computeLiquidTotal(userId))
        return netWorthSnapshotRepository.save(snapshot)
    }

    fun hasSnapshotToday(userId: String): Boolean {
        val startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)
        return netWorthSnapshotRepository.existsByUserIdAndCapturedAtGreaterThanEqual(userId, startOfToday)
    }

    // Real N+1 fix (2026-09-12) -- NetWorthSnapshotScheduler.captureAll used to call
    // hasSnapshotToday once per real user in its nightly loop, an N-query cost scaling
    // with the user base on a real, guaranteed-to-run-every-night job. One batched
    // query up front instead, matching DiscoverService's own real N+1 fix (2026-09-08:
    // "call frequency, not table size, is the real signal for this bug class").
    fun getUserIdsWithSnapshotToday(): Set<String> {
        val startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)
        return netWorthSnapshotRepository.findDistinctUserIdsCapturedSince(startOfToday).toSet()
    }

    // Buckets by calendar month and keeps the LAST snapshot in each bucket (the
    // month-end value, matching a real personal-finance product's own convention) --
    // never averages or interpolates, since interpolating a missing day would be
    // fabricating a number this user's real account history never actually had.
    fun getNetWorthHistory(userId: String): List<NetWorthHistoryPoint> {
        val since = Instant.now().minus(NET_WORTH_HISTORY_MONTHS * 31, ChronoUnit.DAYS)
        val snapshots = netWorthSnapshotRepository.findByUserIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(userId, since)
        return snapshots
            .groupBy { YearMonth.from(it.capturedAt.atZone(ZoneOffset.UTC)) }
            .toSortedMap()
            .map { (month, monthSnapshots) -> NetWorthHistoryPoint(month.toString(), monthSnapshots.last().liquidTotal) }
    }
}
