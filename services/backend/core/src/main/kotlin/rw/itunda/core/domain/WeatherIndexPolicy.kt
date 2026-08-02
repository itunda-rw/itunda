package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

enum class WeatherIndexCropType { MAIZE, RICE, CHILLI_PEPPER, FRENCH_BEANS, IRISH_POTATO }
enum class WeatherIndexPolicyStatus { ENROLLED, PAYOUT_TRIGGERED, SEASON_ENDED_NO_PAYOUT, CANCELLED }

/**
 * Real Rwanda National Agricultural Insurance Scheme (NAIS)-style parametric/weather-index
 * crop insurance -- see WeatherIndexInsuranceService's own doc comment for the full sourced
 * account (WFP, Columbia IRI/Kilimo Salama, NISR Seasonal Agricultural Survey). Genuinely
 * structurally distinct from every other insurance product in this module: no individual
 * claim is ever filed against a policy here. A district+season's published rainfall index
 * either triggers payout for EVERY matching ENROLLED policy at once (parametric/index
 * insurance's defining mechanic) or the season simply ends with no payout -- see
 * SeasonRainfallIndex.kt's own doc comment for how that index gets into the system.
 */
@Entity
@Table(name = "weather_index_policies")
class WeatherIndexPolicy(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val cropType: WeatherIndexCropType,

    // Honest v1 limitation: itunda has no district field anywhere on User, so this is
    // self-declared free text at enrollment time, not verified against any real
    // administrative-boundary registry.
    @Column(nullable = false, length = 100)
    val district: String,

    // NISR's real "2026B"-style season convention (A=Sept-Feb, B=Mar-Jun, C=Jul-Sept).
    @Column(nullable = false, length = 16)
    val season: String,

    @Column(name = "insured_amount", nullable = false, precision = 18, scale = 2)
    val insuredAmount: BigDecimal,

    @Column(name = "premium_amount", nullable = false, precision = 18, scale = 2)
    val premiumAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: WeatherIndexPolicyStatus = WeatherIndexPolicyStatus.ENROLLED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "payout_at")
    var payoutAt: Instant? = null,

    // publishSeasonIndex's batch payout evaluation touches every ENROLLED policy in a
    // district+season at once -- concurrent publishes (or a publish racing a cancel)
    // must not both write the same row.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", cropType = WeatherIndexCropType.MAIZE, district = "", season = "", insuredAmount = BigDecimal.ZERO, premiumAmount = BigDecimal.ZERO)
}
