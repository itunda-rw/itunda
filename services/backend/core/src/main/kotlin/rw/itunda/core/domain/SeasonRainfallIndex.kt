package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Real Rwanda National Agricultural Insurance Scheme (NAIS)-style rainfall index, one row
 * per district+season. WFP reports the Government of Rwanda covers 40% of NAIS premium
 * costs (cooperatives/farmers the remaining 60%) for maize, rice, chilli peppers, French
 * beans, and Irish potatoes; insured land grew from 357 hectares (2019) to 3,333 hectares
 * (2020). An earlier real pilot, Kilimo Salama (Syngenta Foundation/Rwanda Ministry of
 * Agriculture/SORAS Insurance/Swiss Re/IRI/USAID), insured 37,000+ Rwandan smallholders
 * using satellite-derived rainfall data (NOAA's ARC2 product) as the payout trigger,
 * specifically because Rwanda's civil-conflict history left no usable historic rain-gauge
 * network for a conventional index. NISR's own Seasonal Agricultural Survey (run since
 * 2012) is the source of the official Season A (Sept-Feb) / B (Mar-Jun) / C (Jul-Sept)
 * convention this module's `season` strings use (e.g. "2026B").
 *
 * Honest v1 limitation, named explicitly: itunda has no live satellite/rainfall-gauge feed
 * integration and no realistic path to one. `rainfallIndexPercent` is ADMIN-TRANSCRIBED from
 * a real published NISR/Rwanda Meteorology Agency seasonal bulletin by a human, not
 * machine-ingested in real time -- this is NOT a live weather API. It mirrors the exact same
 * "no external data-feed integration this backend has no path to, so a human keys in the
 * real published number" honesty pattern InsuranceService.decideClaim's human review already
 * carries for individual claims; here the human-keyed fact just happens to fan out to every
 * policy in a district+season at once instead of one claim.
 *
 * The DB-level unique constraint on (district, season) is the real race guard against a
 * double-publish double-paying every policy in that district+season -- a second publish
 * attempt must real-409, never silently overwrite (see
 * WeatherIndexInsuranceService.publishSeasonIndex).
 */
@Entity
@Table(name = "season_rainfall_indices", uniqueConstraints = [UniqueConstraint(columnNames = ["district", "season"])])
class SeasonRainfallIndex(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 100)
    val district: String,

    @Column(nullable = false, length = 16)
    val season: String,

    @Column(name = "rainfall_index_percent", nullable = false)
    val rainfallIndexPercent: Double,

    @Column(name = "drought_threshold_percent", nullable = false)
    val droughtThresholdPercent: Double,

    @Column(name = "published_at", nullable = false)
    val publishedAt: Instant = Instant.now(),

    @Column(name = "published_by_admin_id", nullable = false, length = 64)
    val publishedByAdminId: String,
) {
    protected constructor() : this(id = "", district = "", season = "", rainfallIndexPercent = 0.0, droughtThresholdPercent = 0.0, publishedByAdminId = "")
}
