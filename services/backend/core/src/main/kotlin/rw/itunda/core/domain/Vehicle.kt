package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * A real Toss 내 차 시세 (my car's market value)-style registered vehicle -- sourced
 * from Toss's own real, published car-services trio (blog.toss.im/article/
 * toss-automobile-service): 시세 조회 (value lookup, via a real 카마트/Carmart data
 * partnership), 보험료 조회 (insurance quote comparison across 5 real insurers), and
 * 내 차 팔기 (sell my car). This entity + `VehicleValuationService` are itunda's own
 * honest v1 of the first of those three -- see that service's own doc comment for the
 * full scope boundary (no real used-car pricing database partnership exists here,
 * unlike Toss's real Carmart integration; this is a documented, general depreciation
 * estimate, not VIN-specific market data). Insurance quote comparison already has a
 * real home in the existing `insurance` module (`InsuranceService`); "sell my car"
 * maps naturally onto the existing real `marketplace` module's own listing mechanism
 * rather than inventing a second one -- neither is duplicated here.
 */
@Entity
@Table(name = "vehicles")
class Vehicle(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false)
    var make: String,

    @Column(nullable = false)
    var model: String,

    @Column(name = "model_year", nullable = false)
    var modelYear: Int,

    @Column(name = "purchase_price", nullable = false, precision = 18, scale = 2)
    var purchasePrice: BigDecimal,

    @Column(name = "purchase_date", nullable = false)
    var purchaseDate: LocalDate,

    @Column(name = "mileage_km", nullable = false)
    var mileageKm: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", userId = "", make = "", model = "", modelYear = 2020,
        purchasePrice = BigDecimal.ZERO, purchaseDate = LocalDate.now(), mileageKm = 0,
    )
}
