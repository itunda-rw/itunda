package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.VendorCashAdvance
import rw.itunda.core.domain.VendorCashAdvanceStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.VendorCashAdvanceRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class VendorCashAdvanceNotFoundException(message: String) : RuntimeException(message)
class VendorCashAdvanceNotRequestedException(message: String) : RuntimeException(message)
class VendorCashAdvanceNotRepayableException(message: String) : RuntimeException(message)
class VendorCashAdvanceAlreadyActiveException(message: String) : RuntimeException(message)
class VendorCashAdvanceNotEligibleException(message: String) : RuntimeException(message)
class VendorCashAdvanceInvalidAmountException(message: String) : RuntimeException(message)
class VendorCashAdvanceNoWalletException(message: String) : RuntimeException(message)

private val ACTIVE_STATUSES = listOf(VendorCashAdvanceStatus.REQUESTED, VendorCashAdvanceStatus.DISBURSED)

// itunda's own honest, conservative pick -- not a claimed reproduction of any real
// regulatory ceiling (the sourcing didn't specify one; LODA/City of Kigali's real 2%
// restocking loans don't publish a per-vendor cap either).
private val HARD_CAP = BigDecimal("1000000")

// itunda's own one-time factor-fee pricing choice, not a sourced/regulated rate.
private val FLAT_FEE_RATE = BigDecimal("0.08")

// itunda's own daily-sweep collection rate -- the % of a merchant's REAL itunda-
// collected settlement inflow (see runDailyCollection) swept toward remainingOwed
// each collection cycle, not a fixed installment schedule.
private const val COLLECTION_RATE_PERCENT = 15.0

private const val LOOKBACK_DAYS = 30L
private const val MIN_TRADING_DAYS = 14
private const val OFFER_DAYS_MULTIPLE = 90

// A DISBURSED advance is only actually swept once real ~24h has passed since its last
// collection (or since disbursal, if never collected) -- see
// VendorCashAdvanceCollectionScheduler's own doc comment for why the 60-second poll
// interval is a demo-speed convenience, not the real business cadence.
private val MIN_COLLECTION_INTERVAL: Duration = Duration.ofHours(24)

/**
 * Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- a merchant-settlement-
 * based cash advance for Kigali's informal/formalizing street vendors, auto-collected
 * as a variable % of the merchant's own real itunda-routed daily sales rather than a
 * fixed installment schedule.
 *
 * Sourced need: City of Kigali's 2016 census counted 12,197 registered street
 * vendors, and the city/LODA committed in 2022 to relocating ~4,000 into formal
 * mini-markets, with LODA/City of Kigali offering formalized vendors loans at 2%
 * annual interest specifically to help them restock (allAfrica 2022/2024,
 * loda.gov.rw) -- government's own recognition that working-capital access, not
 * stall space, is the real constraint for this population. A 2025 Streetnet
 * International field report documents VSLA savings/loan groups organized around
 * Kigali market/street vendors' irregular daily cash flow. AfDB SME-finance research
 * found only ~25% of Rwandan SMEs use bank loans (40% rely on retained earnings, 30%
 * on trade credit). NISR FinScope 2024 found 72% of Rwandan adults still used
 * informal credit sources despite 96% formal financial inclusion -- inclusion hasn't
 * eliminated informal-credit dependence for people whose income doesn't fit fixed-
 * installment underwriting.
 *
 * This is explicitly the "사장님 대출 (Boss Loans) as a business-specific lending
 * product" follow-up gap `MerchantBusinessAccountService`'s own doc comment names as
 * unshipped. Genuinely distinct from every other lending feature in this codebase
 * (confirmed via grep): `OverdraftService`/`PostpaidCreditService` are user-
 * repayment-initiated with generic credit-score underwriting; `CooperativeService`'s
 * harvest advance is single-lump-sum full-balance-only repayment on a fixed harvest
 * date; `VupLoanService`/`StudentLoanService` use a self-declared-income-based
 * SUGGESTED installment, never auto-collected. NONE of these auto-collects a
 * variable share of a merchant's actual observed daily settlement inflow.
 *
 * **Honest v1 limitations (also called out inline below)**:
 * 1. The collection sweep can only see and collect against settlement volume that
 *    actually flows through itunda's own `MerchantService.collect` (QR/card). A
 *    vendor doing mostly cash sales off-platform is invisible to underwriting and
 *    collection alike -- this product can only serve the itunda-routed share of a
 *    vendor's real revenue.
 * 2. The inflow calculation filters `LedgerEntry.memo` text matching
 *    `MerchantService.collect`'s exact real narration pattern (confirmed via grep:
 *    `"$channelLabel collection - ${intent.description}"` and
 *    `"Card collection - $description"` at `MerchantService.kt:591,792`, both
 *    containing the substring `"collection -"`) -- a real but fragile v1 shortcut;
 *    named honestly rather than pretending it's a structured settlement-category
 *    field.
 * 3. Collection is always capped at
 *    `min(collectionRatePercent x inflow, remainingOwed, currentWalletBalance)` -- it
 *    can never push a merchant's wallet negative or over-collect beyond what's
 *    actually owed, even if a vendor's balance briefly looks inflated from a
 *    non-sales top-up.
 */
@Service
class VendorCashAdvanceService(
    private val vendorCashAdvanceRepository: VendorCashAdvanceRepository,
    private val merchantRepository: MerchantRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val rateLimiter: RateLimiter,
) {
    // 404, not 403 -- a merchant that doesn't exist and a merchant that exists but
    // isn't owned by this caller are indistinguishable from the outside, same IDOR
    // discipline every other ownership check in this codebase already follows.
    private fun getOwnedMerchant(userId: String, merchantId: String): Merchant {
        val merchant = merchantRepository.findById(merchantId).orElse(null)
        if (merchant == null || merchant.ownerUserId != userId) {
            throw VendorCashAdvanceNotFoundException("Merchant not found")
        }
        return merchant
    }

    private fun getOwnedAdvance(userId: String, advanceId: String): Pair<VendorCashAdvance, Merchant> {
        val advance = vendorCashAdvanceRepository.findById(advanceId).orElseThrow {
            VendorCashAdvanceNotFoundException("Vendor cash advance not found")
        }
        val merchant = merchantRepository.findById(advance.merchantId).orElse(null)
        if (merchant == null || merchant.ownerUserId != userId) {
            throw VendorCashAdvanceNotFoundException("Vendor cash advance not found")
        }
        return advance to merchant
    }

    private fun requireWallet(merchant: Merchant) =
        walletRepository.findById(merchant.walletId).orElseThrow {
            VendorCashAdvanceNoWalletException("Merchant settlement wallet not found")
        }

    /**
     * Sums this merchant's real itunda-collected settlement inflow (WALLET CREDIT
     * entries whose memo matches `MerchantService.collect`'s "collection -" narration
     * -- see this class's own doc comment, limitation #2) over the trailing 30 days,
     * and counts distinct calendar trading days. Read-only -- never mutates anything.
     */
    fun getOffer(merchantId: String): Map<String, Any?> {
        val merchant = merchantRepository.findById(merchantId).orElseThrow {
            VendorCashAdvanceNotFoundException("Merchant not found")
        }
        val wallet = requireWallet(merchant)

        val since = Instant.now().minus(Duration.ofDays(LOOKBACK_DAYS))
        val settlementCredits = ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(wallet.id, since)
            .filter { it.direction == LedgerDirection.CREDIT && it.memo.contains("collection -") }

        val tradingDays = settlementCredits.map { it.createdAt.atZone(ZoneOffset.UTC).toLocalDate() }.distinct().size
        if (tradingDays < MIN_TRADING_DAYS) {
            return mapOf(
                "eligible" to false,
                "reason" to "At least $MIN_TRADING_DAYS days of real itunda-collected settlement history is required",
            )
        }

        val totalInflow = settlementCredits.sumOf { it.amount }
        val averageDailySettlement = totalInflow.divide(BigDecimal(LOOKBACK_DAYS), 2, RoundingMode.HALF_UP)
        val offerAmount = averageDailySettlement.multiply(BigDecimal(OFFER_DAYS_MULTIPLE)).min(HARD_CAP).setScale(2, RoundingMode.HALF_UP)
        val feeAmount = offerAmount.multiply(FLAT_FEE_RATE).setScale(2, RoundingMode.HALF_UP)

        return mapOf(
            "eligible" to true,
            "offerAmount" to offerAmount,
            "feeAmount" to feeAmount,
            "collectionRatePercent" to COLLECTION_RATE_PERCENT,
            "averageDailySettlement" to averageDailySettlement,
            "tradingDays" to tradingDays,
        )
    }

    // Real "reject if already active" check-then-CREATE race this session has hit
    // repeatedly (VupLoanService.applyForLoan/StudentLoanService.applyForLoan/
    // MotoOwnershipService.createPlan) -- locking the merchant's own wallet row
    // BEFORE checking for an existing active advance serializes two concurrent
    // applications for the same merchant, closing the same class of race those three
    // features already needed this exact fix for.
    @Transactional
    fun applyForAdvance(userId: String, merchantId: String): VendorCashAdvance {
        val merchant = getOwnedMerchant(userId, merchantId)
        val wallet = requireWallet(merchant)
        walletRepository.findByIdForUpdate(wallet.id)

        val activeAdvances = vendorCashAdvanceRepository.findByMerchantIdAndStatusIn(merchantId, ACTIVE_STATUSES)
        if (activeAdvances.isNotEmpty()) {
            throw VendorCashAdvanceAlreadyActiveException("This merchant already has an active vendor cash advance -- repay it before applying for another")
        }

        // Re-derive the offer server-side -- never trust a client-supplied amount.
        val offer = getOffer(merchantId)
        if (offer["eligible"] != true) {
            throw VendorCashAdvanceNotEligibleException(offer["reason"] as? String ?: "Not eligible for a vendor cash advance")
        }

        rateLimiter.checkLimit("vendor-advance:apply:$userId", limit = 5, window = Duration.ofDays(1))

        val principalAmount = offer["offerAmount"] as BigDecimal
        val feeAmount = offer["feeAmount"] as BigDecimal
        val totalOwed = principalAmount.add(feeAmount)

        return vendorCashAdvanceRepository.save(
            VendorCashAdvance(
                id = "vendoradv_${UUID.randomUUID()}",
                merchantId = merchantId,
                principalAmount = principalAmount,
                feeAmount = feeAmount,
                totalOwed = totalOwed,
                remainingOwed = totalOwed,
                collectionRatePercent = COLLECTION_RATE_PERCENT,
            ),
        )
    }

    // itunda lends `principalAmount` (real cash disbursed) and the merchant owes back
    // `totalOwed = principalAmount + feeAmount`. The fee itself is never disbursed as
    // cash -- it's recognized as itunda's revenue immediately at disbursement time, in
    // the SAME balanced transaction:
    //   - wallet: CREDIT principalAmount (the real cash movement, mirrors every other
    //     disburse in this codebase exactly)
    //   - loan_payable: DEBIT principalAmount (the real principal now owed)
    //   - loan_payable: DEBIT feeAmount (the fee, added to what's owed, with no cash
    //     changing hands for this leg)
    //   - fee_revenue: CREDIT feeAmount (recognized as itunda's revenue immediately)
    // Net loan_payable position across both legs = principalAmount + feeAmount =
    // totalOwed, exactly matching remainingOwed. Debits (principalAmount + feeAmount
    // twice, once each to loan_payable) equal credits (principalAmount to wallet +
    // feeAmount to fee_revenue) for any principal/fee split -- the same "verify the
    // debit/credit directions net to the right final owed amount" discipline
    // `MotoOwnershipService.convertToLoan`'s own doc comment documents having to
    // self-correct for; this feature's test asserts the debits-equal-credits
    // invariant explicitly, the way that fix's own test does.
    @Transactional
    fun disburse(userId: String, advanceId: String): VendorCashAdvance {
        val (advance, merchant) = getOwnedAdvance(userId, advanceId)
        if (advance.status != VendorCashAdvanceStatus.REQUESTED) {
            throw VendorCashAdvanceNotRequestedException("Only a REQUESTED vendor cash advance can be disbursed")
        }
        val wallet = requireWallet(merchant)

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, advance.principalAmount, "Isoko Vendor Cash Advance disbursement"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, advance.principalAmount, "Isoko Vendor Cash Advance principal owed"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, advance.feeAmount, "Isoko Vendor Cash Advance fee owed"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, advance.feeAmount, "Isoko Vendor Cash Advance fee revenue"),
            ),
        )

        advance.status = VendorCashAdvanceStatus.DISBURSED
        advance.disbursedAt = Instant.now()
        return vendorCashAdvanceRepository.save(advance)
    }

    fun getMyAdvance(userId: String, merchantId: String): VendorCashAdvance? {
        getOwnedMerchant(userId, merchantId)
        return vendorCashAdvanceRepository.findByMerchantIdAndStatusIn(merchantId, ACTIVE_STATUSES)
            .maxByOrNull { it.requestedAt }
    }

    fun getAdvance(userId: String, advanceId: String): VendorCashAdvance = getOwnedAdvance(userId, advanceId).first

    // Kept deliberately simple -- for v1, without a separate collection-history table,
    // this reports the advance's own current collection state rather than a full
    // ledger-entry-history endpoint, which would add real complexity beyond this
    // feature's own scope.
    fun getCollectionHistory(userId: String, advanceId: String): Map<String, Any?> {
        val (advance, _) = getOwnedAdvance(userId, advanceId)
        return mapOf(
            "lastCollectionAt" to advance.lastCollectionAt,
            "remainingOwed" to advance.remainingOwed,
            "totalOwed" to advance.totalOwed,
            "status" to advance.status,
        )
    }

    @Transactional
    fun repayEarly(userId: String, advanceId: String, amount: BigDecimal): VendorCashAdvance {
        val (advance, merchant) = getOwnedAdvance(userId, advanceId)
        if (advance.status != VendorCashAdvanceStatus.DISBURSED) {
            throw VendorCashAdvanceNotRepayableException("Only a DISBURSED vendor cash advance can be repaid")
        }
        if (amount <= BigDecimal.ZERO) throw VendorCashAdvanceInvalidAmountException("Repayment amount must be positive")

        val wallet = requireWallet(merchant)

        // Clamp BEFORE ever touching the ledger -- the exact overshoot-clamp lesson
        // from InsuranceService.contributeToFund/VupLoanService.repay/
        // StudentLoanService.repay/MotoOwnershipService.repay.
        val actualAmount = amount.min(advance.remainingOwed)

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "Isoko Vendor Cash Advance repayment"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, actualAmount, "Isoko Vendor Cash Advance repayment"),
            ),
        )

        advance.remainingOwed = advance.remainingOwed.subtract(actualAmount)
        if (advance.remainingOwed <= BigDecimal.ZERO) {
            advance.status = VendorCashAdvanceStatus.REPAID
            advance.repaidAt = Instant.now()
        }
        return vendorCashAdvanceRepository.save(advance)
    }

    // For VendorCashAdvanceCollectionScheduler.
    fun getAdvancesDueForCollection(): List<VendorCashAdvance> = vendorCashAdvanceRepository.findByStatus(VendorCashAdvanceStatus.DISBURSED)

    /**
     * Sums this merchant's real itunda-collected settlement inflow since the last
     * collection (or since disbursal, if never collected -- see limitations #1/#2 on
     * this class's own doc comment), sweeps
     * `min(collectionRatePercent x inflow, remainingOwed, currentWalletBalance)`
     * toward `remainingOwed`. Skips (returns false) rather than posting a zero/negative
     * leg when there's nothing real to collect.
     */
    @Transactional
    fun runDailyCollection(advance: VendorCashAdvance): Boolean {
        val merchant = merchantRepository.findById(advance.merchantId).orElse(null) ?: return false
        val wallet = walletRepository.findById(merchant.walletId).orElse(null) ?: return false

        val since = advance.lastCollectionAt ?: advance.disbursedAt ?: advance.requestedAt
        val settlementCredits = ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(wallet.id, since)
            .filter { it.direction == LedgerDirection.CREDIT && it.memo.contains("collection -") }
        val inflow = settlementCredits.sumOf { it.amount }

        val rateShare = inflow.multiply(BigDecimal.valueOf(advance.collectionRatePercent)).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        val collectAmount = rateShare.min(advance.remainingOwed).min(wallet.availableBalance)
        if (collectAmount <= BigDecimal.ZERO) return false

        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, collectAmount, "Isoko Vendor Cash Advance collection"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, collectAmount, "Isoko Vendor Cash Advance collection"),
            ),
        )

        advance.remainingOwed = advance.remainingOwed.subtract(collectAmount)
        advance.lastCollectionAt = Instant.now()
        if (advance.remainingOwed <= BigDecimal.ZERO) {
            advance.status = VendorCashAdvanceStatus.REPAID
            advance.repaidAt = Instant.now()
        }
        vendorCashAdvanceRepository.save(advance)
        return true
    }

    // Real business-cadence gate -- the scheduler polls every 60 seconds (demo-speed),
    // but an advance is only actually swept once real ~24h has elapsed since its last
    // collection/disbursal, so this doesn't collect every 60 seconds in practice. See
    // VendorCashAdvanceCollectionScheduler's own doc comment.
    fun isDueForCollection(advance: VendorCashAdvance): Boolean {
        val since = advance.lastCollectionAt ?: advance.disbursedAt ?: return false
        return Duration.between(since, Instant.now()) >= MIN_COLLECTION_INTERVAL
    }
}
