package rw.itunda.insurance

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.SeasonRainfallIndex
import rw.itunda.core.domain.WalletType
import rw.itunda.core.domain.WeatherIndexCropType
import rw.itunda.core.domain.WeatherIndexPolicy
import rw.itunda.core.domain.WeatherIndexPolicyStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.SeasonRainfallIndexRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.repository.WeatherIndexPolicyRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
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
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
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

        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw NoWalletException("No wallet found for this account")

        val premiumAmount = insuredAmount.multiply(rateFor(cropType)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        val memo = "Crop weather-index premium - $cropType, $trimmedDistrict $trimmedSeason"

        // Same DEBIT wallet / CREDIT insurance_premium_revenue ledger shape as
        // InsuranceService.enrollInPlan's first-premium leg -- see this module's own
        // insurance_premium_revenue account.
        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, premiumAmount, memo),
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
        val policy = getPolicy(userId, policyId)
        if (policy.status != WeatherIndexPolicyStatus.ENROLLED) {
            throw WeatherIndexPolicyNotCancellableException("Cannot cancel a ${policy.status} policy")
        }
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw NoWalletException("No wallet found for this account")
        val memo = "Crop weather-index premium refund - ${policy.cropType}, ${policy.district} ${policy.season}"
        // Full refund -- same DEBIT insurance_premium_revenue / CREDIT wallet shape
        // InsuranceService.cancelFund already establishes for a premium refund.
        ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg("insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.DEBIT, policy.premiumAmount, memo),
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, policy.premiumAmount, memo),
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
     * the loser's transaction (including any payout legs it may have started posting)
     * rolls back whole -- so no district+season is ever double-paid.
     *
     * Every ENROLLED policy matching this exact district+season is evaluated once,
     * independent of any other district/season's policies. Below the drought threshold,
     * every one is paid its full insured amount from insurance_claims_expense straight into
     * the farmer's MAIN wallet (same approve-path ledger shape as
     * InsuranceService.decideClaim). At or above the threshold, the season simply ends with
     * no payout. A single farmer with no MAIN wallet somehow does not fail the whole batch --
     * logged and skipped, same "one bad row doesn't fail the batch" convention
     * ActionsBatchController's own doc comment establishes for batched actions.
     */
    @Transactional
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
                if (droughtTriggered) {
                    val wallet = walletRepository.findByUserIdAndType(policy.userId, WalletType.MAIN)
                    if (wallet == null) {
                        log.warn("Skipping weather-index payout for policy ${policy.id}: user ${policy.userId} has no MAIN wallet")
                        continue
                    }
                    val memo = "Crop weather-index payout - ${policy.cropType}, ${policy.district} ${policy.season}"
                    ledgerService.postLedgerTransaction(
                        wallet.currency,
                        listOf(
                            LedgerLeg("insurance_claims_expense", LedgerAccountType.INSURANCE_CLAIMS_EXPENSE, LedgerDirection.DEBIT, policy.insuredAmount, memo),
                            LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, policy.insuredAmount, memo),
                        ),
                    )
                    policy.status = WeatherIndexPolicyStatus.PAYOUT_TRIGGERED
                    policy.payoutAt = Instant.now()
                } else {
                    policy.status = WeatherIndexPolicyStatus.SEASON_ENDED_NO_PAYOUT
                }
                weatherIndexPolicyRepository.save(policy)
            } catch (e: Exception) {
                log.warn("Skipping weather-index policy ${policy.id} during publishSeasonIndex batch: ${e.message}", e)
            }
        }

        return index
    }
}
