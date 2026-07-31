package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) --
 * sourced from Karrot's own real, published feature (about.daangn.com: connecting a
 * real used-car buyer with a local, real garage/mechanic to accompany and inspect a car
 * before purchase). Same real self-registration shape `RideDriver`/`Rider` already
 * establish for an opt-in service role: any itunda user can register, no admin approval
 * gate -- see `VehicleInspectionService.registerAsMechanic`'s own doc comment.
 *
 * Deliberately no real-time location/dispatch fields (unlike `RideDriver`) -- a real
 * inspection is booked directly against a chosen mechanic by a buyer browsing the list,
 * not auto-dispatched the way a ride or delivery is; Karrot's own real feature is a
 * directory/booking flow, not a live-matching one.
 */
@Entity
@Table(name = "vehicle_inspection_mechanics")
class VehicleInspectionMechanic(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "business_name", nullable = false, length = 200)
    val businessName: String,

    @Column(nullable = false)
    var available: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", walletId = "", businessName = "")
}
