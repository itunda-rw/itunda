package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * Real 비즈프로필 (Karrot Business Profile) visitor-count tracking -- one row per
 * (merchant, day), incremented atomically whenever a consumer opens that merchant's
 * real place-detail (the real trigger point: `MapsPlaceDetailService.getPlaceDetail`,
 * already built this session for the Maps redesign). Matches the reference's own
 * real "방문수 추이" (visit-count trend) line chart, which needs real per-day
 * granularity, not just a lifetime counter -- see
 * `MerchantProfileViewService.recordView`'s own doc comment for the real atomic
 * upsert this backs.
 *
 * Lives in `:merchant`'s Business Profile surface, not consumer Hood -- itunda
 * already splits merchant-owner tooling into its own apps everywhere else (real,
 * direct user decision this pass).
 */
@Entity
@Table(name = "merchant_profile_views", uniqueConstraints = [UniqueConstraint(columnNames = ["merchant_id", "view_date"])])
class MerchantProfileView(
    @Id
    @Column(length = 96)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "view_date", nullable = false)
    val viewDate: LocalDate,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 1,
) {
    protected constructor() : this(id = "", merchantId = "", viewDate = LocalDate.now())
}
