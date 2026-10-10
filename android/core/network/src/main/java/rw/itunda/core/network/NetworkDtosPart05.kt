package rw.itunda.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so Gson deserializes the real backend's JSON directly.

data class RideTripDto(
    val id: String, val passengerId: String, val driverId: String?, val pickupAddress: String,
    val pickupLatitude: Double, val pickupLongitude: Double, val dropoffAddress: String,
    val dropoffLatitude: Double, val dropoffLongitude: Double, val distanceKm: Double,
    val fare: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val status: String, val createdAt: String,
    // Real gap found (Support product-completeness pass, 2026-09-08): the backend's
    // RideTrip.kt has always returned this field, but this DTO never declared it, so
    // it was silently discarded on every response -- the exact same drift bank-mfe
    // already found and fixed 2026-08-16 (see lib/rideshare.ts's own doc comment).
    // Needed for the real "report an issue" hand-off into Support.
    val transactionId: String,
    // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- null means an ASAP
    // request, unchanged from before.
    val scheduledFor: String? = null,
    // Real Uber post-trip tipping -- see RideTripService.tipDriver's own doc comment.
    // Ported from bank-mfe (2026-09-03). Non-null once tipped.
    val tipAmount: java.math.BigDecimal? = null,
)
data class RideTripResponse(val success: Boolean, val trip: RideTripDto)
data class RideTripsResponse(val success: Boolean, val trips: List<RideTripDto>)
// Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
// fb8f2e3c -- see project_itunda_pagination_discard_sweep memory) --
// getMyTrips/getMyDriverTrips are real Pageable-backed on the backend, but
// page was never sent, silently capping trip history at 20 rows.
data class RideTripsPageResponse(val success: Boolean, val trips: List<RideTripDto>, val page: Int, val totalPages: Int)
data class StartRideTripRequest(val pin: String)
data class RideDriverLocationDto(val latitude: Double, val longitude: Double, val updatedAt: String)
data class RideDriverLocationResponse(val success: Boolean, val location: RideDriverLocationDto?)
data class TipRideTripRequest(val amount: java.math.BigDecimal)
data class RideTripPinResponse(val success: Boolean, val pin: String)
// Real Kakao T-style multi-stop rides (item 214) -- see the backend's RideTripStop.kt
// doc comment.
data class RideStopRequestDto(val address: String, val latitude: Double, val longitude: Double)
data class RequestRideTripRequest(
    val pickupAddress: String, val pickupLatitude: Double, val pickupLongitude: Double,
    val dropoffAddress: String, val dropoffLatitude: Double, val dropoffLongitude: Double,
    val scheduledFor: String? = null,
    val stops: List<RideStopRequestDto>? = null,
)
data class RideTripStopDto(
    val id: String, val tripId: String, val sequence: Int, val address: String,
    val latitude: Double, val longitude: Double, val arrivedAt: String?,
)
data class RideTripStopResponse(val success: Boolean, val stop: RideTripStopDto)
data class RideTripStopsResponse(val success: Boolean, val stops: List<RideTripStopDto>)

// Real Uber Safety "Trusted Contacts" -- mirrors the backend's RideTrustedContact.kt
// exactly (see that file's own doc comment for the full sourced account).
data class RideTrustedContactDto(
    val id: String, val userId: String, val contactUserId: String, val contactName: String, val createdAt: String,
)
data class RideTrustedContactResponse(val success: Boolean, val contact: RideTrustedContactDto)
data class RideTrustedContactsResponse(val success: Boolean, val contacts: List<RideTrustedContactDto>)
data class AddRideTrustedContactRequest(val phoneNumber: String, val name: String)
data class SendStatusToTrustedContactsResponse(val success: Boolean, val sentCount: Int)

// Real Kakao T 대리운전 (designated driver, item 221) -- a professional driver comes
// to the customer's location and drives the CUSTOMER'S OWN CAR home for them, distinct
// from ride-hailing above (driver uses their own vehicle). Mirrors
// DesignatedDriver.kt/DesignatedDriverTrip.kt exactly.
data class DesignatedDriverDto(
    val id: String, val userId: String, val accountId: String, val licenseNumber: String, val available: Boolean,
    val currentLatitude: Double?, val currentLongitude: Double?, val createdAt: String,
)
data class DesignatedDriverResponse(val success: Boolean, val driver: DesignatedDriverDto?)
data class RegisterDesignatedDriverRequest(val licenseNumber: String)
data class SetDesignatedDriverAvailabilityRequest(val available: Boolean)
data class UpdateDesignatedDriverLocationRequest(val latitude: Double, val longitude: Double)
data class DesignatedDriverTripDto(
    val id: String, val customerId: String, val driverId: String?, val pickupAddress: String,
    val pickupLatitude: Double, val pickupLongitude: Double, val dropoffAddress: String,
    val dropoffLatitude: Double, val dropoffLongitude: Double, val vehicleMake: String, val vehicleModel: String,
    val vehiclePlate: String, val distanceKm: Double, val fare: java.math.BigDecimal,
    val platformFee: java.math.BigDecimal, val status: String, val createdAt: String,
)
data class DesignatedDriverTripResponse(val success: Boolean, val trip: DesignatedDriverTripDto)
data class DesignatedDriverTripsResponse(val success: Boolean, val trips: List<DesignatedDriverTripDto>, val page: Int = 0, val totalPages: Int = 1)
data class RequestDesignatedDriverTripRequest(
    val pickupAddress: String, val pickupLatitude: Double, val pickupLongitude: Double,
    val dropoffAddress: String, val dropoffLatitude: Double, val dropoffLongitude: Double,
    val vehicleMake: String, val vehicleModel: String, val vehiclePlate: String,
)

// Real Kakao T 바이크 (Kakao T Bike, item 222) -- real PEER-TO-PEER bike/scooter rental
// pool (any user self-registers a bike they own), billed by elapsed TIME at rental end
// -- distinct from ride-hailing/designated-driver, which both know their fare up front.
// Mirrors Bike.kt/BikeRentalSession.kt exactly.
data class RegisterBikeRequest(val type: String, val latitude: Double, val longitude: Double)
data class BikeDto(
    val id: String, val ownerUserId: String, val accountId: String, val type: String,
    val currentLatitude: Double, val currentLongitude: Double, val available: Boolean, val createdAt: String,
)
data class BikeResponse(val success: Boolean, val bike: BikeDto)
data class BikesResponse(val success: Boolean, val bikes: List<BikeDto>)
data class SetBikeAvailabilityRequest(val available: Boolean)
data class UpdateBikeLocationRequest(val latitude: Double, val longitude: Double)
data class StartBikeRentalRequest(val bikeId: String, val startLatitude: Double, val startLongitude: Double)
data class EndBikeRentalRequest(val endLatitude: Double, val endLongitude: Double)
data class BikeRentalSessionDto(
    val id: String, val bikeId: String, val riderUserId: String, val startedAt: String, val endedAt: String?,
    val startLatitude: Double, val startLongitude: Double, val endLatitude: Double?, val endLongitude: Double?,
    val durationMinutes: Int?, val totalFare: java.math.BigDecimal?, val platformFee: java.math.BigDecimal?,
    val status: String,
)
data class BikeRentalResponse(val success: Boolean, val rental: BikeRentalSessionDto)
data class BikeRentalsResponse(val success: Boolean, val rentals: List<BikeRentalSessionDto>, val page: Int = 0, val totalPages: Int = 1)

// Real Kakao T 주차 (Kakao T Parking, item 223) -- real PEER-TO-PEER parking-spot
// rental pool (any user self-lists a spot they own/control), billed by elapsed HOURS
// at checkout -- same "settle at end, no fare known up front" shape Bike already
// establishes, just hourly instead of per-minute. Mirrors ParkingSpot.kt/
// ParkingSession.kt exactly.
data class RegisterParkingSpotRequest(val address: String, val latitude: Double, val longitude: Double, val hourlyRate: java.math.BigDecimal)
data class ParkingSpotDto(
    val id: String, val ownerUserId: String, val accountId: String, val address: String,
    val latitude: Double, val longitude: Double, val hourlyRate: java.math.BigDecimal,
    val available: Boolean, val createdAt: String,
)
data class ParkingSpotResponse(val success: Boolean, val spot: ParkingSpotDto)
data class ParkingSpotsResponse(val success: Boolean, val spots: List<ParkingSpotDto>)
data class SetParkingSpotAvailabilityRequest(val available: Boolean)
data class StartParkingSessionRequest(val spotId: String)
data class ParkingSessionDto(
    val id: String, val spotId: String, val renterUserId: String, val startedAt: String, val endedAt: String?,
    val durationMinutes: Int?, val totalFare: java.math.BigDecimal?, val platformFee: java.math.BigDecimal?,
    val status: String,
)
data class ParkingSessionResponse(val success: Boolean, val session: ParkingSessionDto)
data class ParkingSessionsResponse(val success: Boolean, val sessions: List<ParkingSessionDto>, val page: Int = 0, val totalPages: Int = 1)

// Real Kakao T 시외버스 (intercity bus booking, item 224) -- real peer-to-peer
// coach-operator trip pool, fare charged in FULL at booking time (not settled at end
// like Parking/Bike, since a bus ticket's fare is known up front). Mirrors BusTrip.kt/
// BusBooking.kt exactly.
data class PostBusTripRequest(
    val origin: String, val destination: String, val departureTime: String,
    val totalSeats: Int, val farePerSeat: java.math.BigDecimal,
)
data class BusTripDto(
    val id: String, val operatorUserId: String, val accountId: String, val origin: String, val destination: String,
    val departureTime: String, val totalSeats: Int, val availableSeats: Int, val farePerSeat: java.math.BigDecimal,
    val createdAt: String,
)
data class BusTripResponse(val success: Boolean, val trip: BusTripDto)
data class BusTripsResponse(val success: Boolean, val trips: List<BusTripDto>)
data class BookBusSeatsRequest(val tripId: String, val seatCount: Int)
data class BusBookingDto(
    val id: String, val tripId: String, val riderUserId: String, val seatCount: Int,
    val totalFare: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val paymentTransactionId: String,
    val status: String, val refundTransactionId: String?, val createdAt: String,
)
data class BusBookingResponse(val success: Boolean, val booking: BusBookingDto)
data class BusBookingsResponse(val success: Boolean, val bookings: List<BusBookingDto>, val page: Int = 0, val totalPages: Int = 1)

// Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- see the
// backend's KnowledgeQuestion.kt/KnowledgeAnswer.kt doc comments for the full sourced
// account. A genuinely different shape from the trip/rental DTOs above -- no account
// movement, no location. Mirrors those entities' field names exactly.
data class KnowledgeCategory(val id: String, val label: String)
data class KnowledgeCategoriesResponse(val success: Boolean, val categories: List<KnowledgeCategory>)
data class PostKnowledgeQuestionRequest(val category: String, val title: String, val body: String)
data class KnowledgeQuestionDto(
    val id: String, val askerId: String, val category: String, val title: String, val body: String,
    val adoptedAnswerId: String?, val createdAt: String,
)
data class KnowledgeQuestionResponse(val success: Boolean, val question: KnowledgeQuestionDto)
data class KnowledgeQuestionsResponse(val success: Boolean, val questions: List<KnowledgeQuestionDto>, val page: Int = 0, val totalPages: Int = 1)
data class PostKnowledgeAnswerRequest(val body: String)
data class KnowledgeAnswerDto(
    val id: String, val questionId: String, val answererId: String, val body: String,
    val isAdopted: Boolean, val createdAt: String,
)
data class KnowledgeAnswerResponse(val success: Boolean, val answer: KnowledgeAnswerDto)
data class KnowledgeAnswersResponse(val success: Boolean, val answers: List<KnowledgeAnswerDto>, val page: Int = 0, val totalPages: Int = 1)
data class KnowledgeReputationResponse(val success: Boolean, val adoptedAnswerCount: Int)

// Real Kakao T-style post-trip driver rating (item 213) -- see the backend's
// RideTripReview.kt doc comment.
data class SubmitRideReviewRequest(val rating: Int, val comment: String? = null)
data class RideTripReviewDto(
    val id: String, val tripId: String, val passengerId: String, val driverId: String,
    val rating: Int, val comment: String?, val createdAt: String,
)
data class RideTripReviewResponse(val success: Boolean, val review: RideTripReviewDto)
data class RideDriverRatingResponse(val success: Boolean, val average: Double?, val count: Long)
data class RideDriverReviewsResponse(val success: Boolean, val reviews: List<RideTripReviewDto>)

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- mirrors
// bank-mfe's lib/vehicleInspection.ts exactly.
data class VehicleInspectionMechanicDto(
    val id: String, val userId: String, val accountId: String, val businessName: String,
    val available: Boolean, val createdAt: String,
)
data class RegisterInspectionMechanicRequest(val businessName: String)
data class SetInspectionMechanicAvailabilityRequest(val available: Boolean)
data class RequestVehicleInspectionRequest(val listingId: String, val mechanicId: String, val fee: java.math.BigDecimal, val scheduledFor: String)
data class CompleteVehicleInspectionRequest(val findings: String? = null)
data class VehicleInspectionBookingDto(
    val id: String, val listingId: String, val buyerId: String, val mechanicId: String,
    val fee: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val scheduledFor: String,
    val status: String, val findings: String?, val createdAt: String,
)
data class VehicleInspectionMechanicResponse(val success: Boolean, val mechanic: VehicleInspectionMechanicDto)
data class VehicleInspectionMechanicOrNullResponse(val success: Boolean, val mechanic: VehicleInspectionMechanicDto?)
data class VehicleInspectionMechanicsResponse(val success: Boolean, val mechanics: List<VehicleInspectionMechanicDto>)
data class VehicleInspectionBookingResponse(val success: Boolean, val booking: VehicleInspectionBookingDto)
data class VehicleInspectionBookingsResponse(val success: Boolean, val bookings: List<VehicleInspectionBookingDto>)

// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- mirrors
// bank-mfe's lib/vehicles.ts exactly.
data class VehicleDto(
    val id: String, val make: String, val model: String, val modelYear: Int,
    val purchasePrice: java.math.BigDecimal, val purchaseDate: String, val mileageKm: Int, val createdAt: String,
)
data class RegisterVehicleRequest(
    val make: String, val model: String, val modelYear: Int,
    val purchasePrice: java.math.BigDecimal, val purchaseDate: String, val mileageKm: Int,
)
data class UpdateVehicleMileageRequest(val mileageKm: Int)
data class VehicleValuationDto(
    val vehicle: VehicleDto, val ageYears: Int, val expectedMileageKm: Int,
    val currentEstimatedValue: java.math.BigDecimal, val estimatedValueIn1Year: java.math.BigDecimal,
    val estimatedValueIn2Years: java.math.BigDecimal, val estimatedValueIn3Years: java.math.BigDecimal,
)
data class VehicleResponse(val success: Boolean, val vehicle: VehicleDto)
data class VehiclesResponse(val success: Boolean, val vehicles: List<VehicleDto>)
data class VehicleValuationResponse(val success: Boolean, val valuation: VehicleValuationDto)

// Real Toss 유스 (Toss Youth)-style guardian-child account link -- mirrors bank-mfe's
// lib/family.ts exactly.
data class InviteChildRequest(val childPhoneNumber: String)
data class RespondToInviteRequest(val accept: Boolean)
data class FamilyLinkDto(
    val id: String, val guardianUserId: String, val childUserId: String,
    val status: String, val createdAt: String, val respondedAt: String?,
    // Real spend-limit enforcement (2026-07-27) -- see FamilyLinkService.setSpendLimit's
    // own doc comment on the backend. Already real-enforced against every P2P send a
    // child makes (P2pService.sendDirect calls enforceSpendLimit before the ledger
    // movement) -- but a guardian had no way to ever SET one until now, so the
    // enforcement path could never actually trigger.
    val dailySpendLimit: java.math.BigDecimal? = null,
)
data class FamilyLinkViewDto(val link: FamilyLinkDto, val guardianName: String, val childName: String)
data class ChildOverviewDto(val childUserId: String, val childName: String, val accountBalance: Double, val recentTransactions: List<TransactionDto>)
data class FamilyLinkResponse(val success: Boolean, val link: FamilyLinkDto)
data class FamilyLinksResponse(val success: Boolean, val invites: List<FamilyLinkDto>)
data class FamilyLinkViewsResponse(val success: Boolean, val children: List<FamilyLinkViewDto> = emptyList(), val guardians: List<FamilyLinkViewDto> = emptyList())
data class ChildOverviewResponse(val success: Boolean, val overview: ChildOverviewDto)
data class SetSpendLimitRequest(val dailySpendLimit: java.math.BigDecimal?)

data class SpendingCategoryDto(val name: String, val amount: java.math.BigDecimal)
data class SpendingInsightResponse(val success: Boolean, val categories: List<SpendingCategoryDto>, val totalSpent: java.math.BigDecimal)
data class BusinessExpenseSummaryResponse(val success: Boolean, val categories: List<SpendingCategoryDto>, val totalSpent: java.math.BigDecimal, val sinceMonthsAgo: Long)

data class SetBudgetRequest(val category: String? = null, val monthlyLimit: java.math.BigDecimal)
data class BudgetViewDto(
    val category: String?,
    val monthlyLimit: java.math.BigDecimal,
    val spent: java.math.BigDecimal,
    val remaining: java.math.BigDecimal,
    val percentUsed: Int,
    val status: String,
)
data class GetBudgetsResponse(val success: Boolean, val budgets: List<BudgetViewDto>)
data class BudgetSummaryDto(val category: String?, val monthlyLimit: java.math.BigDecimal)
data class SetBudgetResponse(val success: Boolean, val budget: BudgetSummaryDto)

data class ConfigureAutoTopUpRequest(
    val linkedAccountId: String,
    val thresholdAmount: java.math.BigDecimal,
    val topUpAmount: java.math.BigDecimal,
    val dailyTriggerCap: Int = 3,
    val enabled: Boolean = true,
)
data class AutoTopUpSettingDto(
    val id: String,
    val userId: String,
    val accountId: String,
    val linkedAccountId: String,
    val enabled: Boolean,
    val thresholdAmount: java.math.BigDecimal,
    val topUpAmount: java.math.BigDecimal,
    val dailyTriggerCap: Int,
    val triggersToday: Int,
    val lastTriggerDate: String? = null,
    val lastTriggeredAt: String? = null,
)
data class GetAutoTopUpSettingResponse(val success: Boolean, val setting: AutoTopUpSettingDto)
data class TriggerAutoTopUpResponse(val success: Boolean, val triggered: Boolean, val reason: String?)

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- mirrors
// GroupAccount.kt/GroupAccountService.kt exactly.
data class GroupAccountDto(val id: String, val name: String, val ownerId: String, val accountId: String, val monthlyDuesAmount: java.math.BigDecimal?, val createdAt: String)
data class GroupAccountMemberDto(val userId: String, val firstName: String, val lastName: String, val isOwner: Boolean, val joinedAt: String)
data class CreateGroupAccountRequest(val name: String)
data class CreateGroupAccountResponse(val success: Boolean, val groupAccount: GroupAccountDto)
data class GroupAccountsResponse(val success: Boolean, val groupAccounts: List<GroupAccountDto>)
data class GroupAccountDetailResponse(val success: Boolean, val groupAccount: GroupAccountDto, val balance: java.math.BigDecimal, val members: List<GroupAccountMemberDto>, val message: String? = null)
data class InviteMemberRequest(val phoneNumber: String)
data class InviteMemberResponse(val success: Boolean, val member: GroupAccountMemberDto)
data class GroupAccountAmountRequest(val amount: java.math.BigDecimal)
data class SetDuesAmountRequest(val amount: java.math.BigDecimal?)
data class GroupAccountDuesMemberDto(val userId: String, val firstName: String, val lastName: String, val contributedAmount: java.math.BigDecimal, val paid: Boolean)
data class GroupAccountDuesDto(val duesAmount: java.math.BigDecimal?, val cycleMonth: String, val members: List<GroupAccountDuesMemberDto>)
data class GroupAccountDuesResponse(val success: Boolean, val dues: GroupAccountDuesDto)
data class RemindUnpaidDuesResponse(val success: Boolean, val remindedCount: Int)

// Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See the
// backend's Ikimina.kt doc comment for the full sourced account. Distinct from
// GroupAccountDto above (Kakao Bank 모임통장): that feature has one permanent owner
// with sole withdrawal authority; an ikimina rotates the full pot to a different
// member each real round, until everyone has been paid exactly once. Genuinely the
// first feature in this codebase not sourced from Toss/Kakao/Naver/Coupang. Mirrors
// bank-mfe's lib/ikimina.ts exactly.
data class IkiminaDto(
    val id: String, val name: String, val organizerId: String, val accountId: String,
    val contributionAmount: java.math.BigDecimal, val cycleFrequencyDays: Int, val memberCap: Int,
    val currentRound: Int, val status: String, val createdAt: String,
)
data class IkiminaMemberDto(
    val userId: String, val firstName: String, val lastName: String,
    val payoutOrder: Int, val hasReceivedPayout: Boolean, val isOrganizer: Boolean,
)
data class IkiminaContributionStatusDto(val userId: String, val contributed: Boolean)
data class CreateIkiminaRequest(val name: String, val contributionAmount: java.math.BigDecimal, val cycleFrequencyDays: Int, val memberCap: Int)
// payout added 2026-09-05 -- IkiminaService.contributeThisRound's own doc comment:
// a contribution that completes the round auto-triggers the payout in the same
// call, non-null ONLY on the one contribution that completes a round. Nullable
// with a default so createIkimina/startIkiminaCycle (which reuse this same
// response shape but never send this field) are unaffected.
data class IkiminaPayoutInfo(val ikimina: IkiminaDto, val recipientUserId: String, val amount: java.math.BigDecimal)
data class CreateIkiminaResponse(val success: Boolean, val ikimina: IkiminaDto, val payout: IkiminaPayoutInfo? = null)
data class IkiminasResponse(val success: Boolean, val ikiminas: List<IkiminaDto>)
data class IkiminaDetailResponse(
    val success: Boolean, val ikimina: IkiminaDto, val balance: java.math.BigDecimal,
    val members: List<IkiminaMemberDto>, val currentRoundContributions: List<IkiminaContributionStatusDto>,
)
data class InviteIkiminaMemberRequest(val phoneNumber: String)
data class InviteIkiminaMemberResponse(val success: Boolean, val member: IkiminaMemberDto)
data class IkiminaPayoutResponse(val success: Boolean, val ikimina: IkiminaDto, val recipientUserId: String, val amount: java.math.BigDecimal)

// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model (416 real sector SACCOs, 4M+ members, RWF 200B+ deposits
// as of 2024). Distinct from IkiminaDto (informal rotating-pot ROSCA, no shares/
// dividends): a SACCO member buys real shares and receives periodic real dividend
// distributions tied to the pool's real performance. Mirrors bank-mfe's lib/sacco.ts
// exactly.
data class SaccoShareholdingDto(
    val id: String, val userId: String, val accountId: String,
    val sharesHeld: java.math.BigDecimal, val totalContributed: java.math.BigDecimal, val createdAt: String,
)
data class SaccoAmountRequest(val amount: java.math.BigDecimal)
data class SaccoShareholdingResponse(val success: Boolean, val shareholding: SaccoShareholdingDto?, val currentValue: java.math.BigDecimal?)
data class SaccoDividendPayoutDto(
    val id: String, val distributionId: String, val shareholdingId: String,
    val amount: java.math.BigDecimal, val payoutTransactionId: String, val createdAt: String,
)
data class SaccoDividendPayoutsResponse(val success: Boolean, val payouts: List<SaccoDividendPayoutDto>)

// Real Rwanda coffee-cooperative harvest-advance / input financing -- sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems, grounded in
// Rwanda's own real coffee sector (Rwanda Coffee Cooperatives Federation: 13 member
// cooperatives, ~19,000 producer members). A direct itunda-to-farmer lending
// relationship mirroring the regular Loans feature's own loan_payable receivable
// shape -- never a shared/pooled account. Mirrors bank-mfe's lib/harvestAdvance.ts
// exactly, including the post-fix repay contract (amount must equal the full real
// outstanding principal, no partial repayment).
data class CooperativeDto(
    val id: String, val name: String, val cropType: String, val registrationNumber: String?, val createdAt: String,
)
data class CooperativeMembershipDto(
    val id: String, val cooperativeId: String, val userId: String, val accountId: String,
    val memberSince: String, val active: Boolean,
)
data class HarvestAdvanceDto(
    val id: String, val membershipId: String, val accountId: String, val principalAmount: java.math.BigDecimal,
    val purpose: String, val expectedHarvestDate: String, val repaymentDueDate: String, val status: String,
    val disbursedAt: String?, val repaidAt: String?, val createdAt: String,
)
data class RegisterCooperativeRequest(val name: String, val cropType: String, val registrationNumber: String?)
data class RequestAdvanceRequest(
    val membershipId: String, val principalAmount: java.math.BigDecimal, val purpose: String, val expectedHarvestDate: String,
)
data class RepayAdvanceRequest(val amount: java.math.BigDecimal)
data class CooperativeResponse(val success: Boolean, val cooperative: CooperativeDto)
data class CooperativeMembershipResponse(val success: Boolean, val membership: CooperativeMembershipDto)
data class CooperativeMembershipsResponse(val success: Boolean, val memberships: List<CooperativeMembershipDto>)
data class CooperativeOverviewResponse(
    val success: Boolean, val cooperative: CooperativeDto, val myMembership: CooperativeMembershipDto, val memberCount: Int,
)
