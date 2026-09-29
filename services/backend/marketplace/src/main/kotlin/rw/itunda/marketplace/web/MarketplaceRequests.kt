package rw.itunda.marketplace.web

import rw.itunda.marketplace.OfferResponseAction
import java.math.BigDecimal

// Real fix (2026-08-26): split out of MarketplaceController.kt once that file grew
// past its file-size-lint baseline. Pure request DTOs, no behavior -- zero-risk
// mechanical move, same package so no import changes anywhere.

data class CreateListingRequest(
    val title: String,
    val description: String,
    val price: BigDecimal,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
    // Real 당근카 (Karrot Vehicles) fields -- see Listing.vehicleIsLeaseTakeover's own
    // doc comment.
    val vehicleMileageKm: Int? = null,
    val vehicleInsuranceClaimCount: Int? = null,
    val vehicleIsLeaseTakeover: Boolean = false,
    val leaseTotalAcquisitionCost: BigDecimal? = null,
    val leaseRemainingMonths: Int? = null,
    val leaseTotalMonths: Int? = null,
    val leaseMonthlyPayment: BigDecimal? = null,
    val leaseSubsidyAmount: BigDecimal? = null,
    val leaseReturnFee: BigDecimal? = BigDecimal.ZERO,
)

data class MakeOfferRequest(val amount: BigDecimal)
data class RespondToOfferRequest(val action: OfferResponseAction, val counterAmount: BigDecimal? = null)
data class MarkSoldRequest(val buyerPhoneNumber: String? = null)
data class SubmitHoodReviewRequest(val goodPoints: List<String> = emptyList(), val uncomfortablePoints: List<String> = emptyList())
data class BoostListingRequest(val days: Int)
// Real 가격 수정 (price edit) -- see MarketplaceService.updatePrice's own doc comment.
data class UpdateListingPriceRequest(val price: BigDecimal)
data class DisputeEscrowRequest(val reason: String)
// Real gap closed 2026-08-15 -- see MarketplaceEscrow.deliveryAddress's own doc comment.
// Optional: omit it (or send it empty) for the original in-person handoff.
data class PayEscrowRequest(val deliveryAddress: String? = null)
