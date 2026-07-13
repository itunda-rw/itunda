package rw.itunda.overview

import org.springframework.stereotype.Service
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal

data class AccountSummary(val id: String, val type: String, val name: String, val balance: BigDecimal, val currency: String)
data class SavingsSummary(val totalSaved: BigDecimal, val goalCount: Int)
data class LoansSummary(val totalOutstanding: BigDecimal, val activeCount: Int)
data class InvestmentsSummary(val totalCostBasis: BigDecimal, val holdingCount: Int)
data class InsuranceSummary(val activePolicyCount: Int, val totalMonthlyPremium: BigDecimal)
// Deliberately no `balance` field -- see LinkedAccount.kt's own doc comment for why:
// itunda has no real provider access to fetch a live external balance from, and
// fabricating one would misrepresent this as more integrated than it honestly is.
data class LinkedAccountSummary(val id: String, val provider: String, val maskedAccountNumber: String, val status: String)
data class OverviewResult(
    val netWorth: BigDecimal,
    val accounts: List<AccountSummary>,
    val savings: SavingsSummary,
    val loans: LoansSummary,
    val investments: InvestmentsSummary,
    val insurance: InsuranceSummary,
    val linkedAccounts: List<LinkedAccountSummary>,
)

/**
 * Real "money state first" aggregation across every real itunda product — previously only
 * wallets showed on Home (see docs/TOSS_PARITY_MATRIX.md's Account aggregation row). External
 * bank/MoMo account linking remains target/blocked (no real consent registry, no provider
 * access) — this closes the *internal* aggregation gap only: itunda's own wallets, savings
 * goals, loans, stock holdings, and insurance policies, which were already each real and
 * ledger-backed individually but never summarized in one place.
 *
 * Investments use cost basis (shares * avgPrice), not live market value — `StockCatalog`
 * (which prices holdings) lives in the `:stocks` module, and no module in this backend
 * depends on another feature module, only `:core` (checked before writing this). Cost basis
 * is still a real number computed from real stored data, just not mark-to-market.
 *
 * netWorth = wallet balances + savings + investment cost basis - loan outstanding. Verified
 * these don't double-count: depositing to savings, buying stock, and disbursing/repaying a
 * loan all move real money between a wallet and a dedicated ledger clearing account (checked
 * SavingsService/StocksService/LoansService directly) — never both counted as wallet balance
 * and product balance at once. Insurance is excluded from netWorth entirely (paid premiums
 * are a sunk expense, not an asset, same as real personal-finance accounting) and reported as
 * a coverage summary instead.
 */
@Service
class OverviewService(
    private val walletRepository: WalletRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val loanAccountRepository: LoanAccountRepository,
    private val holdingRepository: HoldingRepository,
    private val insurancePolicyRepository: InsurancePolicyRepository,
    private val linkedAccountRepository: LinkedAccountRepository,
) {

    fun getOverview(userId: String): OverviewResult {
        val wallets = walletRepository.findByUserId(userId)
        val accounts = wallets.map { AccountSummary(it.id, it.type.name, it.accountName, it.balance, it.currency) }
        val walletTotal = wallets.fold(BigDecimal.ZERO) { acc, w -> acc + w.balance }

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
            .map { LinkedAccountSummary(it.id, it.provider, it.externalAccountNumberMasked, it.status.name) }

        val netWorth = walletTotal + savingsTotal + costBasisTotal - outstandingTotal

        return OverviewResult(netWorth, accounts, savings, loans, investments, insurance, linkedAccounts)
    }
}
