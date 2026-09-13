package rw.itunda.insurance

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.SeasonRainfallIndex
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.WeatherIndexCropType
import rw.itunda.core.domain.WeatherIndexPolicy
import rw.itunda.core.domain.WeatherIndexPolicyStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.SeasonRainfallIndexRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.WeatherIndexPolicyRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.util.UUID

private val MAX_INSURED_AMOUNT = BigDecimal("500000")

class WeatherIndexPolicyNotFoundException(message: String) : RuntimeException(message)
class WeatherIndexPolicyNotCancellableException(message: String) : RuntimeException(message)
class SeasonRainfallIndexAlreadyPublishedException(message: String) : RuntimeException(message)
class InvalidWeatherIndexEnrollmentException(message: String) : RuntimeException(message)

/**
 * Real Rwanda National Agricultural Insurance Scheme (NAIS)-style parametric/weather-index
 * crop insurance. WFP reports the Government of Rwanda covers 40% of NAIS premium costs
 * (cooperatives/farmers the remaining 60%) for maize, rice, chilli peppers, French beans, and
 * Irish potatoes; insured land grew from 357 hectares (2019) to 3,333 hectares (2020). An
 * earlier real pilot, Kilimo Salama (Syngenta Foundation/Rwanda Ministry of
 * Agriculture/SORAS Insurance/Swiss Re/IRI/USAID), insured 37,000+ Rwandan smallholders using
 * satellite-derived rainfall data (NOAA's ARC2 product) as the payout trigger, specifically
 * because Rwanda's civil-conflict history left no usable historic rain-gauge network for a
 * conventional index. NISR's own Seasonal Agricultural Survey (run since 2012) is the source
 * of the official Season A (Sept-Feb) / B (Mar-Jun) / C (Jul-Sept) convention this service's
 * `season` strings use (e.g. "2026B"). Sources: WFP Medium/Insight, Columbia IRI, NISR
 * Seasonal Agricultural Survey.
 *
 * Genuinely, structurally distinct from every other insurance product in this module
 * (InsuranceService's 5 plans + Mutuelle de Santé + Ejo Heza ya Moto premium savings, all
 * CLAIMS-BASED -- a user files an individual claim, an ADMIN reviews and approves/denies it):
 * here, NO individual claim is ever filed. A district+season's real published rainfall index
 * crossing a drought threshold auto-triggers payout to EVERY enrolled policy in that
 * district+season simultaneously -- the defining mechanic of parametric/index insurance. Also
 * distinct from CooperativeService's harvest-advance (a LOAN against future crop sale, using
 * LOAN_PAYABLE): this is real insurance, money only ever flows out on a real weather-triggered
 * event, and is never repaid.
 *
 * Honest v1 limitations, stated explicitly (see also SeasonRainfallIndex.kt's own doc
 * comment):
 * 1. itunda has no live satellite/rainfall-gauge feed integration and no realistic path to
 *    one -- the season's rainfall index is ADMIN-TRANSCRIBED from a real published
 *    NISR/Rwanda Meteorology Agency seasonal bulletin, not machine-ingested in real time. This
 *    mirrors the exact same "no external data-feed integration this backend has no path to,
 *    so a human keys in the real published number" honesty pattern
 *    InsuranceService.decideClaim's human review already carries -- this is NOT a live
 *    weather API.
 * 2. Only the farmer-paid 60% cooperative share is modeled as real money movement in this v1
 *    -- NAIS's real 40% government subsidy portion is NOT modeled as an actual ledger
 *    transfer, since itunda has no path to receive real GoR subsidy payments (same honesty
 *    class as VupLoan not fabricating a government disbursement it can't actually source).
 */
@Service
class WeatherIndexInsuranceService(
    private val weatherIndexPolicyRepository: WeatherIndexPolicyRepository,
    private val seasonRainfallIndexRepository: SeasonRainfallIndexRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val payoutExecutor: WeatherIndexPayoutExecutor,
) {
    private val log = LoggerFactory.getLogger(WeatherIndexInsuranceService::class.java)

    // itunda's own conservative flat premium-per-crop rate -- NOT a reproduction of NAIS's
    // real actuarial pricing (itunda has no access to that), just an honest flat bound so
    // this feature can be demoed with real ledger-backed money movement. Rice/Irish potato
    // (irrigated/higher input cost) and chilli pepper (highest-value, most drought-sensitive
    // per WFP's own crop list) are priced above maize/French beans.
    private val cropCatalog = listOf(
        mapOf("cropType" to WeatherIndexCropType.MAIZE, "name" to "Maize", "premiumRatePercent" to 6.0, "description" to "Rwanda's staple cereal crop, grown in all three NAIS seasons"),
        mapOf("cropType" to WeatherIndexCropType.RICE, "name" to "Rice", "premiumRatePercent" to 7.0, "description" to "Irrigated marshland rice, higher input cost than rain-fed crops"),
        mapOf("cropType" to WeatherIndexCropType.CHILLI_PEPPER, "name" to "Chilli Pepper", "premiumRatePercent" to 8.0, "description" to "High-value export crop, most drought-sensitive of the 5 NAIS crops"),
        mapOf("cropType" to WeatherIndexCropType.FRENCH_BEANS, "name" to "French Beans", "premiumRatePercent" to 6.0, "description" to "Short-cycle legume, widely intercropped with maize"),
        mapOf("cropType" to WeatherIndexCropType.IRISH_POTATO, "name" to "Irish Potato", "premiumRatePercent" to 7.0, "description" to "Highland tuber crop, concentrated in Northern/Western Province"),
    )

    fun getCatalog(): List<Map<String, Any?>> = cropCatalog

    private fun rateFor(cropType: WeatherIndexCropType): BigDecimal {
        val entry = cropCatalog.first { it["cropType"] == cropType }
        return BigDecimal(entry["premiumRatePercent"].toString())
    }

    @Transactional
    fun enroll(userId: String, cropType: WeatherIndexCropType, district: String, season: String, insuredAmount: BigDecimal): WeatherIndexPolicy {
        val trimmedDistrict = district.trim()
        val trimmedSeason = season.trim()
        if (trimmedDistrict.isEmpty() || trimmedDistrict.length > 100) {
            throw InvalidWeatherIndexEnrollmentException("District is required and must be 100 characters or fewer")
        }
        if (trimmedSeason.isEmpty() || trimmedSeason.length > 16) {
            throw InvalidWeatherIndexEnrollmentException("Season is required and must be 16 characters or fewer")
        }
        if (insuredAmount <= BigDecimal.ZERO || insuredAmount > MAX_INSURED_AMOUNT) {
            throw InvalidWeatherIndexEnrollmentException("Insured amount must be greater than zero and no more than $MAX_INSURED_AMOUNT RWF")
        }

        // Real anti-spam limit, same convention as InsuranceService.submitClaim/createPremiumFund.
        rateLimiter.checkLimit("weather-index:enroll:$userId", limit = 10, window = Duration.ofDays(1))

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw NoAccountException("No account found for this account")

        val premiumAmount = insuredAmount.multiply(rateFor(cropType)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        val memo = "Crop weather-index premium - $cropType, $trimmedDistrict $trimmedSeason"

        // Same DEBIT account / CREDIT insurance_premium_revenue ledger shape as
        // InsuranceService.enrollInPlan's first-premium leg -- see this module's own
        // insurance_premium_revenue account.
        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, premiumAmount, memo),
                LedgerLeg("insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.CREDIT, premiumAmount, memo),
            ),
        )

        val policy = WeatherIndexPolicy(
            id = "wip_${UUID.randomUUID()}",
            userId = userId,
            cropType = cropType,
            district = trimmedDistrict,
            season = trimmedSeason,
            insuredAmount = insuredAmount,
            premiumAmount = premiumAmount,
        )
        return weatherIndexPolicyRepository.save(policy)
    }

    fun getMyPolicies(userId: String): List<WeatherIndexPolicy> = weatherIndexPolicyRepository.findByUserId(userId)

    fun getPolicy(userId: String, policyId: String): WeatherIndexPolicy =
        weatherIndexPolicyRepository.findById(policyId)
            .filter { it.userId == userId }
            .orElseThrow { WeatherIndexPolicyNotFoundException("Policy not found") }

    @Transactional
    fun cancel(userId: String, policyId: String): WeatherIndexPolicy {
        // Real gap found live (2026-09-13, sibling-asymmetry sweep): enroll (above) and
        // InsuranceService.cancelFund (the equivalent refund action in the sibling
        // insurance service) both rate-limit; this real full-refund action never did.
        rateLimiter.checkLimit("weather-index:cancel:$userId", limit = 10, window = Duration.ofDays(1))
        val policy = getPolicy(userId, policyId)
        if (policy.status != WeatherIndexPolicyStatus.ENROLLED) {
            throw WeatherIndexPolicyNotCancellableException("Cannot cancel a ${policy.status} policy")
        }
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw NoAccountException("No account found for this account")
        val memo = "Crop weather-index premium refund - ${policy.cropType}, ${policy.district} ${policy.season}"
        // Full refund -- same DEBIT insurance_premium_revenue / CREDIT account shape
        // InsuranceService.cancelFund already establishes for a premium refund.
        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg("insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.DEBIT, policy.premiumAmount, memo),
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, policy.premiumAmount, memo),
            ),
        )
        policy.status = WeatherIndexPolicyStatus.CANCELLED
        return weatherIndexPolicyRepository.save(policy)
    }

    fun getSeasonIndex(district: String, season: String): SeasonRainfallIndex? =
        seasonRainfallIndexRepository.findByDistrictAndSeason(district, season)

    /**
     * The real auto-payout batch-evaluation entry point. Publishing a district+season's
     * rainfall index is a one-time, human-transcribed fact (see this class's own doc
     * comment) with its own real DB-level uniqueness guard -- this pre-check gives a clean
     * 409 for the common case, but the real backstop against two concurrent publish
     * attempts racing each other is season_rainfall_indices' own
     * (district, season) unique constraint (see V222__weather_index_insurance.sql):
     * if both requests somehow pass this pre-check, only one INSERT can ever succeed, and
     * the loser's own transaction rolls back cleanly -- so no district+season is ever
     * double-paid.
     *
     * Real bug found in this feature's own build-time review (2026-08-02): a first draft
     * wrapped this whole per-policy payout loop in ONE `@Transactional` method with a
     * try/catch per policy, intending "one bad farmer doesn't fail the whole batch" -- but
     * that isolation was fake. A `RuntimeException` from any nested `@Transactional`-
     * participating call (e.g. a genuine optimistic-lock conflict if a farmer's own
     * `cancel()` races this exact update) marks the WHOLE ambient Spring transaction
     * rollback-only the instant it's thrown, regardless of whether outer code catches and
     * logs it -- silently reverting every OTHER farmer's already-"successful" payout in the
     * same batch, plus the newly-published index row itself, once the method returns and
     * Spring fails to commit. Deliberately NOT `@Transactional` here for that reason; each
     * step below (saving the index row, and each policy's own settlement via
     * `WeatherIndexPayoutExecutor`, a genuinely separate `@Service` bean called through its
     * own real transactional proxy) gets its own independent, isolated transaction --
     * mirroring the exact fix `P2pService.sendDirect`/`RoundUpService.processRoundUp`
     * already established for this same self-invocation-inside-one-transaction gotcha, and
     * the real per-action isolation `ActionsBatchController`'s own separate-bean-per-call
     * shape already achieves for the offline batch endpoint.
     *
     * Every ENROLLED policy matching this exact district+season is evaluated once,
     * independent of any other district/season's policies. Below the drought threshold,
     * every one is paid its full insured amount from insurance_claims_expense straight into
     * the farmer's MAIN account (same approve-path ledger shape as
     * InsuranceService.decideClaim). At or above the threshold, the season simply ends with
     * no payout. A single farmer with no MAIN account, or any other single-policy failure,
     * genuinely cannot take down another farmer's already-paid settlement or the index row.
     */
    fun publishSeasonIndex(adminId: String, district: String, season: String, rainfallIndexPercent: Double, droughtThresholdPercent: Double): SeasonRainfallIndex {
        val trimmedDistrict = district.trim()
        val trimmedSeason = season.trim()
        if (seasonRainfallIndexRepository.findByDistrictAndSeason(trimmedDistrict, trimmedSeason) != null) {
            throw SeasonRainfallIndexAlreadyPublishedException("A rainfall index has already been published for $trimmedDistrict $trimmedSeason")
        }

        val index = seasonRainfallIndexRepository.save(
            SeasonRainfallIndex(
                id = "sri_${UUID.randomUUID()}",
                district = trimmedDistrict,
                season = trimmedSeason,
                rainfallIndexPercent = rainfallIndexPercent,
                droughtThresholdPercent = droughtThresholdPercent,
                publishedByAdminId = adminId,
            ),
        )

        val droughtTriggered = rainfallIndexPercent < droughtThresholdPercent
        val matchingPolicies = weatherIndexPolicyRepository.findByDistrictAndSeasonAndStatus(trimmedDistrict, trimmedSeason, WeatherIndexPolicyStatus.ENROLLED)
        for (policy in matchingPolicies) {
            try {
                payoutExecutor.settleOnePolicy(policy, droughtTriggered)
            } catch (e: Exception) {
                log.warn("Skipping weather-index policy ${policy.id} during publishSeasonIndex batch: ${e.message}", e)
            }
        }

        return index
    }
}
