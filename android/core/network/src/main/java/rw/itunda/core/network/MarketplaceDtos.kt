package rw.itunda.core.network

// Extracted out of ApiService.kt (2026-08-28, itunda Hood/Karrot redesign) to make
// room for real 당근카 (Karrot Vehicles) fields without pushing the already-at-baseline
// ApiService.kt over its frozen file-size-lint limit -- same "extract, don't just trim"
// precedent this session's own Marketplace backend piece already used.
data class ListingDto(
    val id: String,
    val sellerId: String,
    val title: String,
    val description: String,
    val price: Double,
    val category: String,
    val status: String,
    val createdAt: String,
    // Real optional seller-set location (2026-07-18 backend) -- backs real proximity
    // search and, 2026-07-19, "Directions to this seller".
    val latitude: Double? = null,
    val longitude: Double? = null,
    val neighborhood: String? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
    // buyerId added 2026-07-24 -- real optional buyer identification captured at
    // mark-sold time, see backend Listing.kt's own doc comment. Only set once a real
    // review becomes possible for this transaction.
    val buyerId: String? = null,
    // Real seller-paid sponsored placement (2026-07-25) -- see backend Listing.kt's own
    // doc comment. Null/expired means "not boosted" -- clients should only show a
    // "Sponsored" badge when this is a real, still-future ISO instant.
    val boostedUntil: String? = null,
    // Real like count (2026-08-03) -- see backend Listing.kt's own doc comment. A
    // separate concept from favoriteIds' personal wishlist -- this is a public
    // engagement count, matching real 당근마켓's heart count on every listing row.
    val likeCount: Long = 0,
    // Real 당근마켓 끌어올리기 (bump to top of feed), 2026-08-10 -- see backend
    // Listing.kt's own doc comment. Null means never bumped.
    val bumpedAt: String? = null,
    // Real 당근카 (Karrot Vehicles) fields (2026-08-28) -- see backend
    // Listing.vehicleIsLeaseTakeover's own doc comment. Null mileage/claim count means
    // "not a vehicle listing"; lease* fields stay null unless vehicleIsLeaseTakeover.
    val vehicleMileageKm: Int? = null,
    val vehicleInsuranceClaimCount: Int? = null,
    val vehicleIsLeaseTakeover: Boolean = false,
    val leaseTotalAcquisitionCost: Double? = null,
    val leaseRemainingMonths: Int? = null,
    val leaseTotalMonths: Int? = null,
    val leaseMonthlyPayment: Double? = null,
    val leaseSubsidyAmount: Double? = null,
    val leaseReturnFee: Double? = null,
)
data class CreateListingRequest(
    val title: String,
    val description: String,
    val price: Double,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
    val vehicleMileageKm: Int? = null,
    val vehicleInsuranceClaimCount: Int? = null,
    val vehicleIsLeaseTakeover: Boolean = false,
    val leaseTotalAcquisitionCost: Double? = null,
    val leaseRemainingMonths: Int? = null,
    val leaseTotalMonths: Int? = null,
    val leaseMonthlyPayment: Double? = null,
    val leaseSubsidyAmount: Double? = null,
    val leaseReturnFee: Double? = null,
)
