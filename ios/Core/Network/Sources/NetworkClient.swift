import Foundation

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so JSONDecoder reads the real backend's JSON directly.
public struct RegisterRequest: Encodable {
    public let phoneNumber: String
    public let email: String?
    public let firstName: String
    public let lastName: String
    public let password: String
    public let referralCode: String?
    // Added 2026-07-21, same reasoning as LoginRequest's deviceId/deviceName -- the
    // device that registers proves password ownership in the same request, so it's
    // auto-trusted server-side (DeviceService.recordRegistrationDevice) with no
    // separate step-up needed.
    public let deviceId: String?
    public let deviceName: String?
    public init(phoneNumber: String, email: String?, firstName: String, lastName: String, password: String, referralCode: String?, deviceId: String?, deviceName: String?) { self.phoneNumber = phoneNumber; self.email = email; self.firstName = firstName; self.lastName = lastName; self.password = password; self.referralCode = referralCode; self.deviceId = deviceId; self.deviceName = deviceName }
}

// deviceId/deviceName added 2026-07-21 -- mirrors bank-mfe's real device-binding
// login call exactly. See DeviceStore.swift for how these are generated.
public struct LoginRequest: Encodable {
    public let phoneNumber: String
    public let password: String
    public let deviceId: String?
    public let deviceName: String?
    public init(phoneNumber: String, password: String, deviceId: String?, deviceName: String?) { self.phoneNumber = phoneNumber; self.password = password; self.deviceId = deviceId; self.deviceName = deviceName }
}

public struct RefreshRequest: Encodable {
    public let refreshToken: String
    public init(refreshToken: String) { self.refreshToken = refreshToken }
}

public struct LogoutRequest: Encodable {
    public let refreshToken: String?
    public init(refreshToken: String?) { self.refreshToken = refreshToken }
}

public struct PublicUser: Decodable {
    public let id: String
    public let phoneNumber: String
    public let email: String?
    public let firstName: String
    public let lastName: String
    public let kycVerified: Bool
    public let creditScore: Int
    public let createdAt: String
    // Real hyperlocal neighborhood (2026-07-20) -- see AuthService.setNeighborhood's own
    // doc comment. Set via a real coordinate, reverse-geocoded server-side; never
    // self-declared free text.
    public let neighborhood: String?
    public let neighborhoodVerifiedAt: String?
    public let neighborhoodVerificationCount: Int?
    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // MiniWalletService.kt's own doc comment. Set via NetworkClient.setBirthDate.
    public let birthDate: String?
    // Real email/phone verification (item 169/179) -- see AuthService.requestEmailVerification/
    // requestPhoneVerification's own doc comments. Backend has returned these on every
    // profile response since 2026-07-13/26; this client just never modeled them until now.
    public let emailVerified: Bool?
    public let phoneVerified: Bool?
    // Real profile photo (URL, not a binary upload) -- also the real, buildable half
    // of Rewards' task_profile. Found 2026-07-29 via a full-backend-endpoint sweep:
    // real, working endpoint with zero client anywhere, and this field wasn't even
    // carried by this DTO until now.
    public let profilePhotoUrl: String?
}

public struct UpdateProfilePhotoRequest: Encodable { public let profilePhotoUrl: String }

public struct ConfirmEmailVerificationRequest: Encodable { public let token: String }
public struct ConfirmPhoneVerificationRequest: Encodable { public let code: String }

public struct SetNeighborhoodRequest: Encodable {
    public let latitude: Double
    public let longitude: Double
}

public struct SetBirthDateRequest: Encodable {
    public let birthDate: String
}

// Real KakaoBank mini-style capped starter wallet -- see MiniWalletService.kt's own
// doc comment (real balance/daily/monthly caps plus a real 7-18 age-eligibility gate).
public struct OpenMiniWalletResponse: Decodable { public let success: Bool; public let wallet: Wallet }
public struct DepositMiniWalletRequest: Encodable { public let amount: Double }
public struct DepositMiniWalletResponse: Decodable { public let success: Bool; public let id: String; public let amount: Double; public let completedAt: String }

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- mirrors
// GroupAccount.kt/GroupAccountService.kt exactly.
public struct GroupAccountDto: Decodable {
    public let id: String
    public let name: String
    public let ownerId: String
    public let walletId: String
    public let monthlyDuesAmount: Double?
    public let createdAt: String
}
public struct GroupAccountMemberDto: Decodable {
    public let userId: String
    public let firstName: String
    public let lastName: String
    public let isOwner: Bool
    public let joinedAt: String
}
public struct CreateGroupAccountRequest: Encodable { public let name: String }
public struct CreateGroupAccountResponse: Decodable { public let success: Bool; public let groupAccount: GroupAccountDto }
public struct GroupAccountsResponse: Decodable { public let success: Bool; public let groupAccounts: [GroupAccountDto] }
public struct GroupAccountDetailResponse: Decodable {
    public let success: Bool
    public let groupAccount: GroupAccountDto
    public let balance: Double
    public let members: [GroupAccountMemberDto]
}
public struct InviteMemberRequest: Encodable { public let phoneNumber: String }
public struct InviteMemberResponse: Decodable { public let success: Bool; public let member: GroupAccountMemberDto }
public struct GroupAccountAmountRequest: Encodable { public let amount: Double }
public struct SetDuesAmountRequest: Encodable { public let amount: Double? }
public struct GroupAccountDuesMemberDto: Decodable {
    public let userId: String
    public let firstName: String
    public let lastName: String
    public let contributedAmount: Double
    public let paid: Bool
}
public struct GroupAccountDuesDto: Decodable { public let duesAmount: Double?; public let cycleMonth: String; public let members: [GroupAccountDuesMemberDto] }
public struct GroupAccountDuesResponse: Decodable { public let success: Bool; public let dues: GroupAccountDuesDto }
public struct RemindUnpaidDuesResponse: Decodable { public let success: Bool; public let remindedCount: Int }

public struct AuthResponse: Decodable {
    public let message: String
    public let user: PublicUser
    public let accessToken: String
    public let refreshToken: String
}

public enum NetworkError: Error {
    case invalidResponse
    case httpError(statusCode: Int)
    // Real device binding (2026-07-21 port) -- a new, purely additive case rather
    // than widening httpError's own arity (which every existing `catch let
    // NetworkError.httpError(statusCode)` site across this target would need
    // updating for -- exactly the "broader networking-layer change" TalkScreen.swift's
    // own errorMessage doc comment (2026-07-19) already named and deliberately
    // deferred). Thrown only from authenticatedPost's idempotency-keyed path below,
    // mirroring the backend's own DeviceVerificationFilter, which only ever gates
    // requests carrying a real Idempotency-Key header.
    case deviceNotVerified
    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- purely additive,
    // same rationale as deviceNotVerified above: thrown only from the Mini wallet's
    // own dedicated request methods, which decode the real ApiError.code on a 422.
    case miniWalletBirthDateRequired
    case miniWalletAgeIneligible
}

/// Real login/session flow (2026-07-11) -- this app previously had no networking
/// layer at all backing SessionManager/LoginScreen; Core/Network's BffClient
/// protocol (see Core/Network/Sources/BffClient.swift) is unused SDUI scaffolding,
/// not a real HTTP client, same as Android's core/network module before this
/// session's fix lived in :app instead. Plain URLSession, no third-party dependency,
/// matching how minimal the rest of this app's networking surface already is.
public final class NetworkClient {
    public static let shared = NetworkClient()

    // iOS Simulator shares the host Mac's network directly ("localhost" resolves to
    // the Mac itself), unlike the Android emulator's 10.0.2.2 alias -- no special
    // address needed here. A physical iOS device would need the host's real LAN IP
    // instead (see android/app/build.gradle.kts's apiBaseUrl comment for the Android
    // equivalent of this same problem).
    // static/internal (2026-07-16) so SaroniteBrownfieldModule can reuse the exact same
    // value rather than a second hardcoded literal that could drift out of sync.
    public static let baseURLString = "http://localhost:4001/"
    private let baseURL = URL(string: NetworkClient.baseURLString)!
    private let session = URLSession(configuration: .default)

    private lazy var encoder: JSONEncoder = JSONEncoder()
    private lazy var decoder: JSONDecoder = JSONDecoder()

    private init() {}

    public func register(_ request: RegisterRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/register", body: request, authToken: nil)
    }

    public func login(_ request: LoginRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login", body: request, authToken: nil)
    }

    public func refresh(_ request: RefreshRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/refresh", body: request, authToken: nil)
    }

    public func logout(accessToken: String, request: LogoutRequest) async throws {
        _ = try await sendRequest(path: "api/v1/auth/logout", body: request, authToken: accessToken)
    }

    private func post<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        authToken: String?
    ) async throws -> Response {
        let data = try await sendRequest(path: path, body: body, authToken: authToken)
        return try decoder.decode(Response.self, from: data)
    }

    @discardableResult
    private func sendRequest<Body: Encodable>(path: String, body: Body, authToken: String?) async throws -> Data {
        var urlRequest = URLRequest(url: baseURL.appendingPathComponent(path))
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let authToken {
            urlRequest.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
        urlRequest.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: urlRequest)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return data
    }
}

// Mirrors services/backend/core/.../domain/Wallet.kt / SavingsGoal.kt / InterestJar.kt
// exactly -- same field names, so JSONDecoder reads the real backend's JSON directly
// (2026-07-11, alongside BankView.swift's real-data wiring; same DTOs Android's
// ApiService.kt just gained).
public struct Wallet: Decodable {
    public let id: String
    public let userId: String
    public let accountNumber: String
    public let accountName: String
    public let type: String
    public let balance: Double
    public let availableBalance: Double
    public let currency: String
    public let isActive: Bool
}

public struct WalletsResponse: Decodable { public let success: Bool; public let wallets: [Wallet] }

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (item 160) -- see the
// backend's ForeignCurrencyWalletService.kt doc comment: scoped to USD/EUR/GBP, real
// live mid-market rate + a real 1.5% itunda margin, real double-entry conversion
// entirely between a user's own RWF and foreign-currency wallets. Reuses `Wallet`
// above for the foreign-currency wallet itself (same real domain shape, `type` ==
// "FOREIGN_CURRENCY"). Android's main app already has this (`ForeignCurrencyScreen.kt`);
// this is the iOS port -- bank-mfe got it in item 154.
public struct ForeignWalletsResponse: Decodable { public let success: Bool; public let wallets: [Wallet] }
public struct ExchangeRateResponse: Decodable { public let success: Bool; public let from: String; public let to: String; public let rate: Double }
public struct OpenForeignWalletRequest: Encodable {
    public let currency: String
    public init(currency: String) { self.currency = currency }
}
public struct ConvertCurrencyRequest: Encodable {
    public let fromCurrency: String; public let toCurrency: String; public let amount: Double
    public init(fromCurrency: String, toCurrency: String, amount: Double) {
        self.fromCurrency = fromCurrency; self.toCurrency = toCurrency; self.amount = amount
    }
}
public struct CurrencyConversionDto: Decodable, Identifiable {
    public let id: String
    public let fromCurrency: String
    public let toCurrency: String
    public let fromAmount: Double
    public let toAmount: Double
    public let rate: Double
    public let marginAmount: Double
}
public struct ConvertCurrencyResponse: Decodable { public let success: Bool; public let conversion: CurrencyConversionDto }
public struct CurrencyConversionsResponse: Decodable { public let success: Bool; public let conversions: [CurrencyConversionDto] }

// Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) equivalent
// (item 161) -- see UpfrontInterestDepositService.kt's own doc comment: the full year's
// 2.80% interest is paid immediately on opening, principal locks in its own dedicated
// wallet for a genuine 12-month term with deliberately no early withdrawal. Android's
// main app already has this (`UpfrontDepositScreen.kt`); bank-mfe got it in item 153.
// This is the iOS port.
public struct UpfrontDepositDto: Decodable, Identifiable {
    public let id: String
    public let principal: Double
    public let interestRate: Double
    public let interestPaid: Double
    public let status: String
    public let openedAt: String
    public let maturesAt: String
    public let maturedAt: String?
    public let withdrawnAt: String?
}
public struct UpfrontDepositsResponse: Decodable { public let success: Bool; public let deposits: [UpfrontDepositDto] }
public struct OpenUpfrontDepositResponse: Decodable { public let success: Bool; public let deposit: UpfrontDepositDto; public let message: String }
public struct OpenUpfrontDepositRequest: Encodable {
    public let principal: Double
    public init(principal: Double) { self.principal = principal }
}

// Real 배민오더-style table/QR in-store ordering (item 162) -- see
// DineInOrderController.kt/DineInOrderService.kt on the backend. Reuses the exact same
// real Merchant/MerchantProduct catalog and menu-option-group resolution EatsOrderService
// already established, but with no delivery address/rider at all. Android has this on
// both the consumer app (EatsScreen.kt's own DINE_IN checkout mode) and merchantapp
// (DineInScreen.kt); bank-mfe got the customer side in item 155. This is the iOS
// consumer-side port -- the iOS MerchantApp restaurant-side queue remains a real,
// separate, not-yet-started follow-up.
public struct DineInOrderItemRequest: Encodable {
    public let menuItemId: String; public let quantity: Int; public let selectedChoiceIds: [String]?
    public init(menuItemId: String, quantity: Int, selectedChoiceIds: [String]?) {
        self.menuItemId = menuItemId; self.quantity = quantity; self.selectedChoiceIds = selectedChoiceIds
    }
}
public struct PlaceDineInOrderRequest: Encodable {
    public let restaurantId: String; public let tableNumber: String; public let items: [DineInOrderItemRequest]; public let notes: String?
    public init(restaurantId: String, tableNumber: String, items: [DineInOrderItemRequest], notes: String?) {
        self.restaurantId = restaurantId; self.tableNumber = tableNumber; self.items = items; self.notes = notes
    }
}
public struct UpdateDineInOrderStatusRequest: Encodable {
    public let status: String
    public init(status: String) { self.status = status }
}
public struct DineInOrderDto: Decodable, Identifiable {
    public let id: String
    public let buyerId: String
    public let restaurantId: String
    public let tableNumber: String
    public let itemsSubtotal: Double
    public let platformFee: Double
    public let totalAmount: Double
    public let transactionId: String
    public let status: String
    public let notes: String?
    public let createdAt: String
    public let updatedAt: String
    public let refundTransactionId: String?
}
public struct DineInOrderItemDto: Decodable, Identifiable {
    public let id: String; public let orderId: String; public let productId: String; public let productName: String
    public let unitPrice: Double; public let quantity: Int; public let selectedOptionsJson: String?
}
public struct DineInOrderDetailResponse: Decodable { public let success: Bool; public let order: DineInOrderDto; public let items: [DineInOrderItemDto] }
public struct DineInOrdersResponse: Decodable { public let success: Bool; public let orders: [DineInOrderDto] }

public struct SpendingCategoryDto: Decodable { public let name: String; public let amount: Double }
public struct SpendingInsightResponse: Decodable { public let success: Bool; public let categories: [SpendingCategoryDto]; public let totalSpent: Double }

// Real Toss budgets/limits equivalent (item 165/173) -- WalletService.setBudget/
// getBudgets, exposed on the pre-existing WalletController. bank-mfe (item 165) and
// Android (item 172) already have this; this is the iOS port.
public struct SetBudgetRequest: Encodable { public let category: String?; public let monthlyLimit: Double }
public struct BudgetViewDto: Decodable, Identifiable {
    public var id: String { category ?? "__overall__" }
    public let category: String?
    public let monthlyLimit: Double
    public let spent: Double
    public let remaining: Double
    public let percentUsed: Int
    public let status: String
}
public struct GetBudgetsResponse: Decodable { public let success: Bool; public let budgets: [BudgetViewDto] }
public struct BudgetSummaryDto: Decodable { public let category: String?; public let monthlyLimit: Double }
public struct SetBudgetResponse: Decodable { public let success: Bool; public let budget: BudgetSummaryDto }

// Real Kakao T-style ride-hailing -- mirrors RideDriver.kt/RideTrip.kt exactly.
public struct RideDriverDto: Decodable {
    public let id: String
    public let userId: String
    public let walletId: String
    public let status: String
    public let available: Bool
    public let currentLatitude: Double?
    public let currentLongitude: Double?
    public let locationUpdatedAt: String?
}
public struct RideDriverResponse: Decodable { public let success: Bool; public let driver: RideDriverDto }
public struct SetRideDriverAvailabilityRequest: Encodable { public let available: Bool }
public struct UpdateRideDriverLocationRequest: Encodable { public let latitude: Double; public let longitude: Double }
public struct RideTripDto: Decodable {
    public let id: String
    public let passengerId: String
    public let driverId: String?
    public let pickupAddress: String
    public let pickupLatitude: Double
    public let pickupLongitude: Double
    public let dropoffAddress: String
    public let dropoffLatitude: Double
    public let dropoffLongitude: Double
    public let distanceKm: Double
    public let fare: Double
    public let platformFee: Double
    public let status: String
    public let createdAt: String
    // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- nil means an ASAP
    // request, unchanged from before.
    public let scheduledFor: String?
}
public struct RideTripResponse: Decodable { public let success: Bool; public let trip: RideTripDto }
public struct RideTripsResponse: Decodable { public let success: Bool; public let trips: [RideTripDto] }
// Real Kakao T-style multi-stop rides (item 214) -- see the backend's RideTripStop.kt
// doc comment.
public struct RideStopRequestDto: Encodable {
    public let address: String
    public let latitude: Double
    public let longitude: Double
    // Real cross-module construction (item 214) -- RideScreenView.swift builds these
    // directly from its own passenger-entered stop inputs, so a real explicit public
    // init is required, the same "public struct in a different module needs an
    // explicit public init" gotcha already found live for OverdraftAccountDto.
    public init(address: String, latitude: Double, longitude: Double) {
        self.address = address
        self.latitude = latitude
        self.longitude = longitude
    }
}
public struct RequestRideTripRequest: Encodable {
    public let pickupAddress: String
    public let pickupLatitude: Double
    public let pickupLongitude: Double
    public let dropoffAddress: String
    public let dropoffLatitude: Double
    public let dropoffLongitude: Double
    public let scheduledFor: String?
    public let stops: [RideStopRequestDto]?
}
public struct RideTripStopDto: Decodable, Identifiable {
    public let id: String
    public let tripId: String
    public let sequence: Int
    public let address: String
    public let latitude: Double
    public let longitude: Double
    public let arrivedAt: String?
}
public struct RideTripStopResponse: Decodable { public let success: Bool; public let stop: RideTripStopDto }
public struct RideTripStopsResponse: Decodable { public let success: Bool; public let stops: [RideTripStopDto] }

// Real Kakao T-style post-trip driver rating (item 213) -- see the backend's
// RideTripReview.kt doc comment.
public struct SubmitRideReviewRequest: Encodable { public let rating: Int; public let comment: String? }
public struct RideTripReviewDto: Decodable {
    public let id: String
    public let tripId: String
    public let passengerId: String
    public let driverId: String
    public let rating: Int
    public let comment: String?
    public let createdAt: String
}
public struct RideTripReviewResponse: Decodable { public let success: Bool; public let review: RideTripReviewDto }
public struct RideDriverRatingResponse: Decodable { public let success: Bool; public let average: Double?; public let count: Int }

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- mirrors
// bank-mfe's lib/vehicleInspection.ts exactly. bank-mfe and Android already have this;
// this is the first iOS client.
public struct VehicleInspectionMechanicDto: Decodable {
    public let id: String
    public let userId: String
    public let walletId: String
    public let businessName: String
    public let available: Bool
    public let createdAt: String
}
public struct RegisterInspectionMechanicRequest: Encodable {
    public let businessName: String
    public init(businessName: String) { self.businessName = businessName }
}
public struct SetInspectionMechanicAvailabilityRequest: Encodable {
    public let available: Bool
    public init(available: Bool) { self.available = available }
}
public struct RequestVehicleInspectionRequest: Encodable {
    public let listingId: String
    public let mechanicId: String
    public let fee: Double
    public let scheduledFor: String
    public init(listingId: String, mechanicId: String, fee: Double, scheduledFor: String) {
        self.listingId = listingId; self.mechanicId = mechanicId; self.fee = fee; self.scheduledFor = scheduledFor
    }
}
public struct CompleteVehicleInspectionRequest: Encodable {
    public let findings: String?
    public init(findings: String?) { self.findings = findings }
}
public struct VehicleInspectionBookingDto: Decodable, Identifiable {
    public let id: String
    public let listingId: String
    public let buyerId: String
    public let mechanicId: String
    public let fee: Double
    public let platformFee: Double
    public let scheduledFor: String
    public let status: String
    public let findings: String?
    public let createdAt: String
}
public struct VehicleInspectionMechanicResponse: Decodable { public let success: Bool; public let mechanic: VehicleInspectionMechanicDto }
public struct VehicleInspectionMechanicOrNullResponse: Decodable { public let success: Bool; public let mechanic: VehicleInspectionMechanicDto? }
public struct VehicleInspectionMechanicsResponse: Decodable { public let success: Bool; public let mechanics: [VehicleInspectionMechanicDto] }
public struct VehicleInspectionBookingResponse: Decodable { public let success: Bool; public let booking: VehicleInspectionBookingDto }
public struct VehicleInspectionBookingsResponse: Decodable { public let success: Bool; public let bookings: [VehicleInspectionBookingDto] }

// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- mirrors
// bank-mfe's lib/vehicles.ts exactly.
public struct VehicleDto: Decodable, Identifiable {
    public let id: String
    public let make: String
    public let model: String
    public let modelYear: Int
    public let purchasePrice: Double
    public let purchaseDate: String
    public let mileageKm: Int
    public let createdAt: String
}
public struct RegisterVehicleRequest: Encodable {
    public let make: String
    public let model: String
    public let modelYear: Int
    public let purchasePrice: Double
    public let purchaseDate: String
    public let mileageKm: Int
}
public struct UpdateVehicleMileageRequest: Encodable { public let mileageKm: Int }
public struct VehicleValuationDto: Decodable {
    public let vehicle: VehicleDto
    public let ageYears: Int
    public let expectedMileageKm: Int
    public let currentEstimatedValue: Double
    public let estimatedValueIn1Year: Double
    public let estimatedValueIn2Years: Double
    public let estimatedValueIn3Years: Double
}
public struct VehicleResponse: Decodable { public let success: Bool; public let vehicle: VehicleDto }
public struct VehiclesResponse: Decodable { public let success: Bool; public let vehicles: [VehicleDto] }
public struct VehicleValuationResponse: Decodable { public let success: Bool; public let valuation: VehicleValuationDto }

// Real Toss 유스 (Toss Youth)-style guardian-child account link -- mirrors bank-mfe's
// lib/family.ts exactly.
public struct InviteChildRequest: Encodable { public let childPhoneNumber: String }
public struct RespondToInviteRequest: Encodable { public let accept: Bool }
public struct FamilyLinkDto: Decodable, Identifiable {
    public let id: String
    public let guardianUserId: String
    public let childUserId: String
    public let status: String
    public let createdAt: String
    public let respondedAt: String?
}
public struct FamilyLinkViewDto: Decodable, Identifiable {
    public let link: FamilyLinkDto
    public let guardianName: String
    public let childName: String
    public var id: String { link.id }
}
public struct ChildOverviewDto: Decodable {
    public let childUserId: String
    public let childName: String
    public let walletBalance: Double
    public let recentTransactions: [TransactionDto]
}
public struct FamilyLinkResponse: Decodable { public let success: Bool; public let link: FamilyLinkDto }
public struct FamilyLinksResponse: Decodable { public let success: Bool; public let invites: [FamilyLinkDto] }
public struct FamilyLinkViewsResponse: Decodable { public let success: Bool; public let children: [FamilyLinkViewDto]?; public let guardians: [FamilyLinkViewDto]? }
public struct ChildOverviewResponse: Decodable { public let success: Bool; public let overview: ChildOverviewDto }

public struct SavingsGoal: Decodable {
    public let id: String
    public let userId: String
    public let walletId: String
    public let name: String
    public let targetAmount: Double
    public let currentAmount: Double
    public let monthlyContribution: Double
    public let interestRate: Double
    public let targetDate: String?
    public let category: String
    public let status: String
    public let color: String
}

public struct SavingsGoalsResponse: Decodable { public let success: Bool; public let goals: [SavingsGoal] }

// Real Kakao Pay 머니굴리기 round-up auto-saving -- mirrors RoundUpSettings.kt exactly.
public let ROUND_UP_INCREMENTS: [Double] = [100, 500, 1000]
public struct RoundUpSettingsDto: Decodable {
    public let id: String
    public let userId: String
    public let enabled: Bool
    public let roundToNearest: Double
    public let targetGoalId: String?
}
public struct RoundUpSettingsResponse: Decodable { public let success: Bool; public let settings: RoundUpSettingsDto? }
public struct SetRoundUpSettingsRequest: Encodable { public let enabled: Bool; public let roundToNearest: Double; public let targetGoalId: String? }

// Real 당근마켓 Keyword Alert (키워드 알림) -- mirrors bank-mfe's lib/marketplace.ts and
// Android's ApiService.kt exactly.
public struct AddKeywordAlertRequest: Encodable { public let keyword: String }
public struct KeywordAlertDto: Decodable, Identifiable { public let id: String; public let userId: String; public let keyword: String; public let createdAt: String }
public struct KeywordAlertResponse: Decodable { public let success: Bool; public let alert: KeywordAlertDto }
public struct KeywordAlertsResponse: Decodable { public let success: Bool; public let alerts: [KeywordAlertDto] }
public struct SetKeywordAlertQuietHoursRequest: Encodable { public let startTime: String; public let endTime: String; public let enabled: Bool }
public struct KeywordAlertQuietHoursDto: Decodable { public let id: String; public let userId: String; public let startTime: String; public let endTime: String; public let enabled: Bool }
public struct KeywordAlertQuietHoursResponse: Decodable { public let success: Bool; public let quietHours: KeywordAlertQuietHoursDto? }

public struct InterestJar: Decodable {
    public let userId: String
    public let walletId: String
    public let balance: Double
    public let rate: Double
    public let earnedThisMonth: Double
    public let earnedTotal: Double
}

public struct InterestJarResponse: Decodable { public let success: Bool; public let jar: InterestJar }

// Real curated promo rail -- see the backend's DiscoverController.kt. Purely
// informational/display, no click-through action or money movement. Android already
// has this (DiscoverSection in ItundaAppScreen.kt, found real on backend + Android with
// zero client anywhere else); this is the first iOS client.
public struct DiscoverItem: Decodable, Identifiable {
    public let id: String
    public let category: String
    public let title: String
    public let subtitle: String
    public let description: String
    public let color: String
    public let isNew: Bool
    public let badge: String?
}
public struct DiscoverResponse: Decodable { public let success: Bool; public let items: [DiscoverItem] }

/// Authenticated GET helper for feature screens that need to call the rest of
/// services/backend's API once logged in -- reads the bearer token from
/// KeychainTokenStore so callers never have to thread it through manually. First
/// real use (2026-07-11): BankView.swift's wallet/savings/interest-jar data, closing
/// the "iOS has no real feature data-fetching wired in" gap this comment used to name.
extension NetworkClient {
    public func getWallets() async throws -> WalletsResponse { try await get("api/v1/wallet") }

    public func openForeignWallet(_ request: OpenForeignWalletRequest) async throws -> ForeignWalletsResponse {
        try await authenticatedPost("api/v1/wallet/foreign-currency/wallets", body: request)
    }

    public func getForeignWallets() async throws -> ForeignWalletsResponse { try await get("api/v1/wallet/foreign-currency/wallets") }

    public func getExchangeRate(from: String, to: String) async throws -> ExchangeRateResponse {
        try await get("api/v1/wallet/foreign-currency/rate", query: [
            URLQueryItem(name: "from", value: from),
            URLQueryItem(name: "to", value: to),
        ])
    }

    public func convertCurrency(_ request: ConvertCurrencyRequest) async throws -> ConvertCurrencyResponse {
        try await authenticatedPost("api/v1/wallet/foreign-currency/convert", body: request)
    }

    public func getMyConversions() async throws -> CurrencyConversionsResponse { try await get("api/v1/wallet/foreign-currency/conversions") }

    public func getUpfrontDeposits() async throws -> UpfrontDepositsResponse { try await get("api/v1/upfront-deposits") }

    public func openUpfrontDeposit(_ request: OpenUpfrontDepositRequest) async throws -> OpenUpfrontDepositResponse {
        try await authenticatedPost("api/v1/upfront-deposits", body: request)
    }

    public func withdrawUpfrontDeposit(id: String) async throws -> OpenUpfrontDepositResponse {
        try await authenticatedPost("api/v1/upfront-deposits/\(id)/withdraw", body: EmptyBody())
    }

    // Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.wallet.
    // WalletService.getSpendingInsight, real since 2026-07-13) -- first iOS client for
    // this feature (item 108, found backend-only via a fresh matrix scan; bank-mfe/
    // Android ported the same day as items 106/107).
    public func getSpendingInsight() async throws -> SpendingInsightResponse { try await get("api/v1/wallet/spending") }

    public func getBudgets() async throws -> GetBudgetsResponse { try await get("api/v1/wallet/budgets") }

    public func setBudget(category: String?, monthlyLimit: Double) async throws -> SetBudgetResponse {
        try await authenticatedPost("api/v1/wallet/budgets", body: SetBudgetRequest(category: category, monthlyLimit: monthlyLimit))
    }

    // Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) --
    // first iOS client for this feature (item 110, found via a fresh matrix scan;
    // bank-mfe has had it since the same day, Android ported it the same day as item
    // 109). Mirrors bank-mfe's lib/rideshare.ts and Android's ApiService.kt exactly.
    public func registerAsRideDriver() async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/register", body: EmptyBody())
    }

    public func getMyRideDriverProfile() async throws -> RideDriverResponse { try await get("api/v1/rides/drivers/me") }

    public func setRideDriverAvailability(available: Bool) async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/availability", body: SetRideDriverAvailabilityRequest(available: available))
    }

    public func updateRideDriverLocation(latitude: Double, longitude: Double) async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/location", body: UpdateRideDriverLocationRequest(latitude: latitude, longitude: longitude))
    }

    public func requestRideTrip(
        pickupAddress: String, pickupLatitude: Double, pickupLongitude: Double,
        dropoffAddress: String, dropoffLatitude: Double, dropoffLongitude: Double,
        scheduledFor: String? = nil, stops: [RideStopRequestDto]? = nil
    ) async throws -> RideTripResponse {
        try await authenticatedPost(
            "api/v1/rides/trips",
            body: RequestRideTripRequest(
                pickupAddress: pickupAddress, pickupLatitude: pickupLatitude, pickupLongitude: pickupLongitude,
                dropoffAddress: dropoffAddress, dropoffLatitude: dropoffLatitude, dropoffLongitude: dropoffLongitude,
                scheduledFor: scheduledFor, stops: stops
            ),
            idempotencyKey: UUID().uuidString
        )
    }

    public func getAvailableRideTrips() async throws -> RideTripsResponse { try await get("api/v1/rides/trips/available") }
    public func getMyRideTrips() async throws -> RideTripsResponse { try await get("api/v1/rides/trips/my-trips") }
    public func getMyRideDriverTrips() async throws -> RideTripsResponse { try await get("api/v1/rides/trips/my-driver-trips") }

    public func acceptRideTrip(id: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/accept", body: EmptyBody())
    }
    public func declineRideTrip(id: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/decline", body: EmptyBody())
    }
    public func startRideTrip(id: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/start", body: EmptyBody())
    }
    public func completeRideTrip(id: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/complete", body: EmptyBody())
    }
    public func cancelRideTrip(id: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/cancel", body: EmptyBody())
    }

    // Real Kakao T 예약 호출 (scheduled ride booking, item 212)/multi-stop (item 214)/
    // driver rating (item 213) -- first iOS client for these three, backend and
    // bank-mfe/Android real since 2026-07-31. Mirrors Android's ApiService.kt exactly.
    public func getRideTripStops(tripId: String) async throws -> RideTripStopsResponse { try await get("api/v1/rides/trips/\(tripId)/stops") }

    public func arriveAtRideStop(tripId: String) async throws -> RideTripStopResponse {
        try await authenticatedPost("api/v1/rides/trips/\(tripId)/stops/arrive", body: EmptyBody())
    }

    public func submitRideReview(tripId: String, rating: Int, comment: String?) async throws -> RideTripReviewResponse {
        try await authenticatedPost("api/v1/rides/trips/\(tripId)/review", body: SubmitRideReviewRequest(rating: rating, comment: comment))
    }

    public func getRideDriverRating(driverId: String) async throws -> RideDriverRatingResponse { try await get("api/v1/rides/drivers/\(driverId)/rating") }

    // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
    // rw.itunda.marketplace.VehicleInspectionService's own doc comment. bank-mfe and
    // Android already have this; this is the first iOS client.
    public func registerAsInspectionMechanic(businessName: String) async throws -> VehicleInspectionMechanicResponse {
        try await authenticatedPost("api/v1/marketplace/inspections/mechanics/register", body: RegisterInspectionMechanicRequest(businessName: businessName))
    }
    public func getMyInspectionMechanicProfile() async throws -> VehicleInspectionMechanicOrNullResponse {
        try await get("api/v1/marketplace/inspections/mechanics/me")
    }
    public func getAvailableInspectionMechanics() async throws -> VehicleInspectionMechanicsResponse {
        try await get("api/v1/marketplace/inspections/mechanics")
    }
    public func setInspectionMechanicAvailability(available: Bool) async throws -> VehicleInspectionMechanicResponse {
        try await authenticatedPost("api/v1/marketplace/inspections/mechanics/availability", body: SetInspectionMechanicAvailabilityRequest(available: available))
    }
    public func requestVehicleInspection(listingId: String, mechanicId: String, fee: Double, scheduledFor: String) async throws -> VehicleInspectionBookingResponse {
        try await authenticatedPost(
            "api/v1/marketplace/inspections",
            body: RequestVehicleInspectionRequest(listingId: listingId, mechanicId: mechanicId, fee: fee, scheduledFor: scheduledFor),
            idempotencyKey: UUID().uuidString
        )
    }
    public func getMyInspectionBookings() async throws -> VehicleInspectionBookingsResponse { try await get("api/v1/marketplace/inspections/my-bookings") }
    public func getMyInspectionMechanicBookings() async throws -> VehicleInspectionBookingsResponse { try await get("api/v1/marketplace/inspections/my-mechanic-bookings") }
    public func acceptVehicleInspection(bookingId: String) async throws -> VehicleInspectionBookingResponse {
        try await authenticatedPost("api/v1/marketplace/inspections/\(bookingId)/accept", body: EmptyBody())
    }
    public func completeVehicleInspection(bookingId: String, findings: String?) async throws -> VehicleInspectionBookingResponse {
        try await authenticatedPost("api/v1/marketplace/inspections/\(bookingId)/complete", body: CompleteVehicleInspectionRequest(findings: findings))
    }
    public func cancelVehicleInspection(bookingId: String) async throws -> VehicleInspectionBookingResponse {
        try await authenticatedPost("api/v1/marketplace/inspections/\(bookingId)/cancel", body: EmptyBody())
    }

    // Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
    // rw.itunda.vehicle.VehicleValuationService's own doc comment. bank-mfe/Android
    // already have this; this is the first iOS client.
    public func registerVehicle(make: String, model: String, modelYear: Int, purchasePrice: Double, purchaseDate: String, mileageKm: Int) async throws -> VehicleResponse {
        try await authenticatedPost("api/v1/vehicles", body: RegisterVehicleRequest(make: make, model: model, modelYear: modelYear, purchasePrice: purchasePrice, purchaseDate: purchaseDate, mileageKm: mileageKm))
    }
    public func getMyVehicles() async throws -> VehiclesResponse { try await get("api/v1/vehicles") }
    public func getVehicleValuation(_ id: String) async throws -> VehicleValuationResponse { try await get("api/v1/vehicles/\(id)/valuation") }
    public func updateVehicleMileage(_ id: String, mileageKm: Int) async throws -> VehicleResponse {
        try await authenticatedPost("api/v1/vehicles/\(id)/mileage", body: UpdateVehicleMileageRequest(mileageKm: mileageKm))
    }
    public func removeVehicle(_ id: String) async throws -> SuccessResponse { try await authenticatedDelete("api/v1/vehicles/\(id)") }

    // Real Toss 유스 (Toss Youth)-style guardian-child account link -- see
    // rw.itunda.family.FamilyLinkService's own doc comment. Honest scope boundary: real
    // read-only spending oversight only, no new allowance mechanism. bank-mfe/Android
    // already have this; this is the first iOS client.
    public func inviteFamilyChild(childPhoneNumber: String) async throws -> FamilyLinkResponse {
        try await authenticatedPost("api/v1/family/invite", body: InviteChildRequest(childPhoneNumber: childPhoneNumber))
    }
    public func getMyFamilyInvites() async throws -> FamilyLinksResponse { try await get("api/v1/family/invites") }
    public func respondToFamilyInvite(_ id: String, accept: Bool) async throws -> FamilyLinkResponse {
        try await authenticatedPost("api/v1/family/invites/\(id)/respond", body: RespondToInviteRequest(accept: accept))
    }
    public func getMyFamilyChildren() async throws -> FamilyLinkViewsResponse { try await get("api/v1/family/children") }
    public func getMyFamilyGuardians() async throws -> FamilyLinkViewsResponse { try await get("api/v1/family/guardians") }
    public func getChildOverview(_ childUserId: String) async throws -> ChildOverviewResponse { try await get("api/v1/family/children/\(childUserId)/overview") }
    public func revokeFamilyLink(_ id: String) async throws -> FamilyLinkResponse {
        try await authenticatedPost("api/v1/family/links/\(id)/revoke", body: EmptyBody())
    }

    public func getSavingsGoals() async throws -> SavingsGoalsResponse { try await get("api/v1/savings/goals") }
    public func getInterestJar() async throws -> InterestJarResponse { try await get("api/v1/savings/interest-jar") }

    public func getDiscoverItems() async throws -> DiscoverResponse { try await get("api/v1/discover") }

    // Real Kakao Pay 머니굴리기 round-up auto-saving (rw.itunda.savings.RoundUpService,
    // real since well before this session) -- first iOS client for this feature (item
    // 113, found via a content-grep sweep: Android has a real client, bank-mfe ported
    // it the same day as item 112, iOS never did). Matches Android's own current scope
    // exactly -- goal destination only, not the newer (2026-07-27) stock-destination
    // option, which stays unwired on every client including Android's.
    public func getRoundUpSettings() async throws -> RoundUpSettingsResponse { try await get("api/v1/savings/round-up") }

    public func setRoundUpSettings(enabled: Bool, roundToNearest: Double, targetGoalId: String?) async throws -> RoundUpSettingsResponse {
        try await authenticatedPost("api/v1/savings/round-up", body: SetRoundUpSettingsRequest(enabled: enabled, roundToNearest: roundToNearest, targetGoalId: targetGoalId))
    }

    // Real 당근마켓 Keyword Alert (키워드 알림) -- first iOS client for this feature
    // (item 116, found via a content-grep sweep: bank-mfe had it since item 114,
    // Android ported it the same day as item 115, iOS never did). Mirrors bank-mfe's
    // lib/marketplace.ts and Android's ApiService.kt exactly.
    public func addKeywordAlert(keyword: String) async throws -> KeywordAlertResponse {
        try await authenticatedPost("api/v1/marketplace/keyword-alerts", body: AddKeywordAlertRequest(keyword: keyword))
    }
    public func getKeywordAlerts() async throws -> KeywordAlertsResponse { try await get("api/v1/marketplace/keyword-alerts") }
    public func removeKeywordAlert(id: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/marketplace/keyword-alerts/\(id)")
    }
    public func setKeywordAlertQuietHours(startTime: String, endTime: String, enabled: Bool) async throws -> KeywordAlertQuietHoursResponse {
        try await authenticatedPost("api/v1/marketplace/keyword-alerts/quiet-hours", body: SetKeywordAlertQuietHoursRequest(startTime: startTime, endTime: endTime, enabled: enabled))
    }
    public func getKeywordAlertQuietHours() async throws -> KeywordAlertQuietHoursResponse { try await get("api/v1/marketplace/keyword-alerts/quiet-hours") }
    public func getTransactionHistory() async throws -> TransactionHistoryResponse { try await get("api/v1/wallet/transactions") }
    // Real account settings screen (2026-07-12).
    public func getProfile() async throws -> ProfileResponse { try await get("api/v1/auth/profile") }

    // Real hyperlocal neighborhood (2026-07-20) -- a real coordinate in, reverse-geocoded
    // server-side into a real neighborhood/sector name. See AuthService.setNeighborhood
    // and bank-mfe's lib/neighborhood.ts, which this mirrors exactly.
    public func setNeighborhood(latitude: Double, longitude: Double) async throws -> ProfileResponse {
        try await authenticatedPost("api/v1/auth/profile/neighborhood", body: SetNeighborhoodRequest(latitude: latitude, longitude: longitude))
    }

    // Real profile photo (URL, not a binary upload) -- see PublicUser.profilePhotoUrl's
    // own doc comment.
    public func updateProfilePhoto(profilePhotoUrl: String) async throws -> ProfileResponse {
        try await authenticatedPut("api/v1/auth/profile/photo", body: UpdateProfilePhotoRequest(profilePhotoUrl: profilePhotoUrl))
    }

    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // AuthService.setBirthDate's own doc comment. birthDate is an ISO-8601 date
    // string ("YYYY-MM-DD").
    public func setBirthDate(_ birthDate: String) async throws -> ProfileResponse {
        try await authenticatedPost("api/v1/auth/profile/birth-date", body: SetBirthDateRequest(birthDate: birthDate))
    }

    // Real email/phone verification (item 169/179) -- see AuthService.requestEmailVerification/
    // requestPhoneVerification's own doc comments: a real code is delivered via a real
    // in-app Notification + push, no real SMS/email gateway exists. bank-mfe (item 169)
    // and Android (item 178) already have this; this is the iOS port.
    public func requestEmailVerification() async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/auth/profile/verify-email", body: EmptyBody())
    }

    public func confirmEmailVerification(token: String) async throws -> ProfileResponse {
        try await authenticatedPost("api/v1/auth/profile/verify-email/confirm", body: ConfirmEmailVerificationRequest(token: token))
    }

    public func requestPhoneVerification() async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/auth/profile/verify-phone", body: EmptyBody())
    }

    public func confirmPhoneVerification(code: String) async throws -> ProfileResponse {
        try await authenticatedPost("api/v1/auth/profile/verify-phone/confirm", body: ConfirmPhoneVerificationRequest(code: code))
    }

    // Real KakaoBank mini-style capped starter wallet (rw.itunda.wallet.
    // MiniWalletService, 2026-07-28) -- first iOS client for this feature (item 101),
    // mirroring bank-mfe's lib/miniWallet.ts and Android's ApiService.kt equivalents.
    // Neither endpoint carries an Idempotency-Key (MiniWalletController.kt declares
    // none), so this uses its own dedicated request path rather than authenticatedPost's
    // idempotency-gated one, decoding the real ApiError.code directly on a 422 instead.
    public func openMiniWallet() async throws -> OpenMiniWalletResponse {
        try await postMiniWallet("api/v1/wallet/mini/open", body: EmptyBody())
    }

    public func depositMiniWallet(amount: Double) async throws -> DepositMiniWalletResponse {
        try await postMiniWallet("api/v1/wallet/mini/deposit", body: DepositMiniWalletRequest(amount: amount))
    }

    // Real Kakao Bank 모임통장 (group/shared account) equivalent -- first iOS client for
    // this feature (item 105, found via a fresh matrix scan: zero client on either
    // mobile platform despite being real on bank-mfe well before this session; Android
    // ported the same day, item 104). Mirrors lib/groupAccounts.ts exactly.
    public func createGroupAccount(name: String) async throws -> CreateGroupAccountResponse {
        try await authenticatedPost("api/v1/group-accounts", body: CreateGroupAccountRequest(name: name))
    }

    public func getMyGroupAccounts() async throws -> GroupAccountsResponse { try await get("api/v1/group-accounts") }

    public func getGroupAccount(id: String) async throws -> GroupAccountDetailResponse {
        try await get("api/v1/group-accounts/\(id)")
    }

    public func inviteGroupAccountMember(id: String, phoneNumber: String) async throws -> InviteMemberResponse {
        try await authenticatedPost("api/v1/group-accounts/\(id)/members", body: InviteMemberRequest(phoneNumber: phoneNumber))
    }

    public func depositToGroupAccount(id: String, amount: Double) async throws -> GroupAccountDetailResponse {
        try await authenticatedPost(
            "api/v1/group-accounts/\(id)/deposit", body: GroupAccountAmountRequest(amount: amount), idempotencyKey: UUID().uuidString
        )
    }

    public func withdrawFromGroupAccount(id: String, amount: Double) async throws -> GroupAccountDetailResponse {
        try await authenticatedPost(
            "api/v1/group-accounts/\(id)/withdraw", body: GroupAccountAmountRequest(amount: amount), idempotencyKey: UUID().uuidString
        )
    }

    public func setGroupAccountDuesAmount(id: String, amount: Double?) async throws -> CreateGroupAccountResponse {
        try await authenticatedPut("api/v1/group-accounts/\(id)/dues", body: SetDuesAmountRequest(amount: amount))
    }

    public func getGroupAccountDues(id: String) async throws -> GroupAccountDuesResponse {
        try await get("api/v1/group-accounts/\(id)/dues")
    }

    public func requestUnpaidGroupAccountDues(id: String) async throws -> RemindUnpaidDuesResponse {
        try await authenticatedPost("api/v1/group-accounts/\(id)/dues/remind", body: EmptyBody())
    }

    private func authenticatedPut<Body: Encodable, Response: Decodable>(_ path: String, body: Body) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return try decoder.decode(Response.self, from: data)
    }

    private func postMiniWallet<Body: Encodable, Response: Decodable>(_ path: String, body: Body) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            if let errorBody = try? decoder.decode(ApiErrorBody.self, from: data) {
                switch errorBody.code {
                case "MINI_WALLET_BIRTH_DATE_REQUIRED": throw NetworkError.miniWalletBirthDateRequired
                case "MINI_WALLET_AGE_INELIGIBLE": throw NetworkError.miniWalletAgeIneligible
                default: break
                }
            }
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }
    public func getNotifications() async throws -> NotificationsResponse { try await get("api/v1/notifications") }

    public func markNotificationRead(_ id: String) async throws {
        _ = try await authenticatedPost("api/v1/notifications/\(id)/read", body: EmptyBody()) as MarkReadResponse
    }

    private func get<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Real query-param GET (2026-07-19) -- `get(_:)` above uses
    /// `appendingPathComponent`, which percent-encodes `?`/`=`/`&` and breaks a query
    /// string (same gotcha `searchDeliveryAddress` already worked around inline); this
    /// is the reusable version of that same fix for any future query-param endpoint.
    private func get<Response: Decodable>(_ path: String, query: [URLQueryItem]) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.filter { $0.value != nil && !($0.value!.isEmpty) }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Authenticated POST, with an optional Idempotency-Key -- every money-moving
    /// call below needs one so a retried tap after a timeout replays the original
    /// result instead of double-spending, same contract as Android's equivalent.
    fileprivate func authenticatedPost<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        idempotencyKey: String? = nil
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let idempotencyKey {
            request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            // Real device binding (2026-07-21 port) -- only checked when this call
            // actually carries an Idempotency-Key, the same real signal the backend's
            // own DeviceVerificationFilter gates on, so this never misclassifies an
            // unrelated 403 on a non-money-moving call as device-not-verified.
            if idempotencyKey != nil, httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }
}

private struct ApiErrorBody: Decodable { let code: String? }

// Mirrors services/backend/wallet's WalletController.kt/TransferQuote.kt and
// services/backend/savings's SavingsController.kt exactly (2026-07-12) -- wires
// the real send-money and savings deposit/claim flows, same DTOs Android's
// ApiService.kt just gained.
public struct QuoteTransferRequest: Encodable {
    public let amount: Double
    public let recipient: String
}

public struct TransferQuoteDto: Decodable {
    public let id: String
    public let fromWalletId: String
    public let recipient: String
    public let amount: Double
    public let fee: Double
    public let totalDebit: Double
    public let currency: String
    public let expiresAt: String
}

public struct QuoteTransferResponse: Decodable { public let success: Bool; public let quote: TransferQuoteDto }

public struct ConfirmTransferRequest: Encodable { public let quoteId: String }

public struct TransactionDto: Decodable {
    public let id: String
    public let senderId: String
    public let recipientId: String
    public let type: String
    public let amount: Double
    public let fee: Double
    public let currency: String
    public let status: String
    public let description: String
    public let createdAt: String
}

public struct TransactionHistoryResponse: Decodable { public let success: Bool; public let transactions: [TransactionDto] }

public struct ConfirmTransferResponse: Decodable {
    public let success: Bool
    public let message: String
    public let transaction: TransactionDto
    public let newBalance: Double
}

// Real direct itunda-to-itunda push-transfer (rw.itunda.p2p, 2026-07-20) -- mirrors
// P2pController's real SendDirectP2pRequest exactly, same as Android's ApiService.kt.
// Deliberately distinct from QuoteTransferRequest/ConfirmTransferRequest above: those
// always route through a simulated external rail and never actually credit another
// itunda user's wallet, even when the recipient is a real itunda account (confirmed via
// a direct MySQL check while building the real fix on the backend one day earlier). This
// is the real one -- no quote step needed, since there's no external rail decision to
// quote.
public struct SendDirectP2pRequest: Encodable { public let recipient: String; public let amount: Double; public let description: String }

// Real fixed-amount person-to-person payment request (item 171) -- the P2P
// counterpart to a merchant's own PaymentIntent (see backend P2pPaymentRequest.kt's
// own doc comment). A real 15-minute-expiring code the requester shares; anyone who
// has the code can pay it directly, real wallet-to-wallet, no fee. Real (rate-limited,
// tested, live-verified against a running backend) but had zero client anywhere until
// bank-mfe/item 167 and Android/item 170 the same session -- this is the iOS port.
public struct GenerateP2pRequest: Encodable { public let amount: Double; public let description: String }
public struct P2pPaymentRequestDto: Decodable, Identifiable {
    public let id: String
    public let requesterUserId: String
    public let amount: Double
    public let description: String
    public let status: String
    public let expiresAt: String
    public let completedTransactionId: String?
    public let paidByUserId: String?
    public let createdAt: String
}
public struct GenerateP2pRequestResponse: Decodable { public let success: Bool; public let request: P2pPaymentRequestDto }
public struct GetP2pRequestsResponse: Decodable { public let success: Bool; public let requests: [P2pPaymentRequestDto] }
public struct PayP2pRequestResponse: Decodable { public let success: Bool; public let message: String; public let transaction: TransactionDto; public let newBalance: Double }

public struct SendDirectP2pResponse: Decodable {
    public let success: Bool
    public let message: String
    public let transaction: TransactionDto
    public let newBalance: Double
}

// Real Toss Bank 자동이체 (auto-transfer) equivalent (2026-07-24 port) -- mirrors
// AutoTransferController's real DTOs exactly (see AutoTransfer.kt/AutoTransferController.kt
// on the backend for the full account), same shapes Android's ApiService.kt already
// gained the same day. Execution reuses P2pService.sendDirect's exact real ledger
// movement, just triggered by a scheduler instead of a direct tap -- recipientIdentifier
// is resolved fresh on every real execution, recipientName is a cached display label only.
public enum AutoTransferFrequency: String, Codable { case WEEKLY, MONTHLY }
public struct AutoTransferDto: Decodable, Identifiable {
    public let id: String
    public let recipientIdentifier: String
    public let recipientName: String
    public let amount: Double
    public let frequency: AutoTransferFrequency
    public let dayOfWeek: Int?
    public let dayOfMonth: Int?
    public let description: String
    public let status: String
    public let nextExecutionAt: String
    public let lastExecutedAt: String?
    public let executionCount: Int
    public let lastFailureReason: String?
}
public struct CreateAutoTransferRequest: Encodable {
    public let recipient: String
    public let amount: Double
    public let frequency: AutoTransferFrequency
    public let dayOfWeek: Int?
    public let dayOfMonth: Int?
    public let description: String
}
public struct AutoTransferResponse: Decodable { public let success: Bool; public let autoTransfer: AutoTransferDto }
public struct AutoTransfersListResponse: Decodable { public let success: Bool; public let autoTransfers: [AutoTransferDto] }

// Real Toss 사기계좌 조회 (fraud-account lookup before transfer) -- see backend
// ScamReportService's own doc comment. itunda's own crowd-sourced report registry,
// not a real police-database integration. Real on bank-mfe/Android only until now
// (2026-07-31).
public struct ScamReportDto: Decodable { public let id: String; public let reporterId: String; public let reportedIdentifier: String; public let reason: String; public let createdAt: String }
public struct ScamCheckResultDto: Decodable { public let identifier: String; public let reportCount: Int; public let warn: Bool }
public struct ReportScamRequest: Encodable { public let identifier: String; public let reason: String }
public struct ScamCheckResponse: Decodable { public let success: Bool; public let result: ScamCheckResultDto }
public struct ScamReportResponse: Decodable { public let success: Bool; public let report: ScamReportDto }
public struct ScamReportsListResponse: Decodable { public let success: Bool; public let reports: [ScamReportDto] }

public struct DepositRequest: Encodable { public let goalId: String; public let amount: Double }
public struct DepositResponse: Decodable { public let success: Bool; public let message: String; public let goal: SavingsGoal }
public struct ClaimInterestResponse: Decodable { public let success: Bool; public let message: String }

// Real offline-action-queue replay (2026-07-13) -- mirrors
// services/backend/offline/src/main/kotlin/rw/itunda/offline/web/ActionsBatchController.kt
// exactly. See OfflineActionQueue.swift for the local persisted queue this replays.
// Scoped to SAVINGS_DEPOSIT's real body shape rather than a generic [String: Any]
// body -- see OfflineActionQueue.swift's header for why that's the right scope here.
public struct SavingsDepositActionBody: Encodable { public let goalId: String; public let amount: Double; public init(goalId: String, amount: Double) { self.goalId = goalId; self.amount = amount } }

public struct BatchActionRequest: Encodable {
    public let clientActionId: String
    public let type: String
    public let idempotencyKey: String
    public let body: SavingsDepositActionBody
    public init(clientActionId: String, type: String, idempotencyKey: String, body: SavingsDepositActionBody) { self.clientActionId = clientActionId; self.type = type; self.idempotencyKey = idempotencyKey; self.body = body }
}

public struct BatchRequest: Encodable {  public let actions: [BatchActionRequest]; public init(actions: [BatchActionRequest]) { self.actions = actions } }

public struct BatchActionResultDto: Decodable {
    public let clientActionId: String
    public let type: String
    public let status: Int
}

public struct BatchResponse: Decodable { public let success: Bool; public let results: [BatchActionResultDto] }

// Mirrors services/backend/auth's AuthController.kt / notifications's
// NotificationController.kt (2026-07-12) -- backs the new Settings screen.
public struct ProfileResponse: Decodable { public let success: Bool; public let user: PublicUser }

public struct NotificationDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let type: String
    public let title: String
    public let body: String
    public let isRead: Bool
    public let createdAt: String
}

public struct NotificationsResponse: Decodable { public let success: Bool; public let notifications: [NotificationDto]; public let unreadCount: Int }
public struct MarkReadResponse: Decodable { public let success: Bool }

// Real device binding (2026-07-21 port) -- mirrors bank-mfe's lib/device.ts /
// Android's TrustedDeviceDto exactly (same real endpoints, same shapes). See
// AuthController.kt on the backend for the real contract: verify always re-verifies
// the CURRENT device (resolved server-side from the caller's own JWT deviceId claim,
// never a client-supplied one), so no id is passed in VerifyDeviceRequest.
public struct TrustedDeviceDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let deviceId: String
    public let deviceName: String?
    public let trusted: Bool
    public let firstSeenAt: String
    public let lastSeenAt: String
    public let verifiedAt: String?
}
public struct DevicesResponse: Decodable { public let success: Bool; public let devices: [TrustedDeviceDto] }
public struct VerifyDeviceRequest: Encodable { public let password: String }
public struct VerifyDeviceResponse: Decodable { public let success: Bool; public let device: TrustedDeviceDto }
public struct RevokeDeviceResponse: Decodable { public let success: Bool }

// Real push device-token registration (item 121) -- see backend DeviceToken.kt's own
// doc comment: PushNotificationService.sendToUser silently no-ops for every real user
// because no client anywhere ever registered a token. Mirrors bank-mfe's
// registerDeviceToken (item 119) and Android's (item 120) exactly.
public enum DevicePlatform: String, Encodable { case android = "ANDROID", ios = "IOS", web = "WEB" }
public struct RegisterDeviceTokenRequest: Encodable {
    public let platform: DevicePlatform
    public let token: String
    public init(platform: DevicePlatform, token: String) {
        self.platform = platform
        self.token = token
    }
}

extension NetworkClient {
    public func getMyDevices() async throws -> DevicesResponse { try await get("api/v1/auth/devices") }

    public func verifyDevice(password: String) async throws -> VerifyDeviceResponse {
        try await authenticatedPost("api/v1/auth/devices/verify", body: VerifyDeviceRequest(password: password))
    }

    public func revokeDevice(deviceId: String) async throws -> RevokeDeviceResponse {
        try await authenticatedDelete("api/v1/auth/devices/\(deviceId)")
    }

    public func registerDeviceToken(_ request: RegisterDeviceTokenRequest) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/notifications/device-tokens", body: request)
    }
}

extension NetworkClient {
    public func quoteTransfer(amount: Double, recipient: String) async throws -> QuoteTransferResponse {
        try await authenticatedPost("api/v1/wallet/transfer/quote", body: QuoteTransferRequest(amount: amount, recipient: recipient))
    }

    public func confirmTransfer(quoteId: String) async throws -> ConfirmTransferResponse {
        try await authenticatedPost(
            "api/v1/wallet/transfer/confirm",
            body: ConfirmTransferRequest(quoteId: quoteId),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real direct P2P push-transfer (2026-07-20) -- see SendDirectP2pRequest's own doc
    // comment for why this replaces quoteTransfer/confirmTransfer above in
    // TransferViewModel.sendTransfer.
    public func sendDirect(recipient: String, amount: Double) async throws -> SendDirectP2pResponse {
        try await authenticatedPost(
            "api/v1/p2p/send",
            body: SendDirectP2pRequest(recipient: recipient, amount: amount, description: ""),
            idempotencyKey: UUID().uuidString
        )
    }

    public func generateP2pRequest(amount: Double, description: String) async throws -> GenerateP2pRequestResponse {
        try await authenticatedPost("api/v1/p2p/request", body: GenerateP2pRequest(amount: amount, description: description))
    }

    public func getMyP2pRequests() async throws -> GetP2pRequestsResponse { try await get("api/v1/p2p/requests") }

    public func payP2pRequest(requestId: String) async throws -> PayP2pRequestResponse {
        try await authenticatedPost("api/v1/p2p/pay/\(requestId)", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    // Real Toss Bank 자동이체 (auto-transfer) equivalent (2026-07-24 port) -- see
    // AutoTransferDto's own doc comment above. No idempotency key on create/pause/resume/
    // cancel (unlike sendDirect/confirmTransfer): these mutate a schedule row, not a wallet
    // balance directly, matching AutoTransferController's own real endpoints exactly (none
    // of the five read an Idempotency-Key header).
    public func createAutoTransfer(
        recipient: String, amount: Double, frequency: AutoTransferFrequency,
        dayOfWeek: Int?, dayOfMonth: Int?, description: String
    ) async throws -> AutoTransferResponse {
        try await authenticatedPost(
            "api/v1/p2p/auto-transfers",
            body: CreateAutoTransferRequest(recipient: recipient, amount: amount, frequency: frequency, dayOfWeek: dayOfWeek, dayOfMonth: dayOfMonth, description: description)
        )
    }

    public func getMyAutoTransfers() async throws -> AutoTransfersListResponse { try await get("api/v1/p2p/auto-transfers") }

    public func pauseAutoTransfer(_ id: String) async throws -> AutoTransferResponse {
        try await authenticatedPost("api/v1/p2p/auto-transfers/\(id)/pause", body: EmptyBody())
    }

    public func resumeAutoTransfer(_ id: String) async throws -> AutoTransferResponse {
        try await authenticatedPost("api/v1/p2p/auto-transfers/\(id)/resume", body: EmptyBody())
    }

    public func cancelAutoTransfer(_ id: String) async throws -> AutoTransferResponse {
        try await authenticatedDelete("api/v1/p2p/auto-transfers/\(id)")
    }

    public func checkScamStatus(identifier: String) async throws -> ScamCheckResponse {
        try await get("api/v1/p2p/scam-reports/check?identifier=\(identifier.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? identifier)")
    }

    public func reportScam(identifier: String, reason: String) async throws -> ScamReportResponse {
        try await authenticatedPost("api/v1/p2p/scam-reports", body: ReportScamRequest(identifier: identifier, reason: reason))
    }

    public func getMyScamReports() async throws -> ScamReportsListResponse { try await get("api/v1/p2p/scam-reports/mine") }

    public func depositToGoal(goalId: String, amount: Double) async throws -> DepositResponse {
        try await authenticatedPost(
            "api/v1/savings/deposit",
            body: DepositRequest(goalId: goalId, amount: amount),
            idempotencyKey: UUID().uuidString
        )
    }

    public func claimInterest() async throws -> ClaimInterestResponse {
        try await authenticatedPost(
            "api/v1/savings/interest-jar/claim",
            body: EmptyBody(),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real offline-action-queue replay (2026-07-13) -- see OfflineActionQueue.swift.
    // No Idempotency-Key header on the batch call itself -- each individual action
    // inside it carries its own, exactly like the backend controller expects.
    public func submitActionBatch(_ request: BatchRequest) async throws -> BatchResponse {
        try await authenticatedPost("api/v1/actions/batch", body: request)
    }
}

private struct EmptyBody: Encodable {}

// MARK: - Messaging / Marketplace / Commerce (2026-07-18)
//
// Mirrors android/app/.../network/ApiService.kt's real DTOs exactly, field-for-field --
// the same three new backend modules (rw.itunda.messaging/marketplace/commerce) that
// Android's Home/Shop/Hood/Talk/My nav redesign wired up. See that file's own header
// comment for the full backend account (real pagination, real IDOR protection, honest
// "poll-based delivery"/"self-declared fulfillment" scope).

public struct ConversationDto: Decodable {
    public let id: String
    public let participantAId: String
    public let participantBId: String
    public let lastMessageAt: String
    public let createdAt: String
}

public struct ConversationSummaryDto: Decodable, Identifiable {
    public let conversationId: String
    public let otherUserId: String
    public let otherUserName: String
    public let lastMessageAt: String
    public let lastMessagePreview: String?
    public let unreadCount: Int
    public let quiet: Bool?
    public let pinnedMessageId: String?
    public var id: String { conversationId }
}

// Real emoji reactions (2026-07-19) -- see MessagingService.toggleReaction's own doc
// comment for the real toggle semantics (tapping an active reaction removes it).
public struct ReactionGroupDto: Decodable { public let emoji: String; public let userIds: [String] }

public struct MessageDto: Decodable, Identifiable {
    public let id: String
    public let conversationId: String
    public let senderId: String
    public let body: String
    public let sentAt: String
    public let readAt: String?
    public let deletedAt: String?
    public let replyToMessageId: String? = nil
    public let reactions: [ReactionGroupDto]
    // Real KakaoTalk Emoticon Store (item 136) -- see EmoticonPackDto's own doc
    // comment. Only ever set on a message actually created via the real
    // /api/v1/emoticons/.../send endpoints.
    public let emoticonId: String?

    // A custom init(from:) below suppresses Swift's automatic memberwise initializer,
    // so this is needed explicitly for real call sites that construct a MessageDto
    // directly (e.g. applying a real-time reaction push to already-loaded state).
    public init(id: String, conversationId: String, senderId: String, body: String, sentAt: String, readAt: String?, reactions: [ReactionGroupDto]) {
        self.id = id
        self.conversationId = conversationId
        self.senderId = senderId
        self.body = body
        self.sentAt = sentAt
        self.readAt = readAt
        self.deletedAt = nil
        self.reactions = reactions
        self.emoticonId = nil
    }

    // Custom decode: the real-time WebSocket push for a brand-new message omits
    // `reactions` entirely (a message can't have a reaction the instant it's sent) --
    // defaults to empty rather than failing to decode the whole push.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        conversationId = try container.decode(String.self, forKey: .conversationId)
        senderId = try container.decode(String.self, forKey: .senderId)
        body = try container.decode(String.self, forKey: .body)
        sentAt = try container.decode(String.self, forKey: .sentAt)
        readAt = try container.decodeIfPresent(String.self, forKey: .readAt)
        deletedAt = try container.decodeIfPresent(String.self, forKey: .deletedAt)
        reactions = try container.decodeIfPresent([ReactionGroupDto].self, forKey: .reactions) ?? []
        emoticonId = try container.decodeIfPresent(String.self, forKey: .emoticonId)
    }

    private enum CodingKeys: String, CodingKey { case id, conversationId, senderId, body, sentAt, readAt, deletedAt, reactions, emoticonId }
}

// Real WebSocket push envelopes (2026-07-18) -- see
// NetworkClient.connectMessagingSocket's own doc comment.
public struct MessagingSocketTypeEnvelope: Decodable { public let type: String }
public struct MessagingSocketMessageEnvelope: Decodable { public let type: String; public let conversationId: String; public let message: MessageDto }

// Real online/offline presence push (2026-07-19) -- see
// rw.itunda.core.realtime.RealtimeMessagePublisher.publishPresenceChange's own doc
// comment for the real transition-only/1:1-only scoping.
public struct MessagingSocketPresenceEnvelope: Decodable { public let type: String; public let userId: String; public let online: Bool }

// Real typing indicator push envelope (2026-07-19) -- see MessagingSocketPush's own doc
// comment.
public struct MessagingSocketTypingEnvelope: Decodable {
    public let type: String
    public let conversationId: String?
    public let groupConversationId: String?
    public let userId: String
}

// Real live reaction push (2026-07-19) -- see
// MessagingWebSocketHandler.publishReactionChange/publishGroupReactionChange's own
// doc comments. Exactly one of conversationId/groupConversationId is set.
public struct MessagingSocketReactionEnvelope: Decodable {
    public let type: String
    public let conversationId: String?
    public let groupConversationId: String?
    public let messageId: String
    public let reactions: [ReactionGroupDto]
}

public struct StartConversationRequest: Encodable {
    public let phoneNumber: String?
    public let otherUserId: String?
}

public struct SendMessageRequest: Encodable {
    public let body: String
    public let replyToMessageId: String?
    public init(body: String, replyToMessageId: String? = nil) {
        self.body = body
        self.replyToMessageId = replyToMessageId
    }
}
public struct TalkContactDto: Decodable, Identifiable { public let userId: String; public let name: String; public var id: String { userId } }
public struct TalkContactsResponse: Decodable { public let success: Bool; public let contacts: [TalkContactDto] }
public struct ConversationQuietResponse: Decodable { public let success: Bool; public let quiet: Bool }
public struct CreateChatReportRequest: Encodable { public let messageId: String; public let reason: String }
public struct SetConversationQuietRequest: Encodable { public let quiet: Bool }
public struct EmptyRequest: Encodable {}
public struct ToggleReactionRequest: Encodable { public let emoji: String }
public struct ReactionsResponse: Decodable { public let success: Bool; public let reactions: [ReactionGroupDto] }

public struct ConversationResponse: Decodable { public let success: Bool; public let conversation: ConversationDto }
public struct ConversationsResponse: Decodable { public let success: Bool; public let conversations: [ConversationSummaryDto] }
public struct MessagesResponse: Decodable { public let success: Bool; public let messages: [MessageDto] }
public struct MessageResponse: Decodable { public let success: Bool; public let message: MessageDto }
public struct PinnedMessageResponse: Decodable { public let success: Bool; public let message: MessageDto? }

// Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber).
public struct CreateGroupRequest: Encodable { public let name: String; public let memberPhoneNumbers: [String] }
public struct SendGroupMessageRequest: Encodable {
    public let body: String
    public let replyToMessageId: String?
    public init(body: String, replyToMessageId: String? = nil) {
        self.body = body
        self.replyToMessageId = replyToMessageId
    }
}

public struct GroupSummaryDto: Decodable, Identifiable {
    public let groupId: String
    public let name: String
    public let memberCount: Int
    public let lastMessageAt: String
    public let lastMessagePreview: String?
    public let unreadCount: Int
    // Real group photo/description (2026-07-28) -- see GroupMessagingService's own doc
    // comments. Found 2026-08-01 via a defined-but-uncalled-endpoint sweep: real on
    // backend since it shipped, zero client anywhere on any of the 3 platforms until
    // now.
    public let photoUrl: String?
    public let description: String?
    public var id: String { groupId }
}
public struct SetGroupPhotoUrlRequest: Encodable { public let photoUrl: String }
public struct SetGroupDescriptionRequest: Encodable { public let description: String }
public struct GroupSummaryResponse: Decodable { public let success: Bool; public let group: GroupSummaryDto }
public struct GroupMessageDto: Decodable, Identifiable {
    public let id: String
    public let groupConversationId: String
    public let senderId: String
    public let body: String
    public let sentAt: String
    public let deletedAt: String?
    public let replyToMessageId: String?
    public let reactions: [ReactionGroupDto]
    // Real KakaoTalk Emoticon Store, group-send side (item 133/204) -- see
    // sendGroupEmoticon's own doc comment. Set only on a message actually sent via
    // EmoticonController's /groups/{id}/send endpoint. Found 2026-07-29 via the
    // defined-but-uncalled-method sweep: the backend/DTO field existed on bank-mfe's
    // equivalent type, but this DTO never carried it and no client ever sent one.
    public let emoticonId: String?

    // Explicit memberwise init -- see MessageDto's own identical note on why this is
    // needed once a custom init(from:) is present.
    public init(id: String, groupConversationId: String, senderId: String, body: String, sentAt: String, reactions: [ReactionGroupDto], emoticonId: String? = nil) {
        self.id = id
        self.groupConversationId = groupConversationId
        self.senderId = senderId
        self.body = body
        self.sentAt = sentAt
        self.deletedAt = nil
        self.replyToMessageId = nil
        self.reactions = reactions
        self.emoticonId = emoticonId
    }

    // Same real-time-push-omits-reactions handling as MessageDto's own custom decode.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        groupConversationId = try container.decode(String.self, forKey: .groupConversationId)
        senderId = try container.decode(String.self, forKey: .senderId)
        body = try container.decode(String.self, forKey: .body)
        sentAt = try container.decode(String.self, forKey: .sentAt)
        deletedAt = try container.decodeIfPresent(String.self, forKey: .deletedAt)
        replyToMessageId = try container.decodeIfPresent(String.self, forKey: .replyToMessageId)
        reactions = try container.decodeIfPresent([ReactionGroupDto].self, forKey: .reactions) ?? []
        emoticonId = try container.decodeIfPresent(String.self, forKey: .emoticonId)
    }

    private enum CodingKeys: String, CodingKey { case id, groupConversationId, senderId, body, sentAt, deletedAt, replyToMessageId, reactions, emoticonId }
}
public struct GroupResponse: Decodable { public let success: Bool; public let group: GroupSummaryDto }
public struct GroupsResponse: Decodable { public let success: Bool; public let groups: [GroupSummaryDto] }
public struct GroupMessagesResponse: Decodable { public let success: Bool; public let messages: [GroupMessageDto] }
public struct GroupMessageResponse: Decodable { public let success: Bool; public let message: GroupMessageDto }
public struct LeaveGroupResponse: Decodable { public let success: Bool }

// Real member list with real resolved display names (2026-07-18) -- closes the honest,
// named limitation this UI carried since group chat first shipped: message bubbles
// showing a truncated sender id instead of a real name.
public struct GroupMemberDto: Decodable, Identifiable { public let userId: String; public let name: String; public var id: String { userId } }
public struct GroupMembersResponse: Decodable { public let success: Bool; public let members: [GroupMemberDto] }

// Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's own
// doc comment on the backend.
public struct PresenceResponse: Decodable { public let success: Bool; public let presence: [String: Bool] }

// Real WebSocket push envelopes for group chat (2026-07-18).
public struct MessagingSocketGroupEnvelope: Decodable { public let type: String; public let groupConversationId: String; public let message: GroupMessageDto }

public enum MessagingSocketPush {
    case directMessage(conversationId: String, message: MessageDto)
    case groupMessage(groupConversationId: String, message: GroupMessageDto)
    case presenceChange(userId: String, online: Bool)
    // Real typing indicator (2026-07-19) -- see
    // MessagingWebSocketHandler.handleTextMessage's own doc comment on the backend.
    // Ephemeral, never persisted; server-ratelimited to one relay per (user,
    // conversation) per 2s. Exactly one of conversationId/groupConversationId is set.
    case typingChange(conversationId: String?, groupConversationId: String?, userId: String)
    case reactionChange(conversationId: String?, groupConversationId: String?, messageId: String, reactions: [ReactionGroupDto])
}

public struct ListingDto: Decodable, Identifiable {
    public let id: String
    public let sellerId: String
    public let title: String
    public let description: String
    public let price: Double
    public let category: String
    public let status: String
    public let createdAt: String
    // Real optional seller-set location (2026-07-18 backend) -- backs real proximity
    // search and, 2026-07-19, "Directions to this seller".
    public let latitude: Double?
    public let longitude: Double?
    public let meetingPlace: String?
    // buyerId added 2026-07-24 -- real optional buyer identification captured at
    // mark-sold time, see backend Listing.kt's own doc comment. Only set once a real
    // review becomes possible for this transaction.
    public let buyerId: String?
    // Real seller-paid sponsored placement -- see backend
    // MarketplaceService.boostListing's own doc comment. A real, still-future
    // boostedUntil only -- never fabricated for an unpaid or expired listing.
    // Android already has this; this is the first iOS client.
    public let boostedUntil: String?
}

public struct CreateListingRequest: Encodable {
    public let title: String
    public let description: String
    public let price: Double
    public let category: String
    public let latitude: Double?
    public let longitude: Double?
    public let meetingPlace: String?
}

public struct ListingResponse: Decodable { public let success: Bool; public let listing: ListingDto }
public struct MarkSoldRequest: Encodable { public let buyerPhoneNumber: String? }
// Real seller-paid sponsored placement -- mirrors Android's ApiService.kt exactly.
public struct BoostListingRequest: Encodable {
    public let days: Int
    public init(days: Int) { self.days = days }
}
public struct BoostTiersResponse: Decodable { public let success: Bool; public let tiers: [String: Double] }
// Real post-transaction review with asymmetric public/private visibility (2026-07-24)
// -- see backend HoodTransactionReview.kt's own doc comment. goodPoints/
// uncomfortablePoints are preset tag ids (never free text), matching Karrot's own real
// review UX.
public struct SubmitHoodReviewRequest: Encodable { public let goodPoints: [String]; public let uncomfortablePoints: [String] }
public struct HoodReviewDto: Decodable, Identifiable {
    public let id: String
    public let transactionType: String
    public let transactionId: String
    public let reviewerId: String
    public let revieweeId: String
    public let goodPoints: [String]
    public let uncomfortablePoints: [String]
    public let createdAt: String
}
public struct HoodReviewResponse: Decodable { public let success: Bool; public let review: HoodReviewDto }
public struct HoodReviewsResponse: Decodable { public let success: Bool; public let reviews: [HoodReviewDto] }
// trustScores added 2026-07-24 -- backend has spread this alongside every listing/
// job-post/property-listing browse response since 2026-07-21
// (rw.itunda.core.web.TrustScoreSupport), but no client ever parsed or rendered it.
// A sellerId/posterId/listerId -> User.trustScore map (Karrot-Score-style, 0-1000,
// starting at 30). Optional since not every endpoint sharing this struct spreads it.
public struct ListingsResponse: Decodable { public let success: Bool; public let listings: [ListingDto]; public let trustScores: [String: Int]? }

// Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe, ported here) --
// mirrors FavoriteRestaurantDto's exact shape; see ListingFavoriteService.kt's own doc
// comment on the backend for why add/remove are both idempotent.
public struct FavoriteListingDto: Decodable, Identifiable {
    public let listingId: String
    public let title: String
    public let price: Double
    public let category: String
    public let favoritedAt: String
    public var id: String { listingId }
}
public struct FavoriteListingsResponse: Decodable { public let success: Bool; public let favorites: [FavoriteListingDto] }

public struct FavoriteJobPostDto: Decodable, Identifiable {
    public let jobPostId: String
    public let title: String
    public let payAmount: Double
    public let category: String
    public let favoritedAt: String
    public var id: String { jobPostId }
}
public struct FavoriteJobPostsResponse: Decodable { public let success: Bool; public let favorites: [FavoriteJobPostDto] }

// Real KakaoTalk-style "선물하기" money gift (2026-07-20) -- see GiftService's own doc
// comment. Money leaves the sender's wallet into a real escrow account the moment a
// gift is sent, and only reaches the recipient's wallet once they explicitly claim it
// (or is auto-refunded after 7 days). Rendered inline as a gift bubble, same "special
// message body" convention PriceOfferDto already established.
public struct GiftDto: Decodable, Identifiable {
    public let id: String
    public let senderId: String
    public let recipientId: String
    public let conversationId: String
    public let messageId: String
    public let amount: Double
    public let note: String?
    public let theme: String?
    public let status: String
    public let holdTransactionId: String
    public let claimTransactionId: String?
    public let expiresAt: String
    public let claimedAt: String?
    public let createdAt: String
}
public struct SendGiftInConversationRequest: Encodable { public let amount: Double; public let note: String?; public let theme: String? }
public struct GiftResponse: Decodable { public let success: Bool; public let gift: GiftDto }
public struct GiftsResponse: Decodable { public let success: Bool; public let gifts: [GiftDto] }

// Real KakaoPay 송금봉투 (money envelope) themed presets (backend since 2026-07-26,
// GiftTheme's own doc comment) -- exactly these 4 real, sourced presets, optional and
// additive alongside the free-text note. Had zero client anywhere until now.
public let giftThemeLabels: [String: String] = [
    "CONGRATULATIONS": "🎉 Congratulations",
    "HEARTFELT": "💌 From the heart",
    "GOOD_LUCK": "🍀 Good luck",
    "SETTLE_UP": "🧾 Settling up",
]

// Real KakaoTalk Emoticon Store (item 136) -- see backend Emoticon.kt's own doc
// comment. Mirrors bank-mfe's lib/emoticons.ts (item 133) and Android's ApiService.kt
// (item 135) exactly.
public struct EmoticonPackDto: Decodable, Identifiable { public let id: String; public let title: String; public let artistName: String; public let thumbnailUrl: String; public let price: Double; public let active: Bool; public let createdAt: String }
public struct EmoticonDto: Decodable, Identifiable { public let id: String; public let packId: String; public let imageUrl: String; public let sortOrder: Int }
public struct OwnedEmoticonPackDto: Decodable, Identifiable { public let id: String; public let userId: String; public let packId: String; public let source: String; public let acquiredAt: String }
public struct EmoticonPacksResponse: Decodable { public let success: Bool; public let packs: [EmoticonPackDto] }
public struct EmoticonsResponse: Decodable { public let success: Bool; public let emoticons: [EmoticonDto] }
public struct OwnedEmoticonPacksResponse: Decodable { public let success: Bool; public let packs: [OwnedEmoticonPackDto] }
public struct OwnedEmoticonPackResponse: Decodable { public let success: Bool; public let ownedPack: OwnedEmoticonPackDto }
// EmoticonController.giftPack returns the key "giftedPack", not "ownedPack" -- a
// distinct response shape from purchase's own response (a real bug caught building
// Android's own port, item 135 -- not reusing one response type here either).
public struct GiftedEmoticonPackResponse: Decodable { public let success: Bool; public let giftedPack: OwnedEmoticonPackDto }
public struct GiftEmoticonPackRequest: Encodable {
    public let recipientPhoneNumber: String
    public init(recipientPhoneNumber: String) { self.recipientPhoneNumber = recipientPhoneNumber }
}
public struct SendEmoticonRequest: Encodable {
    public let emoticonId: String
    public init(emoticonId: String) { self.emoticonId = emoticonId }
}

// Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
public struct CommunityCategoryDto: Decodable, Identifiable { public let id: String; public let label: String }
public struct CommunityPostDto: Decodable, Identifiable {
    public let id: String
    public let authorId: String
    public let category: String
    public let title: String
    public let body: String
    public let status: String
    public let likeCount: Int
    public let commentCount: Int
    public let createdAt: String
    public let latitude: Double?
    public let longitude: Double?
    // groupConversationId added 2026-07-24 -- see backend CommunityPost.kt's own doc
    // comment. Only ever set for category == "meetup" posts that have had at least one
    // real join.
    public let groupConversationId: String?
    // Real 당근모임-style structured meetup fields (2026-07-25) -- see backend
    // CommunityPost.kt's own doc comment. Both nil unless category == "meetup".
    public let eventDate: String?
    public let capacity: Int?
    public init(id: String, authorId: String, category: String, title: String, body: String, status: String, likeCount: Int, commentCount: Int, createdAt: String, latitude: Double?, longitude: Double?, groupConversationId: String? = nil, eventDate: String? = nil, capacity: Int? = nil) { self.id = id; self.authorId = authorId; self.category = category; self.title = title; self.body = body; self.status = status; self.likeCount = likeCount; self.commentCount = commentCount; self.createdAt = createdAt; self.latitude = latitude; self.longitude = longitude; self.groupConversationId = groupConversationId; self.eventDate = eventDate; self.capacity = capacity }
}
public struct CreateCommunityPostRequest: Encodable {
    public let category: String; public let title: String; public let body: String
    public let latitude: Double?; public let longitude: Double?
    public let eventDate: String?; public let capacity: Int?
}
public struct CommunityPostResponse: Decodable { public let success: Bool; public let post: CommunityPostDto }
// joinedCounts added 2026-07-24 -- postId -> real member count of that meetup's group
// chat, closing docs/DESIGN_REFERENCES.md Section 4 recommendation #4.
public struct CommunityPostsResponse: Decodable { public let success: Bool; public let posts: [CommunityPostDto]; public let joinedCounts: [String: Int]? }
public struct CommunityCategoriesResponse: Decodable { public let success: Bool; public let categories: [CommunityCategoryDto] }
public struct CommunityPostDetailResponse: Decodable { public let success: Bool; public let post: CommunityPostDto; public let authorName: String; public let likedByMe: Bool }
public struct CommunityCommentDto: Decodable, Identifiable { public let id: String; public let postId: String; public let authorId: String; public let body: String; public let createdAt: String }
public struct CommunityCommentWithAuthorDto: Decodable, Identifiable {
    public let comment: CommunityCommentDto
    public let authorName: String
    public var id: String { comment.id }
}
public struct CommunityCommentsResponse: Decodable { public let success: Bool; public let comments: [CommunityCommentWithAuthorDto] }
public struct AddCommunityCommentRequest: Encodable { public let body: String }
public struct CommunityCommentResponse: Decodable { public let success: Bool; public let comment: CommunityCommentDto }
public struct ToggleCommunityLikeResponse: Decodable { public let success: Bool; public let liked: Bool }
// Real 같이해요 (join-together) explicit join (2026-07-24) -- see backend
// CommunityService.joinMeetup's own doc comment.
public struct JoinMeetupResponse: Decodable { public let success: Bool; public let groupId: String }

// Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- mirrors
// bank-mfe's lib/community.ts exactly. bank-mfe/Android already have this; this is the
// first iOS client.
public struct ScheduleMeetupSessionsRequest: Encodable {
    public let dates: [String]
    public init(dates: [String]) { self.dates = dates }
}
public struct MeetupSessionDto: Decodable, Identifiable {
    public let id: String
    public let postId: String
    public let sequence: Int
    public let scheduledFor: String
    public let createdAt: String
}
public struct MeetupSessionsResponse: Decodable { public let success: Bool; public let sessions: [MeetupSessionDto] }
public struct MeetupAttendanceDto: Decodable { public let id: String; public let sessionId: String; public let userId: String; public let checkedInAt: String }
public struct MeetupAttendanceResponse: Decodable { public let success: Bool; public let attendance: MeetupAttendanceDto }

// Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see
// rw.itunda.community.CommunityService.finalizeGroupBuy's own doc comment. bank-mfe/
// Android already have this; this is the first iOS client.
public struct FinalizeGroupBuyRequest: Encodable {
    public let totalAmount: Double
    public let description: String
    public init(totalAmount: Double, description: String) { self.totalAmount = totalAmount; self.description = description }
}

// Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
public struct JobCategoryDto: Decodable, Identifiable { public let id: String; public let label: String }
public struct JobPostDto: Decodable, Identifiable {
    public let id: String
    public let posterId: String
    public let category: String
    public let title: String
    public let description: String
    public let payType: String
    public let payAmount: Double
    public let status: String
    public let createdAt: String
    public let latitude: Double?
    public let longitude: Double?
    // workerId added 2026-07-24 -- real optional worker identification captured at
    // mark-filled time, see backend JobPost.kt's own doc comment. Only set once a
    // real review becomes possible for this transaction.
    public let workerId: String?
}
public struct CreateJobPostRequest: Encodable {
    public let category: String; public let title: String; public let description: String; public let payType: String; public let payAmount: Double
    public let latitude: Double?; public let longitude: Double?
}
public struct MarkFilledRequest: Encodable { public let workerPhoneNumber: String? }
public struct JobPostResponse: Decodable { public let success: Bool; public let post: JobPostDto }
public struct JobPostsResponse: Decodable { public let success: Bool; public let posts: [JobPostDto]; public let trustScores: [String: Int]? }
public struct JobCategoriesResponse: Decodable { public let success: Bool; public let categories: [JobCategoryDto] }
public struct ContactPosterResponse: Decodable { public let success: Bool; public let conversation: ConversationDto }

// Real 당근알바-style structured application (2026-07-25 on Android, ported here
// 2026-07-29) -- see backend JobApplicationService's own doc comment. A real
// self-introduction the poster reviews before deciding, not a bare DM -- "message
// poster" (contactPoster above) still exists as a separate, unstructured hand-off.
public struct ApplyToJobRequest: Encodable { public let message: String }
public struct JobApplicationDto: Decodable, Identifiable {
    public let id: String
    public let jobPostId: String
    public let applicantId: String
    public let message: String
    public let status: String
    public let submittedAt: String
    public let respondedAt: String?
}
public struct JobApplicationResponse: Decodable { public let success: Bool; public let application: JobApplicationDto; public let conversation: ConversationDto? }
public struct JobApplicationsResponse: Decodable { public let success: Bool; public let applications: [JobApplicationDto] }
public struct RespondToApplicationRequest: Encodable { public let accept: Bool }

public struct CreateHoodReportRequest: Encodable { public let targetType: String; public let targetId: String; public let reason: String }
public struct HoodReportResponse: Decodable { public let success: Bool }

// Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
public struct PropertyTypeDto: Decodable, Identifiable { public let id: String; public let label: String }
public struct PropertyListingDto: Decodable, Identifiable {
    public let id: String
    public let listerId: String
    public let listingType: String
    public let propertyType: String
    public let title: String
    public let description: String
    public let price: Double
    public let bedrooms: Int?
    public let sizeSqm: Double?
    public let status: String
    public let createdAt: String
    public let latitude: Double?
    public let longitude: Double?
    // counterpartyId added 2026-07-24 -- real optional buyer/tenant identification
    // captured at mark-taken time, see backend PropertyListing.kt's own doc comment.
    // Only set once a real review becomes possible for this transaction.
    public let counterpartyId: String?
    // Real ownership verification (2026-07-25) -- NONE/PENDING/VERIFIED, see backend
    // PropertyOwnershipService's own doc comment. Real on Android since that day; found
    // 2026-08-01 via a fresh backend-module sweep with zero iOS client despite that.
    public let ownershipVerificationStatus: String?
}
public struct MarkTakenRequest: Encodable { public let counterpartyPhoneNumber: String? }
public struct SubmitOwnershipVerificationRequest: Encodable { public let documentUrl: String }
public struct CreatePropertyListingRequest: Encodable {
    public let listingType: String; public let propertyType: String; public let title: String; public let description: String; public let price: Double
    public let bedrooms: Int?; public let sizeSqm: Double?; public let latitude: Double?; public let longitude: Double?
}
public struct PropertyListingResponse: Decodable { public let success: Bool; public let listing: PropertyListingDto }
public struct PropertyListingsResponse: Decodable { public let success: Bool; public let listings: [PropertyListingDto]; public let trustScores: [String: Int]? }
public struct FavoritePropertyListingDto: Decodable, Identifiable { public let propertyListingId: String; public let title: String; public let price: Double; public let listingType: String; public let favoritedAt: String; public var id: String { propertyListingId } }
public struct FavoritePropertyListingsResponse: Decodable { public let success: Bool; public let favorites: [FavoritePropertyListingDto] }
public struct PropertyTypesResponse: Decodable { public let success: Bool; public let propertyTypes: [PropertyTypeDto] }
public struct ContactListerResponse: Decodable { public let success: Bool; public let conversation: ConversationDto }
public struct ContactSellerResponse: Decodable { public let success: Bool; public let conversation: ConversationDto }

// Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService's own
// doc comment. Each offer/counter/accept/reject is a real message in the same real
// conversation contactSeller establishes, rendered inline as an offer bubble.
public struct PriceOfferDto: Decodable, Identifiable {
    public let id: String
    public let listingId: String
    public let messageId: String
    public let conversationId: String
    public let buyerId: String
    public let sellerId: String
    public let proposedByUserId: String
    public let amount: Double
    public let status: String
    public let createdAt: String
    public let respondedAt: String?
}
public struct MakeOfferRequest: Encodable { public let amount: Double }
public struct RespondToOfferRequest: Encodable { public let action: String; public let counterAmount: Double? }
public struct PriceOfferResponse: Decodable { public let success: Bool; public let offer: PriceOfferDto }
public struct PriceOffersResponse: Decodable { public let success: Bool; public let offers: [PriceOfferDto] }

// Real 당근-style price-offer negotiation on a real property listing (2026-07-19) -- see
// PropertyPriceOfferService's own doc comment. Mirrors PriceOfferDto field-for-field.
public struct PropertyPriceOfferDto: Decodable, Identifiable {
    public let id: String
    public let propertyListingId: String
    public let messageId: String
    public let conversationId: String
    public let inquirerId: String
    public let listerId: String
    public let proposedByUserId: String
    public let amount: Double
    public let status: String
    public let createdAt: String
    public let respondedAt: String?
}
public struct MakePropertyOfferRequest: Encodable { public let amount: Double }
public struct RespondToPropertyOfferRequest: Encodable { public let action: String; public let counterAmount: Double? }
public struct PropertyPriceOfferResponse: Decodable { public let success: Bool; public let offer: PropertyPriceOfferDto }
public struct PropertyPriceOffersResponse: Decodable { public let success: Bool; public let offers: [PropertyPriceOfferDto] }

public struct ShoppingMerchantDto: Decodable, Identifiable {
    public let merchantId: String
    public let businessName: String
    public let category: String?
    public let cashbackRate: String
    // Real optional location (2026-07-19) -- backs the real self-hosted Map view.
    public let latitude: Double?
    public let longitude: Double?
    // Real browse-card enrichment (2026-07-21) -- ports Android/bank-mfe's own
    // ShoppingMerchantDto fields (see their doc comments for the full account).
    // photoUrl/minOrderAmount are real, merchant-set (nil when unset); rating/
    // reviewCount are real, batch-aggregated from EatsReview. distanceKm/
    // deliveryTimeMinutes are only present when the caller supplies its own real
    // buyerLat/buyerLng -- like Android's own scoping, this screen doesn't wire those up
    // yet (would need real GPS/location permission plumbing, out of scope this pass), so
    // they render conditionally and are simply absent today, never a fabricated number.
    public let photoUrl: String?
    public let minOrderAmount: Double?
    public let rating: Double?
    public let reviewCount: Int?
    public let distanceKm: Double?
    public let deliveryTimeMinutes: Int?
    public var id: String { merchantId }

    public init(
        merchantId: String, businessName: String, category: String?, cashbackRate: String, latitude: Double? = nil, longitude: Double? = nil,
        photoUrl: String? = nil, minOrderAmount: Double? = nil, rating: Double? = nil, reviewCount: Int? = nil,
        distanceKm: Double? = nil, deliveryTimeMinutes: Int? = nil
    ) {
        self.merchantId = merchantId
        self.businessName = businessName
        self.category = category
        self.cashbackRate = cashbackRate
        self.latitude = latitude
        self.longitude = longitude
        self.photoUrl = photoUrl
        self.minOrderAmount = minOrderAmount
        self.rating = rating
        self.reviewCount = reviewCount
        self.distanceKm = distanceKm
        self.deliveryTimeMinutes = deliveryTimeMinutes
    }
}
public struct ShoppingMerchantsResponse: Decodable { public let success: Bool; public let merchants: [ShoppingMerchantDto] }

// Real "search this map" + "directions" (2026-07-19) -- see rw.itunda.maps.MapsService's
// own doc comment on the backend for why these are a new, general-purpose front door
// onto itunda's already-deployed self-hosted Nominatim/OSRM.
// Codable, not just Decodable (2026-07-22) -- RecentMapSearchesStore needs to encode
// this back to JSON for local UserDefaults persistence, not just decode it from the API.
public struct PlaceSearchResultDto: Codable {  public let displayName: String; public let latitude: Double; public let longitude: Double; public init(displayName: String, latitude: Double, longitude: Double) { self.displayName = displayName; self.latitude = latitude; self.longitude = longitude } }
public struct MapsSearchResponse: Decodable { public let success: Bool; public let results: [PlaceSearchResultDto] }
public struct MapsReverseGeocodeResponse: Decodable { public let success: Bool; public let placeName: String? }
public struct RouteStepDto: Decodable { public let instruction: String; public let distanceMeters: Double; public let streetName: String? }
public struct RouteResultDto: Decodable { public let distanceKm: Double; public let durationMinutes: Double; public let geometry: [[Double]]; public let steps: [RouteStepDto] }
public struct MapsDirectionsResponse: Decodable { public let success: Bool; public let route: RouteResultDto }
public struct ItineraryWaypointRequest: Encodable {  public let latitude: Double; public let longitude: Double; public init(latitude: Double, longitude: Double) { self.latitude = latitude; self.longitude = longitude } }
public struct ItineraryDirectionsRequest: Encodable { public let waypoints: [ItineraryWaypointRequest]; public let mode: String }
// Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives' own
// doc comment on the backend. Often just a single-element array -- OSRM itself decides
// whether a real alternative exists for a given trip.
public struct MapsDirectionsAlternativesResponse: Decodable { public let success: Bool; public let routes: [RouteResultDto] }
public struct MerchantCategoriesResponse: Decodable { public let success: Bool; public let categories: [String] }

// Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #8: a curated deal rail on the Shop landing surface. Every entry is a
// real merchant-set discount, never a fabricated promo -- see backend
// MerchantProductRepository.findDeals's own doc comment.
public struct DealProductDto: Decodable, Identifiable {
    public let id: String
    public let merchantId: String
    public let merchantName: String
    public let name: String
    public let price: Double
    public let imageUrl: String?
    public let originalPrice: Double?
    public let discountPercent: Int?
    public let description: String?
    public let stockQuantity: Int?
}
public struct DealsResponse: Decodable { public let success: Bool; public let products: [DealProductDto] }
public struct MembershipDayStatusResponse: Decodable { public let success: Bool; public let isMembershipDay: Bool; public let multiplier: Double }

// Real "nearby places" category search + bookmarked/favorite places (2026-07-19) -- see
// rw.itunda.maps.MapsService's own doc comment on the backend. `mapNearbyCategories`
// mirrors bank-mfe's own hardcoded `NEARBY_CATEGORIES` list exactly.
public struct NearbyPlaceDto: Decodable {
    public let displayName: String; public let latitude: Double; public let longitude: Double; public let distanceKm: Double
    public init(displayName: String, latitude: Double, longitude: Double, distanceKm: Double) {
        self.displayName = displayName; self.latitude = latitude; self.longitude = longitude; self.distanceKm = distanceKm
    }
}
public struct MapNearbyResponse: Decodable { public let success: Bool; public let places: [NearbyPlaceDto] }

// Real customer-facing itunda cash-agent discovery (item 158) -- see
// AgentDiscoveryController.kt on the backend. Distinct from the OSM-backed category
// search above; Android already treats this as its own "ITUNDA_AGENT" category chip on
// the same Maps row (MapsScreen.kt's searchNearbyAgents), bank-mfe got it in item 157 --
// this is the iOS port, mapped into the same NearbyPlaceDto shape so this screen's
// existing marker/popup rendering needs zero special-casing beyond which fetch to call.
public struct NearbyAgentDto: Decodable { public let id: String; public let displayName: String; public let latitude: Double; public let longitude: Double; public let distanceKm: Double }
public struct NearbyAgentsResponse: Decodable { public let success: Bool; public let agents: [NearbyAgentDto] }
// folderName/color added 2026-07-22 -- see MapBookmark.kt's own doc comment on the
// backend (migration V73). Every bookmark belongs to exactly one named folder with its
// own pin color; a bookmark saved before this existed defaults into "Saved places" /
// "#F5A623" (the same star-yellow the ★ icon already used).
public struct MapBookmarkDto: Decodable, Identifiable { public let id: String; public let displayName: String; public let latitude: Double; public let longitude: Double; public let folderName: String; public let color: String; public let createdAt: String }
public struct MapBookmarksResponse: Decodable { public let success: Bool; public let bookmarks: [MapBookmarkDto] }
public struct AddMapBookmarkRequest: Encodable { public let displayName: String; public let latitude: Double; public let longitude: Double; public let folderName: String?; public let color: String? }
public struct AddMapBookmarkResponse: Decodable { public let success: Bool; public let bookmark: MapBookmarkDto }
// Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc comment.
public struct MoveMapBookmarkRequest: Encodable { public let folderName: String; public let color: String }
public struct MoveMapBookmarkResponse: Decodable { public let success: Bool; public let bookmark: MapBookmarkDto }

public struct MapPlaceCategory: Identifiable { public let id: String; public let label: String; public init(id: String, label: String) { self.id = id; self.label = label } }
public let mapNearbyCategories: [MapPlaceCategory] = [
    MapPlaceCategory(id: "RESTAURANT", label: "Restaurants"),
    MapPlaceCategory(id: "CAFE", label: "Cafes"),
    MapPlaceCategory(id: "HOSPITAL", label: "Hospitals"),
    MapPlaceCategory(id: "PHARMACY", label: "Pharmacies"),
    MapPlaceCategory(id: "BANK", label: "Banks"),
    MapPlaceCategory(id: "ATM", label: "ATMs"),
    MapPlaceCategory(id: "HOTEL", label: "Hotels"),
    MapPlaceCategory(id: "SUPERMARKET", label: "Supermarkets"),
    MapPlaceCategory(id: "GAS_STATION", label: "Gas stations"),
    MapPlaceCategory(id: "SCHOOL", label: "Schools"),
    MapPlaceCategory(id: "ITUNDA_AGENT", label: "Cash agents"),
]

// imageUrl/originalPrice/discountPercent added 2026-07-21, closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #4 -- see backend
// MerchantProduct.kt's own doc comment for the full account (merchant-supplied external
// URL, no upload/storage layer; discountPercent is server-computed, never client-set).
// Real menu-item option groups (2026-07-21, v1: required single-select only) -- ports
// bank-mfe's own MenuOptionChoice/MenuOptionGroup interfaces (lib/eats.ts). See
// MenuOptionGroup.kt's own doc comment on the backend for the full, honestly-scoped
// account.
public struct MenuOptionChoiceDto: Decodable, Identifiable {
    public let id: String
    public let name: String
    public let priceDelta: Double
}
public struct MenuOptionGroupDto: Decodable, Identifiable {
    public let id: String
    public let name: String
    public let choices: [MenuOptionChoiceDto]
}

public struct MerchantProductDto: Decodable, Identifiable {
    public let id: String
    public let merchantId: String
    public let name: String
    public let price: Double
    public let active: Bool
    public let createdAt: String
    public let imageUrl: String?
    public let originalPrice: Double?
    public let discountPercent: Int?
    // description added 2026-07-21, backing the new dedicated product-detail screen
    // (closes docs/DESIGN_REFERENCES.md Section 5 recommendation #6).
    public let description: String?
    public let stockQuantity: Int?
    // Optional/absent on endpoints that don't fold it in (e.g. product search) --
    // only ShoppingController.getMerchantProducts (Eats' menu) populates this today.
    public let optionGroups: [MenuOptionGroupDto]?
    // Real bulk/wholesale pricing -- closes the gap named in Baemin's own real
    // 배민상회 B2B supplies marketplace research. Empty/absent for every product with
    // no real tiers set. See ProductPriceTier.kt's own doc comment on the backend.
    // Android already has this; this is the first iOS client.
    public let priceTiers: [PriceTierDto]?
}
public struct PriceTierDto: Decodable { public let minQuantity: Int; public let unitPrice: Double }
public struct MerchantSummaryDto: Decodable { public let id: String; public let businessName: String }
public struct MerchantProductsResponse: Decodable { public let success: Bool; public let merchant: MerchantSummaryDto; public let products: [MerchantProductDto] }

// Real cross-merchant product search (item 138) -- see backend
// MerchantProductRepository.search's own doc comment. Mirrors bank-mfe's
// lib/shopping.ts ProductSearchResult and Android's ProductSearchResultDto exactly;
// iOS never had this endpoint at all.
public struct ProductSearchResultDto: Decodable, Identifiable {
    public let id: String
    public let merchantId: String
    public let merchantName: String
    public let name: String
    public let price: Double
    public let imageUrl: String?
    public let originalPrice: Double?
    public let discountPercent: Int?
    public let description: String?
    public let stockQuantity: Int?
}
public struct ProductSearchResponse: Decodable { public let success: Bool; public let products: [ProductSearchResultDto] }

// Real KakaoTalk-style 기프티콘 (mobile gift voucher, item 138) -- see backend
// GiftVoucher.kt's own doc comment. Mirrors bank-mfe's lib/giftVouchers.ts (item 134)
// and Android's ApiService.kt (item 137) exactly.
public struct GiftVoucherDto: Decodable, Identifiable {
    public let id: String
    public let purchaserId: String
    public let recipientId: String
    public let conversationId: String
    public let messageId: String
    public let merchantId: String
    public let merchantProductId: String?
    public let productNameSnapshot: String?
    public let amount: Double
    public let status: String
    public let holdTransactionId: String
    public let redeemTransactionId: String?
    public let refundTransactionId: String?
    public let expiresAt: String
    public let redeemedAt: String?
    public let extended: Bool
    public let createdAt: String
}
public struct PurchaseGiftVoucherRequest: Encodable {
    public let recipientPhoneNumber: String
    public let merchantId: String
    public let merchantProductId: String?
    public init(recipientPhoneNumber: String, merchantId: String, merchantProductId: String?) {
        self.recipientPhoneNumber = recipientPhoneNumber
        self.merchantId = merchantId
        self.merchantProductId = merchantProductId
    }
}
public struct GiftVoucherResponse: Decodable { public let success: Bool; public let voucher: GiftVoucherDto }
public struct GiftVouchersResponse: Decodable { public let success: Bool; public let vouchers: [GiftVoucherDto] }

// Real Naver Smart Store-style "알림받기" (follow a store for its own broadcast
// notices) -- see backend MerchantFollowService.kt's own doc comment. Mirrors
// bank-mfe's lib/shopping.ts FollowedMerchant exactly.
public struct FollowedMerchantDto: Decodable, Identifiable { public let merchantId: String; public let businessName: String; public let category: String?; public let followedAt: String; public var id: String { merchantId } }
public struct FollowedMerchantsResponse: Decodable { public let success: Bool; public let follows: [FollowedMerchantDto] }
public struct MerchantFollowDto: Decodable { public let id: String; public let userId: String; public let merchantId: String; public let createdAt: String }
public struct MerchantFollowResponse: Decodable { public let success: Bool; public let follow: MerchantFollowDto }

// Real "pay a merchant" -- mirrors bank-mfe's lib/shopping.ts CollectPaymentResult
// exactly (a flat response, not nested under a key).
public struct CollectPaymentRequest: Encodable {
    public let couponId: String?
    public init(couponId: String? = nil) { self.couponId = couponId }
}
public struct StaticQrPayRequest: Encodable {
    public let amount: Double
    public let description: String?
    public init(amount: Double, description: String? = nil) { self.amount = amount; self.description = description }
}
public struct CollectPaymentResultDto: Decodable {
    public let success: Bool
    public let transactionId: String
    public let merchantName: String
    public let amount: Double
    public let fee: Double
    public let status: String
    public let channel: String
    public let completedAt: String
    public let cashbackEarned: Double
}

// Real Face Pay -- mirrors bank-mfe's lib/facepay.ts exactly.
public struct FacePayEnrollmentDto: Decodable {
    public let id: String
    public let userId: String
    public let active: Bool
    public let enrolledAt: String
    public let revokedAt: String?
}
public struct FacePayEnrollmentResponse: Decodable { public let success: Bool; public let enrollment: FacePayEnrollmentDto }
public struct FacePayStatusResponse: Decodable { public let success: Bool; public let enrolled: Bool; public let enrollment: FacePayEnrollmentDto? }

// Real Shop product wishlist (2026-07-24) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #3: backend (ProductFavoriteService, 2026-07-20) and bank-mfe already
// had this; iOS had zero wiring. Mirrors FavoriteListingDto/FavoriteJobPostDto/
// FavoritePropertyListingDto field-for-field.
public struct FavoriteProductDto: Decodable, Identifiable {
    public let productId: String
    public let merchantId: String
    public let name: String
    public let price: Double
    public let businessName: String
    public let favoritedAt: String
    public let imageUrl: String?
    public let originalPrice: Double?
    public let discountPercent: Int?
    public let description: String?
    public var id: String { productId }
}
public struct FavoriteProductsResponse: Decodable { public let success: Bool; public let favorites: [FavoriteProductDto] }

public struct OrderItemRequest: Encodable {  public let productId: String; public let quantity: Int; public init(productId: String, quantity: Int) { self.productId = productId; self.quantity = quantity } }
public struct PlaceOrderRequest: Encodable {
    public let merchantId: String
    public let items: [OrderItemRequest]
    public let deliveryAddress: String
    public init(merchantId: String, items: [OrderItemRequest], deliveryAddress: String) { self.merchantId = merchantId; self.items = items; self.deliveryAddress = deliveryAddress }
}

public struct OrderDto: Decodable, Identifiable {
    public let id: String
    public let buyerId: String
    public let merchantId: String
    public let deliveryAddress: String
    public let totalAmount: Double
    public let fee: Double
    public let transactionId: String
    public let status: String
    public let createdAt: String
    public let updatedAt: String
    public let refundTransactionId: String?
}
public struct OrderItemDto: Decodable, Identifiable { public let id: String; public let orderId: String; public let productId: String; public let productName: String; public let unitPrice: Double; public let quantity: Int }
public struct OrderDetailResponse: Decodable { public let success: Bool; public let order: OrderDto; public let items: [OrderItemDto] }
public struct OrdersResponse: Decodable { public let success: Bool; public let orders: [OrderDto] }

// Real Coupang-style post-delivery Return & Exchange requests (item 166/175) -- see
// OrderReturnService's own doc comment. bank-mfe (item 166) and Android buyer side
// (item 174) already have this; this is the iOS port, buyer side only (no Shop
// merchant order-management screen exists on iOS at all, same gap as Android).
public let orderReturnReasonCodes = ["DEFECTIVE", "WRONG_ITEM", "NOT_AS_DESCRIBED", "NO_LONGER_NEEDED", "SIZE_FIT", "OTHER"]
public struct RequestOrderReturnRequest: Encodable { public let type: String; public let reasonCode: String; public let reasonNote: String? }
public struct OrderReturnRequestDto: Decodable, Identifiable {
    public let id: String
    public let orderId: String
    public let buyerId: String
    public let merchantId: String
    public let type: String
    public let reasonCode: String
    public let reasonNote: String?
    public let status: String
    public let refundTransactionId: String?
    public let requestedAt: String
    public let decidedAt: String?
}
public struct OrderReturnRequestResponse: Decodable { public let success: Bool; public let returnRequest: OrderReturnRequestDto }
public struct OrderReturnRequestsResponse: Decodable { public let success: Bool; public let returnRequests: [OrderReturnRequestDto] }

// Real post-delivery product reviews (2026-07-20) -- see ProductReviewService's own doc
// comment, mirroring SubmitEatsReviewRequest/EatsReviewDto below but keyed to one order
// line item rather than the whole order (a Commerce order can carry several different
// products from one merchant, and real Coupang reviews are per-product).
public struct SubmitProductReviewRequest: Encodable { public let rating: Int; public let comment: String? }
public struct ProductReviewDto: Decodable {
    public let id: String
    public let orderItemId: String
    public let orderId: String
    public let buyerId: String
    public let productId: String
    public let merchantId: String
    public let rating: Int
    public let comment: String?
    // Real owner-side reply (item 187/188/189) -- see
    // ProductReviewService.replyToProductReview's own doc comment. merchant-mfe (item
    // 187) and Android (item 188) already have this; customer-side display only here
    // (the reply-writing side lives on MerchantApp, the merchant-owner app).
    public let ownerReply: String?
    public let ownerRepliedAt: String?
    public let createdAt: String
}
public struct ProductReviewResponse: Decodable { public let success: Bool; public let review: ProductReviewDto }
public struct ProductReviewsResponse: Decodable { public let success: Bool; public let reviews: [ProductReviewDto] }
public struct ProductRatingResponse: Decodable { public let success: Bool; public let average: Double?; public let count: Int }

// Real Coupang-style pre-purchase product Q&A -- mirrors bank-mfe's lib/commerce.ts
// ProductInquiry exactly.
public struct AskProductInquiryRequest: Encodable { public let question: String }
public struct ProductInquiryDto: Decodable, Identifiable {
    public let id: String
    public let productId: String
    public let merchantId: String
    public let buyerId: String
    public let question: String
    public let answer: String?
    public let answeredAt: String?
    public let createdAt: String
}
public struct ProductInquiryResponse: Decodable { public let success: Bool; public let inquiry: ProductInquiryDto }
public struct ProductInquiriesResponse: Decodable { public let success: Bool; public let inquiries: [ProductInquiryDto] }

// Real Kakao Pay 정기결제/Toss 빌링키-style recurring merchant billing -- mirrors
// bank-mfe's lib/shopping.ts exactly.
public struct MerchantBillingPlanDto: Decodable, Identifiable {
    public let id: String
    public let merchantId: String
    public let name: String
    public let description: String?
    public let amount: Double
    public let intervalDays: Int
    public let active: Bool
    public let createdAt: String
}
public struct MerchantBillingSubscriptionDto: Decodable, Identifiable {
    public let id: String
    public let planId: String
    public let merchantId: String
    public let customerId: String
    public let status: String
    public let nextChargeAt: String
    public let lastChargedAt: String?
    public let chargeCount: Int
    public let lastFailureReason: String?
    public let createdAt: String
    public let cancelledAt: String?
}
public struct MerchantBillingPlansResponse: Decodable { public let success: Bool; public let plans: [MerchantBillingPlanDto] }
public struct MerchantBillingSubscriptionResponse: Decodable { public let success: Bool; public let subscription: MerchantBillingSubscriptionDto }
public struct MerchantBillingSubscriptionsResponse: Decodable { public let success: Bool; public let subscriptions: [MerchantBillingSubscriptionDto] }

// Real recurring-payment ("subscription") detection -- mirrors bank-mfe's lib/wallet.ts
// DetectedSubscription exactly.
public struct DetectedSubscriptionDto: Decodable {
    public let displayName: String
    public let amount: Double
    public let cadence: String
    public let occurrenceCount: Int
    public let lastPaidAt: String
    public let nextExpectedAt: String
    public let monthlyEquivalent: Double
    public let priceIncreased: Bool
    public let previousAmount: Double?
}
public struct DetectedSubscriptionsResponse: Decodable { public let success: Bool; public let subscriptions: [DetectedSubscriptionDto]; public let estimatedMonthlyTotal: Double }

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- mirrors bank-mfe's
// lib/shopping.ts NearbyMerchantAd exactly.
public struct NearbyAdDto: Decodable {
    public let id: String
    public let merchantId: String
    public let title: String
    public let description: String?
    public let radiusMeters: Int
    public let activeUntil: String
}
public struct NearbyMerchantAdDto: Decodable, Identifiable {
    public let ad: NearbyAdDto
    public let businessName: String
    public let distanceKm: Double
    public var id: String { ad.id }
}
public struct NearbyMerchantAdsResponse: Decodable { public let success: Bool; public let ads: [NearbyMerchantAdDto] }

/// Mirrors services/backend/eats's real DTOs exactly (2026-07-18) -- restaurant/menu
/// browsing reuses ShoppingMerchantDto/MerchantProductDto above (a restaurant IS a
/// Merchant, a menu item IS a MerchantProduct -- see rw.itunda.eats.EatsOrderService's
/// own doc comment).
// selectedChoiceIds added 2026-07-21 (v1: required single-select only) -- one choice
// id per required option group on this menu item; omitted/nil for any item with no
// option groups, the pre-existing, unaffected case. See MenuOptionGroup.kt's own doc
// comment on the backend for the full account.
public struct EatsOrderItemRequest: Encodable {  public let menuItemId: String; public let quantity: Int; public let selectedChoiceIds: [String]?; public init(menuItemId: String, quantity: Int, selectedChoiceIds: [String]?) { self.menuItemId = menuItemId; self.quantity = quantity; self.selectedChoiceIds = selectedChoiceIds } }
public struct PlaceEatsOrderRequest: Encodable {
    public let restaurantId: String
    public let items: [EatsOrderItemRequest]
    public let deliveryAddress: String
    public let deliveryLatitude: Double?
    public let deliveryLongitude: Double?
    public let deliveryNotes: String?
    // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- backend-complete
    // since 2026-07-26, bank-mfe/Android clients 2026-07-31; this is the iOS client.
    public let fulfillmentType: String
    public init(restaurantId: String, items: [EatsOrderItemRequest], deliveryAddress: String, deliveryLatitude: Double?, deliveryLongitude: Double?, deliveryNotes: String?, fulfillmentType: String = "DELIVERY") { self.restaurantId = restaurantId; self.items = items; self.deliveryAddress = deliveryAddress; self.deliveryLatitude = deliveryLatitude; self.deliveryLongitude = deliveryLongitude; self.deliveryNotes = deliveryNotes; self.fulfillmentType = fulfillmentType }
}
public struct UpdateEatsOrderStatusRequest: Encodable { public let status: String }
public struct SetRiderAvailabilityRequest: Encodable { public let available: Bool }

// Real self-hosted address-search autocomplete (2026-07-18) -- backed by itunda's own
// Nominatim geocoder, not a third-party Maps API. See
// EatsOrderService.searchDeliveryAddress's own doc comment.
public struct AddressSuggestionDto: Decodable, Identifiable {
    public var id: String { displayName }
    public let displayName: String
    public let latitude: Double
    public let longitude: Double
}
public struct AddressSearchResponse: Decodable { public let success: Bool; public let suggestions: [AddressSuggestionDto] }

// Real post-delivery ratings & reviews (2026-07-18) -- see EatsReviewService's own doc
// comment. Ported from bank-mfe's own review UI, the template for this iOS version.
public struct SubmitEatsReviewRequest: Encodable {
    public let restaurantRating: Int
    public let restaurantComment: String?
    public let riderRating: Int
    public let riderComment: String?
}
public struct EatsReviewDto: Decodable {
    public let id: String
    public let orderId: String
    public let buyerId: String
    public let restaurantId: String
    public let riderId: String
    public let restaurantRating: Int
    public let restaurantComment: String?
    public let riderRating: Int
    public let riderComment: String?
    // Real owner-side reply (item 184/185/186) -- see
    // EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe (item 184)
    // and Android (item 185) already have this; this is the first iOS client.
    public let ownerReply: String?
    public let ownerRepliedAt: String?
    public let createdAt: String
}
public struct EatsReviewResponse: Decodable { public let success: Bool; public let review: EatsReviewDto }
public struct EatsReviewsResponse: Decodable { public let success: Bool; public let reviews: [EatsReviewDto] }
public struct EatsRatingResponse: Decodable { public let success: Bool; public let average: Double?; public let count: Int }
public struct ReplyToEatsReviewRequest: Encodable { public let reply: String }

public struct EatsOrderDto: Decodable, Identifiable {
    public let id: String
    public let buyerId: String
    public let restaurantId: String
    public let riderId: String?
    public let deliveryAddress: String
    public let itemsSubtotal: Double
    public let deliveryFee: Double
    public let platformFee: Double
    public let totalAmount: Double
    public let transactionId: String
    public let deliveryPayoutTransactionId: String?
    public let status: String
    public let createdAt: String
    public let updatedAt: String
    public let refundTransactionId: String?
    public let deliveryLatitude: Double?
    public let deliveryLongitude: Double?
    public let distanceKm: Double?
    public let deliveryNotes: String?
}
public struct EatsOrderItemDto: Decodable, Identifiable {
    public let id: String; public let orderId: String; public let productId: String; public let productName: String; public let unitPrice: Double; public let quantity: Int
    // Real menu-options receipt breakdown (2026-07-21) -- unitPrice above already
    // includes every selected choice's priceDelta; this is purely a human-readable
    // summary, never a second pricing source. See EatsOrderItem.kt's own doc comment.
    public let selectedOptionsJson: String?
}
public struct EatsOrderDetailResponse: Decodable { public let success: Bool; public let order: EatsOrderDto; public let items: [EatsOrderItemDto] }
public struct EatsOrdersResponse: Decodable { public let success: Bool; public let orders: [EatsOrderDto] }

public struct RiderLocationDto: Decodable { public let latitude: Double; public let longitude: Double; public let updatedAt: String }
public struct EatsRiderLocationResponse: Decodable { public let success: Bool; public let available: Bool; public let location: RiderLocationDto? }

public struct RiderDto: Decodable, Identifiable { public let id: String; public let userId: String; public let walletId: String; public let status: String; public let available: Bool; public let createdAt: String }
public struct RiderResponse: Decodable { public let success: Bool; public let rider: RiderDto }

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
public struct FavoriteRestaurantDto: Decodable, Identifiable {
    public let restaurantId: String
    public let businessName: String
    public let category: String?
    public let favoritedAt: String
    public var id: String { restaurantId }
}
public struct FavoriteRestaurantsResponse: Decodable { public let success: Bool; public let favorites: [FavoriteRestaurantDto] }
public struct SuccessResponse: Decodable { public let success: Bool }

// Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- backend real
// since 2026-07-26, bank-mfe/Android clients since 2026-07-28/2026-07-31; this is the
// iOS client. Free delivery only at a restaurant that has itself opted in, never a
// blanket waiver.
public struct EatsMembershipDto: Decodable {
    public let id: String
    public let userId: String
    public let activeUntil: String
    public let createdAt: String
    public let updatedAt: String
}
public struct EatsMembershipResponse: Decodable { public let success: Bool; public let membership: EatsMembershipDto? }
public struct SubscribeEatsMembershipRequest: Encodable { public let days: Int }
public struct EatsMembershipTier { public let days: Int; public let priceRwf: Int }
public let eatsMembershipTiers: [EatsMembershipTier] = [EatsMembershipTier(days: 30, priceRwf: 1500), EatsMembershipTier(days: 90, priceRwf: 4000)]

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// backend PlatformMembership.kt's own doc comment. Deliberately distinct from Eats
// Club above: waives the fee at every restaurant, no merchant opt-in required.
public typealias PlatformMembershipDto = EatsMembershipDto
public struct PlatformMembershipResponse: Decodable { public let success: Bool; public let membership: PlatformMembershipDto? }
public struct SubscribePlatformMembershipRequest: Encodable { public let days: Int }
public let platformMembershipTiers: [EatsMembershipTier] = [EatsMembershipTier(days: 30, priceRwf: 2500), EatsMembershipTier(days: 90, priceRwf: 6500)]

// Real Toss Securities-style stock investing (2026-07-20) -- the first iOS UI this
// feature has ever had, ported from bank-mfe/Android the same session. Day-over-day
// movement/history are real deterministic simulations, not live RSE data -- see the
// backend's StockCatalog.kt for the full account.
public struct StockDto: Decodable, Identifiable {
    public let id: String
    public let symbol: String
    public let name: String
    public let price: Double
    public let change: Double
    public let changePercent: Double
    public let marketCap: String
    public let volume: Int
}
public struct StocksResponse: Decodable { public let success: Bool; public let stocks: [StockDto]? ; public let watchlist: [StockDto]? }
public struct StockPricePointDto: Decodable, Identifiable { public let date: String; public let price: Double; public var id: String { date } }
public struct StockHistoryResponse: Decodable { public let success: Bool; public let history: [StockPricePointDto] }
public struct PortfolioValuePointDto: Decodable, Identifiable { public let date: String; public let value: Double; public var id: String { date } }
public struct PortfolioHistoryResponse: Decodable { public let success: Bool; public let history: [PortfolioValuePointDto] }
public struct StockHoldingDto: Decodable, Identifiable {
    public let stockId: String
    public let symbol: String
    public let name: String
    public let shares: Double
    public let avgPrice: Double
    public let currentPrice: Double
    public let value: Double
    public let returnPercent: Double
    public var id: String { stockId }

    enum CodingKeys: String, CodingKey {
        case stockId, symbol, name, shares, avgPrice, currentPrice, value
        case returnPercent = "return"
    }
}
public struct StockPortfolioDto: Decodable {
    public let totalValue: Double
    public let totalReturn: Double
    public let totalReturnPercent: Double
    public let holdings: [StockHoldingDto]
}
public struct StockPortfolioResponse: Decodable { public let success: Bool; public let portfolio: StockPortfolioDto }
public struct TradeStockRequest: Encodable { public let stockId: String; public let shares: Double }
public struct TradeStockResponse: Decodable { public let success: Bool; public let message: String }

extension NetworkClient {
    public func startConversation(phoneNumber: String) async throws -> ConversationResponse {
        try await authenticatedPost("api/v1/messages/conversations", body: StartConversationRequest(phoneNumber: phoneNumber, otherUserId: nil))
    }

    public func startConversation(otherUserId: String) async throws -> ConversationResponse {
        try await authenticatedPost("api/v1/messages/conversations", body: StartConversationRequest(phoneNumber: nil, otherUserId: otherUserId))
    }

    public func getConversations() async throws -> ConversationsResponse { try await get("api/v1/messages/conversations") }

    public func getTalkContacts() async throws -> TalkContactsResponse { try await get("api/v1/messages/contacts") }

    public func getMessages(conversationId: String) async throws -> MessagesResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/messages")
    }

    public func searchMessages(conversationId: String, query: String) async throws -> MessagesResponse {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        return try await get("api/v1/messages/conversations/\(conversationId)/messages/search?query=\(encoded)")
    }

    public func sendMessage(conversationId: String, body: String, replyToMessageId: String? = nil) async throws -> MessageResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/messages", body: SendMessageRequest(body: body, replyToMessageId: replyToMessageId))
    }

    public func deleteMessage(conversationId: String, messageId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/conversations/\(conversationId)/messages/\(messageId)")
    }

    public func getPinnedConversationMessage(conversationId: String) async throws -> PinnedMessageResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/pin")
    }

    public func pinConversationMessage(conversationId: String, messageId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/pin/\(messageId)", body: EmptyRequest())
    }

    public func unpinConversationMessage(conversationId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/conversations/\(conversationId)/pin")
    }

    public func blockConversationParticipant(conversationId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/block", body: EmptyRequest())
    }

    public func unblockConversationParticipant(conversationId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/conversations/\(conversationId)/block")
    }

    public func getConversationQuiet(conversationId: String) async throws -> ConversationQuietResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/quiet")
    }

    public func setConversationQuiet(conversationId: String, quiet: Bool) async throws -> ConversationQuietResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/quiet", body: SetConversationQuietRequest(quiet: quiet))
    }

    public func reportChatMessage(messageId: String, reason: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/chat/reports", body: CreateChatReportRequest(messageId: messageId, reason: reason))
    }

    // Real toggle -- tapping an already-active reaction removes it, same semantics as
    // MessagingService.toggleReaction on the backend.
    public func toggleReaction(messageId: String, emoji: String) async throws -> ReactionsResponse {
        try await authenticatedPost("api/v1/messages/messages/\(messageId)/reactions", body: ToggleReactionRequest(emoji: emoji))
    }

    // Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
    public func createGroup(name: String, memberPhoneNumbers: [String]) async throws -> GroupResponse {
        try await authenticatedPost("api/v1/messages/groups", body: CreateGroupRequest(name: name, memberPhoneNumbers: memberPhoneNumbers))
    }

    public func getMyGroups() async throws -> GroupsResponse { try await get("api/v1/messages/groups") }

    public func getGroupMessages(groupId: String) async throws -> GroupMessagesResponse {
        try await get("api/v1/messages/groups/\(groupId)/messages")
    }

    public func sendGroupMessage(groupId: String, body: String, replyToMessageId: String? = nil) async throws -> GroupMessageResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/messages", body: SendGroupMessageRequest(body: body, replyToMessageId: replyToMessageId))
    }

    public func deleteGroupMessage(groupId: String, messageId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/groups/\(groupId)/messages/\(messageId)")
    }

    public func toggleGroupReaction(groupMessageId: String, emoji: String) async throws -> ReactionsResponse {
        try await authenticatedPost("api/v1/messages/groups/messages/\(groupMessageId)/reactions", body: ToggleReactionRequest(emoji: emoji))
    }

    public func getGroupMembers(groupId: String) async throws -> GroupMembersResponse {
        try await get("api/v1/messages/groups/\(groupId)/members")
    }

    // Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's
    // own doc comment on the backend. Works for any set of user ids, not just 1:1
    // conversation partners -- e.g. a group thread can pass every member's id.
    public func getPresence(userIds: [String]) async throws -> PresenceResponse {
        try await get("api/v1/messages/presence", query: userIds.map { URLQueryItem(name: "userIds", value: $0) })
    }

    /// Real WebSocket live-transport (2026-07-18) -- see
    /// rw.itunda.app.websocket.MessagingWebSocketHandler's own doc comment for the real
    /// backend push shape this mirrors exactly (also ported to Android the same day).
    /// Native `URLSessionWebSocketTask`, no third-party dependency. Routes both
    /// "message" (1:1) and "group_message" pushes -- group chat gained a real mobile UI
    /// the same day this was extended.
    public func connectMessagingSocket(onPush: @escaping (MessagingSocketPush) -> Void) -> URLSessionWebSocketTask {
        let wsBase = NetworkClient.baseURLString
            .replacingOccurrences(of: "http://", with: "ws://")
            .replacingOccurrences(of: "https://", with: "wss://")
        let token = KeychainTokenStore.shared.getAccessToken() ?? ""
        let url = URL(string: "\(wsBase)ws/messaging?token=\(token)")!
        let task = session.webSocketTask(with: url)
        task.resume()
        receiveMessagingSocketFrame(task, onPush: onPush)
        return task
    }

    // Real typing indicator send (2026-07-19) -- best-effort, matching bank-mfe/Android's
    // own sendTyping helpers; a failed send on a closed/never-connected task is silently
    // swallowed, same as every other non-critical real-time signal in this layer.
    public func sendTyping(_ task: URLSessionWebSocketTask, conversationId: String? = nil, groupConversationId: String? = nil) {
        var payload: [String: String] = ["type": "typing"]
        if let conversationId { payload["conversationId"] = conversationId }
        if let groupConversationId { payload["groupConversationId"] = groupConversationId }
        guard let data = try? JSONEncoder().encode(payload), let text = String(data: data, encoding: .utf8) else { return }
        task.send(.string(text)) { _ in }
    }

    private func receiveMessagingSocketFrame(_ task: URLSessionWebSocketTask, onPush: @escaping (MessagingSocketPush) -> Void) {
        task.receive { [weak self] result in
            guard let self else { return }
            if case .success(.string(let text)) = result, let data = text.data(using: .utf8) {
                // Two-pass decode: only fully decode `message` once we've confirmed the
                // real `type` -- a group_message push's nested message object has a
                // different shape (groupConversationId, not conversationId) and would
                // otherwise fail MessageDto's strict decode, and vice versa.
                if let typeEnvelope = try? JSONDecoder().decode(MessagingSocketTypeEnvelope.self, from: data) {
                    if typeEnvelope.type == "message",
                       let envelope = try? JSONDecoder().decode(MessagingSocketMessageEnvelope.self, from: data) {
                        onPush(.directMessage(conversationId: envelope.conversationId, message: envelope.message))
                    } else if typeEnvelope.type == "group_message",
                              let envelope = try? JSONDecoder().decode(MessagingSocketGroupEnvelope.self, from: data) {
                        onPush(.groupMessage(groupConversationId: envelope.groupConversationId, message: envelope.message))
                    } else if typeEnvelope.type == "presence",
                              let envelope = try? JSONDecoder().decode(MessagingSocketPresenceEnvelope.self, from: data) {
                        onPush(.presenceChange(userId: envelope.userId, online: envelope.online))
                    } else if typeEnvelope.type == "typing",
                              let envelope = try? JSONDecoder().decode(MessagingSocketTypingEnvelope.self, from: data) {
                        onPush(.typingChange(conversationId: envelope.conversationId, groupConversationId: envelope.groupConversationId, userId: envelope.userId))
                    } else if typeEnvelope.type == "reaction",
                              let envelope = try? JSONDecoder().decode(MessagingSocketReactionEnvelope.self, from: data) {
                        onPush(.reactionChange(
                            conversationId: envelope.conversationId, groupConversationId: envelope.groupConversationId,
                            messageId: envelope.messageId, reactions: envelope.reactions,
                        ))
                    }
                }
            }
            // Real, non-critical -- a malformed/unexpected push or a transient receive
            // error shouldn't kill the app; the 4s poll stays as the real fallback
            // delivery path regardless. Only a genuinely closed socket stops the loop.
            if case .success = result {
                self.receiveMessagingSocketFrame(task, onPush: onPush)
            } else if case .failure = result {
                // Socket closed/errored -- stop listening, poll takes over.
            }
        }
    }

    public func createListing(title: String, description: String, price: Double, category: String, latitude: Double? = nil, longitude: Double? = nil, meetingPlace: String? = nil) async throws -> ListingResponse {
        try await authenticatedPost("api/v1/marketplace/listings", body: CreateListingRequest(title: title, description: description, price: price, category: category, latitude: latitude, longitude: longitude, meetingPlace: meetingPlace))
    }

    public func browseListings(category: String? = nil) async throws -> ListingsResponse {
        var path = "api/v1/marketplace/listings"
        if let category, !category.isEmpty {
            path += "?category=\(category.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? category)"
        }
        return try await get(path)
    }

    public func getNearbyListings(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> ListingsResponse {
        try await get("api/v1/marketplace/listings/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    public func getMyListings() async throws -> ListingsResponse { try await get("api/v1/marketplace/my-listings") }

    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend ListingRepository's own doc comment.
    public func getMyPurchases() async throws -> ListingsResponse { try await get("api/v1/marketplace/my-purchases") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    // Real 400 on the caller's own neighborhood-not-set case, matching Android/web.
    public func getListingsMyNeighborhood(category: String? = nil) async throws -> ListingsResponse {
        try await get("api/v1/marketplace/listings/my-neighborhood", query: [URLQueryItem(name: "category", value: category)])
    }

    public func markListingSold(_ listingId: String, buyerPhoneNumber: String? = nil) async throws -> ListingResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/mark-sold", body: MarkSoldRequest(buyerPhoneNumber: buyerPhoneNumber))
    }

    // Real seller-paid sponsored placement -- see backend
    // rw.itunda.marketplace.MarketplaceService.boostListing's own doc comment. Android
    // already has this; this is the first iOS client (bank-mfe never built it either).
    public func getBoostTiers() async throws -> BoostTiersResponse { try await get("api/v1/marketplace/boost-tiers") }
    public func boostListing(_ listingId: String, days: Int) async throws -> ListingResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/boost", body: BoostListingRequest(days: days), idempotencyKey: UUID().uuidString)
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    public func submitListingReview(_ listingId: String, goodPoints: [String], uncomfortablePoints: [String]) async throws -> HoodReviewResponse {
        try await authenticatedPost(
            "api/v1/marketplace/listings/\(listingId)/review",
            body: SubmitHoodReviewRequest(goodPoints: goodPoints, uncomfortablePoints: uncomfortablePoints),
        )
    }

    public func getListingReviews(_ listingId: String) async throws -> HoodReviewsResponse {
        try await get("api/v1/marketplace/listings/\(listingId)/review")
    }

    public func removeListing(_ listingId: String) async throws -> ListingResponse {
        try await authenticatedDelete("api/v1/marketplace/listings/\(listingId)")
    }

    public func contactSeller(listingId: String) async throws -> ContactSellerResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/contact-seller", body: EmptyBody())
    }

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService.
    public func makeOffer(listingId: String, amount: Double) async throws -> PriceOfferResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/offers", body: MakeOfferRequest(amount: amount))
    }

    public func respondToOffer(offerId: String, action: String, counterAmount: Double? = nil) async throws -> PriceOfferResponse {
        try await authenticatedPost("api/v1/marketplace/offers/\(offerId)/respond", body: RespondToOfferRequest(action: action, counterAmount: counterAmount))
    }

    public func getOffersForConversation(conversationId: String) async throws -> PriceOffersResponse {
        try await get("api/v1/marketplace/conversations/\(conversationId)/offers")
    }

    // Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe) -- ported here,
    // closing the "Android/iOS don't have this yet" gap that row's own doc comment
    // named. Mirrors addFavoriteRestaurant/removeFavoriteRestaurant exactly.
    public func addListingFavorite(_ listingId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/favorite", body: EmptyBody())
    }

    public func removeListingFavorite(_ listingId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/marketplace/listings/\(listingId)/favorite")
    }

    public func getMyFavoriteListings() async throws -> FavoriteListingsResponse { try await get("api/v1/marketplace/listings/favorites") }

    // Real KakaoTalk-style gift send/claim (2026-07-20) -- see GiftService.
    public func sendGiftInConversation(conversationId: String, amount: Double, note: String?, theme: String? = nil) async throws -> GiftResponse {
        try await authenticatedPost(
            "api/v1/gifts/conversations/\(conversationId)",
            body: SendGiftInConversationRequest(amount: amount, note: note, theme: theme),
            idempotencyKey: UUID().uuidString
        )
    }

    public func claimGift(giftId: String) async throws -> GiftResponse {
        try await authenticatedPost("api/v1/gifts/\(giftId)/claim", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func getGiftsForConversation(conversationId: String) async throws -> GiftsResponse {
        try await get("api/v1/gifts/conversations/\(conversationId)")
    }

    // Real KakaoTalk Emoticon Store (item 136) -- mirrors bank-mfe's lib/emoticons.ts
    // (item 133) and Android's ApiService.kt (item 135) exactly.
    public func getEmoticonPacks() async throws -> EmoticonPacksResponse { try await get("api/v1/emoticons/packs") }

    public func getPackEmoticons(packId: String) async throws -> EmoticonsResponse { try await get("api/v1/emoticons/packs/\(packId)") }

    public func getOwnedEmoticonPacks() async throws -> OwnedEmoticonPacksResponse { try await get("api/v1/emoticons/packs/owned") }

    public func purchaseEmoticonPack(packId: String) async throws -> OwnedEmoticonPackResponse {
        try await authenticatedPost("api/v1/emoticons/packs/\(packId)/purchase", body: EmptyBody())
    }

    public func giftEmoticonPack(packId: String, recipientPhoneNumber: String) async throws -> GiftedEmoticonPackResponse {
        try await authenticatedPost("api/v1/emoticons/packs/\(packId)/gift", body: GiftEmoticonPackRequest(recipientPhoneNumber: recipientPhoneNumber))
    }

    public func sendEmoticon(conversationId: String, emoticonId: String) async throws -> MessageResponse {
        try await authenticatedPost("api/v1/emoticons/conversations/\(conversationId)/send", body: SendEmoticonRequest(emoticonId: emoticonId))
    }

    public func sendGroupEmoticon(groupId: String, emoticonId: String) async throws -> GroupMessageResponse {
        try await authenticatedPost("api/v1/emoticons/groups/\(groupId)/send", body: SendEmoticonRequest(emoticonId: emoticonId))
    }

    // Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
    public func getCommunityCategories() async throws -> CommunityCategoriesResponse { try await get("api/v1/community/categories") }

    public func createCommunityPost(
        category: String, title: String, body: String, latitude: Double? = nil, longitude: Double? = nil,
        eventDate: String? = nil, capacity: Int? = nil
    ) async throws -> CommunityPostResponse {
        try await authenticatedPost(
            "api/v1/community/posts",
            body: CreateCommunityPostRequest(category: category, title: title, body: body, latitude: latitude, longitude: longitude, eventDate: eventDate, capacity: capacity)
        )
    }

    public func browseCommunityPosts(category: String? = nil) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts", query: [URLQueryItem(name: "category", value: category)])
    }

    public func getNearbyCommunityPosts(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    public func getMyCommunityPosts() async throws -> CommunityPostsResponse { try await get("api/v1/community/my-posts") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    public func getCommunityPostsMyNeighborhood(category: String? = nil) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts/my-neighborhood", query: [URLQueryItem(name: "category", value: category)])
    }

    public func getCommunityPost(_ postId: String) async throws -> CommunityPostDetailResponse { try await get("api/v1/community/posts/\(postId)") }

    public func removeCommunityPost(_ postId: String) async throws -> CommunityPostResponse {
        try await authenticatedDelete("api/v1/community/posts/\(postId)")
    }

    public func getCommunityComments(_ postId: String) async throws -> CommunityCommentsResponse {
        try await get("api/v1/community/posts/\(postId)/comments")
    }

    public func addCommunityComment(_ postId: String, body: String) async throws -> CommunityCommentResponse {
        try await authenticatedPost("api/v1/community/posts/\(postId)/comments", body: AddCommunityCommentRequest(body: body))
    }

    public func toggleCommunityLike(_ postId: String) async throws -> ToggleCommunityLikeResponse {
        try await authenticatedPost("api/v1/community/posts/\(postId)/like", body: EmptyBody())
    }

    // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see backend
    // CommunityService.joinMeetup's own doc comment.
    public func joinCommunityMeetup(_ postId: String) async throws -> JoinMeetupResponse {
        try await authenticatedPost("api/v1/community/posts/\(postId)/join", body: EmptyBody())
    }

    public func scheduleMeetupSessions(_ postId: String, dates: [String]) async throws -> MeetupSessionsResponse {
        try await authenticatedPost("api/v1/community/posts/\(postId)/sessions", body: ScheduleMeetupSessionsRequest(dates: dates))
    }
    public func getMeetupSessions(_ postId: String) async throws -> MeetupSessionsResponse {
        try await get("api/v1/community/posts/\(postId)/sessions")
    }
    public func checkIntoMeetupSession(_ sessionId: String) async throws -> MeetupAttendanceResponse {
        try await authenticatedPost("api/v1/community/sessions/\(sessionId)/check-in", body: EmptyBody())
    }
    public func finalizeGroupBuy(_ postId: String, totalAmount: Double, description: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/community/posts/\(postId)/finalize-group-buy", body: FinalizeGroupBuyRequest(totalAmount: totalAmount, description: description))
    }

    // Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
    public func getJobCategories() async throws -> JobCategoriesResponse { try await get("api/v1/jobs/categories") }

    public func createJobPost(category: String, title: String, description: String, payType: String, payAmount: Double, latitude: Double? = nil, longitude: Double? = nil) async throws -> JobPostResponse {
        try await authenticatedPost("api/v1/jobs/posts", body: CreateJobPostRequest(category: category, title: title, description: description, payType: payType, payAmount: payAmount, latitude: latitude, longitude: longitude))
    }

    public func browseJobPosts(category: String? = nil) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts", query: [URLQueryItem(name: "category", value: category)])
    }

    public func getNearbyJobPosts(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    public func getMyJobPosts() async throws -> JobPostsResponse { try await get("api/v1/jobs/my-posts") }

    // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend JobPostRepository's own doc comment.
    public func getMyWorkedJobPosts() async throws -> JobPostsResponse { try await get("api/v1/jobs/my-worked-posts") }

    public func addJobPostFavorite(_ jobPostId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/jobs/posts/\(jobPostId)/favorite", body: EmptyBody())
    }

    public func removeJobPostFavorite(_ jobPostId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/jobs/posts/\(jobPostId)/favorite")
    }

    public func getMyFavoriteJobPosts() async throws -> FavoriteJobPostsResponse { try await get("api/v1/jobs/posts/favorites") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    public func getJobPostsMyNeighborhood(category: String? = nil) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts/my-neighborhood", query: [URLQueryItem(name: "category", value: category)])
    }

    public func markJobPostFilled(_ jobPostId: String, workerPhoneNumber: String? = nil) async throws -> JobPostResponse {
        try await authenticatedPost("api/v1/jobs/posts/\(jobPostId)/mark-filled", body: MarkFilledRequest(workerPhoneNumber: workerPhoneNumber))
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    public func submitJobPostReview(_ jobPostId: String, goodPoints: [String], uncomfortablePoints: [String]) async throws -> HoodReviewResponse {
        try await authenticatedPost(
            "api/v1/jobs/posts/\(jobPostId)/review",
            body: SubmitHoodReviewRequest(goodPoints: goodPoints, uncomfortablePoints: uncomfortablePoints),
        )
    }

    public func getJobPostReviews(_ jobPostId: String) async throws -> HoodReviewsResponse {
        try await get("api/v1/jobs/posts/\(jobPostId)/review")
    }

    public func removeJobPost(_ jobPostId: String) async throws -> JobPostResponse {
        try await authenticatedDelete("api/v1/jobs/posts/\(jobPostId)")
    }

    public func contactPoster(_ jobPostId: String) async throws -> ContactPosterResponse {
        try await authenticatedPost("api/v1/jobs/posts/\(jobPostId)/contact-poster", body: EmptyBody())
    }

    // Real single-post-detail fetch -- needed to resolve a job post's title from a bare
    // applicationId in "My applications" below, same as Android's own item-196 addition.
    public func getJobPost(_ jobPostId: String) async throws -> JobPostResponse { try await get("api/v1/jobs/posts/\(jobPostId)") }

    // Real 당근알바-style structured application (2026-07-25 on Android, ported here
    // 2026-07-29) -- see ApplyToJobRequest's own doc comment.
    public func applyToJob(_ jobPostId: String, message: String) async throws -> JobApplicationResponse {
        try await authenticatedPost("api/v1/jobs/posts/\(jobPostId)/apply", body: ApplyToJobRequest(message: message))
    }

    public func getApplicationsForJobPost(_ jobPostId: String) async throws -> JobApplicationsResponse {
        try await get("api/v1/jobs/posts/\(jobPostId)/applications")
    }

    public func getMyJobApplications() async throws -> JobApplicationsResponse { try await get("api/v1/jobs/my-applications") }

    public func respondToJobApplication(_ applicationId: String, accept: Bool) async throws -> JobApplicationResponse {
        try await authenticatedPost("api/v1/jobs/applications/\(applicationId)/respond", body: RespondToApplicationRequest(accept: accept))
    }

    public func reportHoodContent(targetType: String, targetId: String, reason: String) async throws -> HoodReportResponse {
        try await authenticatedPost("api/v1/hood/reports", body: CreateHoodReportRequest(targetType: targetType, targetId: targetId, reason: reason))
    }

    // Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
    public func getPropertyTypes() async throws -> PropertyTypesResponse { try await get("api/v1/realestate/property-types") }

    public func createPropertyListing(
        listingType: String, propertyType: String, title: String, description: String, price: Double,
        bedrooms: Int? = nil, sizeSqm: Double? = nil,
        latitude: Double? = nil, longitude: Double? = nil,
    ) async throws -> PropertyListingResponse {
        try await authenticatedPost(
            "api/v1/realestate/listings",
            body: CreatePropertyListingRequest(
                listingType: listingType, propertyType: propertyType, title: title, description: description, price: price,
                bedrooms: bedrooms, sizeSqm: sizeSqm, latitude: latitude, longitude: longitude,
            ),
        )
    }

    public func browsePropertyListings(listingType: String? = nil, propertyType: String? = nil) async throws -> PropertyListingsResponse {
        try await get("api/v1/realestate/listings", query: [
            URLQueryItem(name: "listingType", value: listingType),
            URLQueryItem(name: "propertyType", value: propertyType),
        ])
    }

    public func getNearbyPropertyListings(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> PropertyListingsResponse {
        try await get("api/v1/realestate/listings/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    public func getMyPropertyListings() async throws -> PropertyListingsResponse { try await get("api/v1/realestate/my-listings") }

    // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend PropertyListingRepository's own doc comment.
    public func getMyAcquiredPropertyListings() async throws -> PropertyListingsResponse { try await get("api/v1/realestate/my-acquired-listings") }
    public func addPropertyListingFavorite(_ id: String) async throws -> SuccessResponse { try await authenticatedPost("api/v1/realestate/listings/\(id)/favorite", body: EmptyBody()) }
    public func removePropertyListingFavorite(_ id: String) async throws -> SuccessResponse { try await authenticatedDelete("api/v1/realestate/listings/\(id)/favorite") }
    public func getMyFavoritePropertyListings() async throws -> FavoritePropertyListingsResponse { try await get("api/v1/realestate/listings/favorites") }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see setNeighborhood.
    // Deliberately not combined with listingType/propertyType filters -- an honest v1
    // scoping choice, same as the real backend endpoint this calls.
    public func getPropertyListingsMyNeighborhood() async throws -> PropertyListingsResponse {
        try await get("api/v1/realestate/listings/my-neighborhood")
    }

    public func markPropertyListingTaken(_ propertyListingId: String, counterpartyPhoneNumber: String? = nil) async throws -> PropertyListingResponse {
        try await authenticatedPost(
            "api/v1/realestate/listings/\(propertyListingId)/mark-taken",
            body: MarkTakenRequest(counterpartyPhoneNumber: counterpartyPhoneNumber),
        )
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    public func submitPropertyListingReview(_ propertyListingId: String, goodPoints: [String], uncomfortablePoints: [String]) async throws -> HoodReviewResponse {
        try await authenticatedPost(
            "api/v1/realestate/listings/\(propertyListingId)/review",
            body: SubmitHoodReviewRequest(goodPoints: goodPoints, uncomfortablePoints: uncomfortablePoints),
        )
    }

    public func getPropertyListingReviews(_ propertyListingId: String) async throws -> HoodReviewsResponse {
        try await get("api/v1/realestate/listings/\(propertyListingId)/review")
    }

    // Real ownership verification (2026-07-25) -- see PropertyListingDto's own doc
    // comment. document-upload + human review; iOS has no real photo/document upload
    // pipeline (same gap bank-mfe's own port named), so this takes a documentUrl
    // directly rather than inventing one -- an honest v1 scope-down, not a silent gap.
    public func submitPropertyOwnershipVerification(_ propertyListingId: String, documentUrl: String) async throws -> SuccessResponse {
        try await authenticatedPost(
            "api/v1/realestate/listings/\(propertyListingId)/verify-ownership",
            body: SubmitOwnershipVerificationRequest(documentUrl: documentUrl),
        )
    }

    public func removePropertyListing(_ propertyListingId: String) async throws -> PropertyListingResponse {
        try await authenticatedDelete("api/v1/realestate/listings/\(propertyListingId)")
    }

    public func contactLister(_ propertyListingId: String) async throws -> ContactListerResponse {
        try await authenticatedPost("api/v1/realestate/listings/\(propertyListingId)/contact-lister", body: EmptyBody())
    }

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService.
    public func makePropertyOffer(listingId: String, amount: Double) async throws -> PropertyPriceOfferResponse {
        try await authenticatedPost("api/v1/realestate/listings/\(listingId)/offers", body: MakePropertyOfferRequest(amount: amount))
    }

    public func respondToPropertyOffer(offerId: String, action: String, counterAmount: Double? = nil) async throws -> PropertyPriceOfferResponse {
        try await authenticatedPost("api/v1/realestate/offers/\(offerId)/respond", body: RespondToPropertyOfferRequest(action: action, counterAmount: counterAmount))
    }

    public func getPropertyOffersForConversation(conversationId: String) async throws -> PropertyPriceOffersResponse {
        try await get("api/v1/realestate/conversations/\(conversationId)/offers")
    }

    // Real category/search filter (2026-07-19) -- both optional and combinable. See
    // ShoppingController.getEligibleMerchants's own doc comment on the backend.
    // buyerLat/buyerLng added 2026-07-21 (see ShoppingMerchantDto's own doc comment) --
    // optional, mirroring Android's own getShoppingMerchants signature.
    public func getShoppingMerchants(category: String? = nil, q: String? = nil, buyerLat: Double? = nil, buyerLng: Double? = nil) async throws -> ShoppingMerchantsResponse {
        try await get("api/v1/shopping/merchants", query: [
            URLQueryItem(name: "category", value: category),
            URLQueryItem(name: "q", value: q),
            URLQueryItem(name: "buyerLat", value: buyerLat.map { String($0) }),
            URLQueryItem(name: "buyerLng", value: buyerLng.map { String($0) }),
        ])
    }

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    public func getMerchantCategories() async throws -> MerchantCategoriesResponse { try await get("api/v1/shopping/merchants/categories") }

    // Real "Deals" rail (2026-07-25) -- see backend MerchantProductRepository.findDeals's
    // own doc comment.
    public func getShopDeals() async throws -> DealsResponse { try await get("api/v1/shopping/products/deals") }

    // Real Naver Pay 멤버십 데이 (Membership Day) cashback boost -- see
    // rw.itunda.merchant.ShoppingCashbackService's own doc comment. bank-mfe/Android
    // already have this; this is the first iOS client.
    public func getMembershipDayStatus() async throws -> MembershipDayStatusResponse { try await get("api/v1/shopping/membership-day") }

    // Real "search this map" + "directions" (2026-07-19) -- see MapsService.
    public func searchPlaces(query: String) async throws -> MapsSearchResponse {
        try await get("api/v1/maps/search", query: [URLQueryItem(name: "q", value: query)])
    }

    // Backs the ruler (distance-measurement) tool's "what's here" label -- ported from
    // bank-mfe's own real reverseGeocode call, 2026-07-23.
    public func reverseGeocode(lat: Double, lng: Double) async throws -> MapsReverseGeocodeResponse {
        try await get("api/v1/maps/reverse", query: [
            URLQueryItem(name: "lat", value: String(lat)),
            URLQueryItem(name: "lng", value: String(lng)),
        ])
    }

    // mode added 2026-07-22 (default "DRIVING") -- see OsrmRoutingClient.route's own doc
    // comment on the backend for the real, separately-deployed foot-profile OSRM
    // instance this now lets a caller reach.
    public func getDirections(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String = "DRIVING") async throws -> MapsDirectionsResponse {
        try await get("api/v1/maps/directions", query: [
            URLQueryItem(name: "fromLat", value: String(fromLat)),
            URLQueryItem(name: "fromLng", value: String(fromLng)),
            URLQueryItem(name: "toLat", value: String(toLat)),
            URLQueryItem(name: "toLng", value: String(toLng)),
            URLQueryItem(name: "mode", value: mode),
        ])
    }

    public func getItineraryDirections(waypoints: [ItineraryWaypointRequest], mode: String = "DRIVING") async throws -> MapsDirectionsResponse {
        try await authenticatedPost("api/v1/maps/directions/itinerary", body: ItineraryDirectionsRequest(waypoints: waypoints, mode: mode))
    }

    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // own doc comment.
    public func getDirectionsAlternatives(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: String = "DRIVING") async throws -> MapsDirectionsAlternativesResponse {
        try await get("api/v1/maps/directions/alternatives", query: [
            URLQueryItem(name: "fromLat", value: String(fromLat)),
            URLQueryItem(name: "fromLng", value: String(fromLng)),
            URLQueryItem(name: "toLat", value: String(toLat)),
            URLQueryItem(name: "toLng", value: String(toLng)),
            URLQueryItem(name: "mode", value: mode),
        ])
    }

    // Real "nearby places" category search + bookmarked/favorite places (2026-07-19) --
    // see rw.itunda.maps.MapsService's own doc comment on the backend.
    public func searchNearbyPlaces(category: String, lat: Double, lng: Double, radiusKm: Double = 2.0) async throws -> MapNearbyResponse {
        try await get("api/v1/maps/nearby", query: [
            URLQueryItem(name: "category", value: category),
            URLQueryItem(name: "lat", value: String(lat)),
            URLQueryItem(name: "lng", value: String(lng)),
            URLQueryItem(name: "radiusKm", value: String(radiusKm)),
        ])
    }

    public func searchNearbyAgents(lat: Double, lng: Double, radiusKm: Double = 5.0) async throws -> NearbyAgentsResponse {
        try await get("api/v1/agents/nearby", query: [
            URLQueryItem(name: "latitude", value: String(lat)),
            URLQueryItem(name: "longitude", value: String(lng)),
            URLQueryItem(name: "radiusKm", value: String(radiusKm)),
        ])
    }

    public func getMyMapBookmarks() async throws -> MapBookmarksResponse { try await get("api/v1/maps/bookmarks") }

    public func addMapBookmark(displayName: String, latitude: Double, longitude: Double, folderName: String? = nil, color: String? = nil) async throws -> AddMapBookmarkResponse {
        try await authenticatedPost("api/v1/maps/bookmarks", body: AddMapBookmarkRequest(displayName: displayName, latitude: latitude, longitude: longitude, folderName: folderName, color: color))
    }

    // Real "move to folder" (2026-07-22) -- see MoveMapBookmarkRequest's own doc
    // comment. A real query-param PATCH -- same manual-request pattern
    // removeMapBookmark's own doc comment above already established for a query-param
    // request this client's authenticated* helpers don't directly support.
    public func moveMapBookmark(latitude: Double, longitude: Double, folderName: String, color: String) async throws -> MoveMapBookmarkResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/maps/bookmarks"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "lat", value: String(latitude)), URLQueryItem(name: "lng", value: String(longitude))]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(MoveMapBookmarkRequest(folderName: folderName, color: color))
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(MoveMapBookmarkResponse.self, from: data)
    }

    // A real query-param DELETE -- `authenticatedDelete(_:)` below takes no query, so
    // this is a one-off manual request, same pattern `searchDeliveryAddress` already
    // uses for its own query-param GET.
    public func removeMapBookmark(latitude: Double, longitude: Double) async throws -> SuccessResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/maps/bookmarks"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "lat", value: String(latitude)), URLQueryItem(name: "lng", value: String(longitude))]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "DELETE"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(SuccessResponse.self, from: data)
    }

    public func getMerchantProducts(merchantId: String) async throws -> MerchantProductsResponse {
        try await get("api/v1/shopping/merchants/\(merchantId)/products")
    }

    // Real cross-merchant product search (item 138) -- see ProductSearchResultDto's
    // own doc comment.
    public func searchProducts(_ query: String) async throws -> ProductSearchResponse {
        try await get("api/v1/shopping/products/search?q=\(query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query)")
    }

    // Real KakaoTalk-style 기프티콘 gift voucher (item 138) -- see GiftVoucherDto's own
    // doc comment.
    public func purchaseGiftVoucher(_ request: PurchaseGiftVoucherRequest) async throws -> GiftVoucherResponse {
        try await authenticatedPost("api/v1/gift-vouchers", body: request, idempotencyKey: UUID().uuidString)
    }

    public func getGiftVouchersForConversation(conversationId: String) async throws -> GiftVouchersResponse {
        try await get("api/v1/gift-vouchers/conversations/\(conversationId)")
    }

    public func extendGiftVoucherExpiry(voucherId: String) async throws -> GiftVoucherResponse {
        try await authenticatedPost("api/v1/gift-vouchers/\(voucherId)/extend", body: EmptyBody())
    }

    // Real Naver Smart Store-style "알림받기" (follow a store) -- first iOS client for
    // this feature (item 117, found via a content-grep sweep: bank-mfe has it,
    // Android/iOS didn't; Android ported the same day). Mirrors bank-mfe's
    // lib/shopping.ts and Android's ApiService.kt exactly.
    public func followMerchant(merchantId: String) async throws -> MerchantFollowResponse {
        try await authenticatedPost("api/v1/merchant/\(merchantId)/follow", body: EmptyBody())
    }
    public func unfollowMerchant(merchantId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/merchant/\(merchantId)/follow")
    }
    public func getMyFollowedMerchants() async throws -> FollowedMerchantsResponse {
        try await get("api/v1/merchant/follows?size=200")
    }

    // Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
    // (this app has no scanner), mirrors bank-mfe's lib/shopping.ts collectPayment/
    // payByStaticQr and Android's ApiService.kt exactly. This is the first iOS client for
    // either -- previously neither the dynamic per-sale flow nor the static QR flow
    // existed anywhere on this native consumer app.
    public func collectPayment(intentId: String, couponId: String? = nil) async throws -> CollectPaymentResultDto {
        try await authenticatedPost("api/v1/merchant/collect/\(intentId)", body: CollectPaymentRequest(couponId: couponId), idempotencyKey: UUID().uuidString)
    }
    public func payByStaticQr(merchantId: String, amount: Double, description: String? = nil) async throws -> CollectPaymentResultDto {
        try await authenticatedPost("api/v1/merchant/\(merchantId)/static-qr/pay", body: StaticQrPayRequest(amount: amount, description: description), idempotencyKey: UUID().uuidString)
    }

    // Real Face Pay -- see rw.itunda.merchant.FacePayService's own doc comment. The
    // backend has been fully real since 2026-07-13; bank-mfe/Android already have this;
    // this is the first iOS client. Enrolling swaps Pay-by-code's own collect call to
    // this channel -- same manual code entry, just a different real ledger channel
    // label, matching bank-mfe's own honest scope exactly (no device biometric prompt
    // gates it on any client, itunda's own).
    public func enrollFacePay() async throws -> FacePayEnrollmentResponse {
        try await authenticatedPost("api/v1/facepay/enroll", body: EmptyBody())
    }
    public func revokeFacePay() async throws -> FacePayEnrollmentResponse {
        try await authenticatedPost("api/v1/facepay/revoke", body: EmptyBody())
    }
    public func getFacePayStatus() async throws -> FacePayStatusResponse { try await get("api/v1/facepay/status") }
    public func collectWithFacePay(intentId: String) async throws -> CollectPaymentResultDto {
        try await authenticatedPost("api/v1/facepay/collect/\(intentId)", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func placeOrder(_ request: PlaceOrderRequest) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    public func getMyOrders() async throws -> OrdersResponse { try await get("api/v1/orders/my-orders") }

    public func getOrder(_ orderId: String) async throws -> OrderDetailResponse { try await get("api/v1/orders/\(orderId)") }

    /// Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    /// See rw.itunda.commerce.OrderService.cancelOrder's own doc comment.
    public func cancelOrder(_ orderId: String) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders/\(orderId)/cancel", body: EmptyBody())
    }

    /// Real post-delivery product reviews (2026-07-20) -- see OrderController.submitProductReview.
    public func submitProductReview(orderItemId: String, rating: Int, comment: String?) async throws -> ProductReviewResponse {
        try await authenticatedPost("api/v1/orders/items/\(orderItemId)/review", body: SubmitProductReviewRequest(rating: rating, comment: comment))
    }

    public func requestOrderReturn(orderId: String, type: String, reasonCode: String, reasonNote: String?) async throws -> OrderReturnRequestResponse {
        try await authenticatedPost("api/v1/orders/\(orderId)/return", body: RequestOrderReturnRequest(type: type, reasonCode: reasonCode, reasonNote: reasonNote))
    }

    public func getMyReturnRequests() async throws -> OrderReturnRequestsResponse {
        try await get("api/v1/orders/returns/my-requests")
    }

    public func getProductRating(_ productId: String) async throws -> ProductRatingResponse {
        try await get("api/v1/orders/products/\(productId)/rating")
    }

    public func getProductReviews(_ productId: String) async throws -> ProductReviewsResponse {
        try await get("api/v1/orders/products/\(productId)/reviews")
    }

    // Real Coupang-style pre-purchase product Q&A (상품문의) -- see
    // rw.itunda.commerce.ProductInquiryService's own doc comment. bank-mfe/Android
    // already have the buyer-side ask/view flow; this is the first iOS client. Honest
    // scope boundary: the seller-answer flow has zero UI anywhere yet, not even on
    // bank-mfe/merchant-mfe -- not a mobile-specific gap, so not built here either.
    public func askProductInquiry(_ productId: String, question: String) async throws -> ProductInquiryResponse {
        try await authenticatedPost("api/v1/orders/products/\(productId)/inquiries", body: AskProductInquiryRequest(question: question))
    }
    public func getProductInquiries(_ productId: String) async throws -> ProductInquiriesResponse {
        try await get("api/v1/orders/products/\(productId)/inquiries")
    }

    // Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing --
    // see rw.itunda.merchant.MerchantBillingService's own doc comment. Customer-facing
    // half only, matching bank-mfe's own scope (plan creation is merchant-owner-only,
    // not built here). bank-mfe/Android already have this; this is the first iOS client.
    public func getMerchantBillingPlans(_ merchantId: String) async throws -> MerchantBillingPlansResponse {
        try await get("api/v1/merchant/\(merchantId)/billing-plans")
    }
    public func subscribeToBillingPlan(_ planId: String) async throws -> MerchantBillingSubscriptionResponse {
        try await authenticatedPost("api/v1/merchant/billing-plans/\(planId)/subscribe", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }
    public func getMyBillingSubscriptions() async throws -> MerchantBillingSubscriptionsResponse {
        try await get("api/v1/merchant/billing-subscriptions/my")
    }
    public func cancelBillingSubscription(_ subscriptionId: String) async throws -> MerchantBillingSubscriptionResponse {
        try await authenticatedPost("api/v1/merchant/billing-subscriptions/\(subscriptionId)/cancel", body: EmptyBody())
    }

    // Real recurring-payment ("subscription") detection -- see
    // rw.itunda.wallet.SubscriptionDetectionService's own doc comment. bank-mfe/Android
    // already have this; this is the first iOS client.
    public func getDetectedSubscriptions() async throws -> DetectedSubscriptionsResponse { try await get("api/v1/wallet/subscriptions") }

    // Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- the
    // customer-facing browse half (merchant-mfe owns the paid create/extend side).
    // bank-mfe/Android already have this; this is the first iOS client.
    public func getNearbyMerchantAds(latitude: Double, longitude: Double) async throws -> NearbyMerchantAdsResponse {
        try await get("api/v1/merchant/ads/nearby", query: [URLQueryItem(name: "latitude", value: "\(latitude)"), URLQueryItem(name: "longitude", value: "\(longitude)")])
    }

    // Real Shop product wishlist (2026-07-24) -- backend shipped 2026-07-20
    // (ProductFavoriteService), bank-mfe wired the same day; this closes the iOS-side
    // gap. Mirrors addListingFavorite/removeListingFavorite exactly.
    public func addProductFavorite(_ productId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/orders/products/\(productId)/favorite", body: EmptyBody())
    }

    public func removeProductFavorite(_ productId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/orders/products/\(productId)/favorite")
    }

    public func getMyFavoriteProducts() async throws -> FavoriteProductsResponse { try await get("api/v1/orders/products/favorites") }

    // Real Coupang Eats-style food delivery (2026-07-18) -- see rw.itunda.eats.web.EatsController.
    public func placeEatsOrder(_ request: PlaceEatsOrderRequest) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    public func getMyEatsOrders() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/my-orders") }

    public func placeDineInOrder(_ request: PlaceDineInOrderRequest) async throws -> DineInOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/dine-in/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    public func getMyDineInOrders() async throws -> DineInOrdersResponse { try await get("api/v1/eats/dine-in/orders/my-orders") }

    public func getRestaurantDineInOrders() async throws -> DineInOrdersResponse { try await get("api/v1/eats/dine-in/orders/restaurant-orders") }

    public func updateDineInOrderStatus(id: String, status: String) async throws -> DineInOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/dine-in/orders/\(id)/status", body: UpdateDineInOrderStatusRequest(status: status))
    }

    public func cancelDineInOrder(id: String) async throws -> DineInOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/dine-in/orders/\(id)/cancel", body: EmptyBody())
    }

    /// Real order detail, including items -- backs the real "Reorder" button (2026-07-19):
    /// a buyer can re-populate a cart from a past order's real items rather than retyping
    /// their whole order from scratch.
    public func getEatsOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await get("api/v1/eats/orders/\(orderId)")
    }

    /// Real address-search autocomplete (2026-07-18) -- backed by itunda's own
    /// self-hosted Nominatim geocoder. See EatsController.searchDeliveryAddress's own
    /// doc comment. Uses URLComponents (not appendingPathComponent) so the query string
    /// is encoded correctly -- the first query-param GET in this client.
    // Real post-delivery ratings & reviews (2026-07-18) -- see EatsController.submitReview.
    public func submitEatsReview(orderId: String, restaurantRating: Int, restaurantComment: String?, riderRating: Int, riderComment: String?) async throws -> EatsReviewResponse {
        try await authenticatedPost(
            "api/v1/eats/orders/\(orderId)/review",
            body: SubmitEatsReviewRequest(restaurantRating: restaurantRating, restaurantComment: restaurantComment, riderRating: riderRating, riderComment: riderComment)
        )
    }

    public func getRestaurantRating(_ restaurantId: String) async throws -> EatsRatingResponse {
        try await get("api/v1/eats/restaurants/\(restaurantId)/rating")
    }

    // Real written-review list + owner-reply (item 184/185/186).
    public func getRestaurantReviews(_ restaurantId: String) async throws -> EatsReviewsResponse {
        try await get("api/v1/eats/restaurants/\(restaurantId)/reviews")
    }

    public func replyToRestaurantReview(_ reviewId: String, reply: String) async throws -> EatsReviewResponse {
        try await authenticatedPost("api/v1/eats/reviews/\(reviewId)/reply", body: ReplyToEatsReviewRequest(reply: reply))
    }

    public func searchDeliveryAddress(_ query: String) async throws -> AddressSearchResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/eats/geocode/search"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "q", value: query)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(AddressSearchResponse.self, from: data)
    }

    /// Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    /// only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    public func cancelEatsOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/cancel", body: EmptyBody())
    }
    public func getRiderDeliveries() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/rider-deliveries") }
    public func getAvailableDeliveries() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/available") }

    /// Real live rider-location tracking (2026-07-19 backend, first mobile client
    /// 2026-07-29, item 183) -- "the defining 'watch your order arrive' moment," see
    /// EatsOrderService.getRiderLocation's own doc comment. bank-mfe has had this since
    /// 2026-07-20 (LiveRiderMap.tsx); Android (item 182) and iOS main apps never did.
    public func getEatsRiderLocation(_ orderId: String) async throws -> EatsRiderLocationResponse {
        try await get("api/v1/eats/orders/\(orderId)/rider-location")
    }

    public func claimDelivery(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/claim", body: EmptyBody())
    }

    public func updateRiderOrderStatus(_ orderId: String, status: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/rider-status", body: UpdateEatsOrderStatusRequest(status: status))
    }

    public func registerRider() async throws -> RiderResponse {
        try await authenticatedPost("api/v1/eats/riders/register", body: EmptyBody())
    }

    public func getMyRiderProfile() async throws -> RiderResponse { try await get("api/v1/eats/riders/me") }

    public func setRiderAvailability(_ available: Bool) async throws -> RiderResponse {
        try await authenticatedPost("api/v1/eats/riders/availability", body: SetRiderAvailabilityRequest(available: available))
    }

    // Real bookmarked/favorited restaurants (2026-07-19) -- see
    // EatsFavoriteService.kt's own doc comment for why add/remove are both idempotent.
    public func addFavoriteRestaurant(_ restaurantId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/eats/restaurants/\(restaurantId)/favorite", body: EmptyBody())
    }

    public func removeFavoriteRestaurant(_ restaurantId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/eats/restaurants/\(restaurantId)/favorite")
    }

    public func getMyFavoriteRestaurants() async throws -> FavoriteRestaurantsResponse { try await get("api/v1/eats/favorites") }

    public func getMyEatsMembership() async throws -> EatsMembershipResponse { try await get("api/v1/eats/membership/me") }

    public func subscribeEatsMembership(days: Int) async throws -> EatsMembershipResponse {
        try await authenticatedPost("api/v1/eats/membership/subscribe", body: SubscribeEatsMembershipRequest(days: days), idempotencyKey: UUID().uuidString)
    }

    public func getMyPlatformMembership() async throws -> PlatformMembershipResponse { try await get("api/v1/eats/platform-membership/me") }

    public func subscribePlatformMembership(days: Int) async throws -> PlatformMembershipResponse {
        try await authenticatedPost("api/v1/eats/platform-membership/subscribe", body: SubscribePlatformMembershipRequest(days: days), idempotencyKey: UUID().uuidString)
    }

    // Real Toss Securities-style stock investing (2026-07-20) -- see StocksResponse's
    // own doc comment for the full account.
    public func getStocks() async throws -> StocksResponse { try await get("api/v1/stocks") }

    public func getStockHistory(stockId: String, days: Int = 14) async throws -> StockHistoryResponse {
        try await get("api/v1/stocks/\(stockId)/history", query: [URLQueryItem(name: "days", value: String(days))])
    }

    public func getStockPortfolio() async throws -> StockPortfolioResponse { try await get("api/v1/stocks/portfolio") }

    public func getPortfolioHistory(days: Int = 30) async throws -> PortfolioHistoryResponse {
        try await get("api/v1/stocks/portfolio/history", query: [URLQueryItem(name: "days", value: String(days))])
    }

    public func buyStock(stockId: String, shares: Double) async throws -> TradeStockResponse {
        try await authenticatedPost("api/v1/stocks/buy", body: TradeStockRequest(stockId: stockId, shares: shares), idempotencyKey: UUID().uuidString)
    }

    public func sellStock(stockId: String, shares: Double) async throws -> TradeStockResponse {
        try await authenticatedPost("api/v1/stocks/sell", body: TradeStockRequest(stockId: stockId, shares: shares), idempotencyKey: UUID().uuidString)
    }

    public func getStockWatchlist() async throws -> StocksResponse { try await get("api/v1/stocks/watchlist") }

    public func watchStock(stockId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/stocks/\(stockId)/watch", body: EmptyBody())
    }

    public func unwatchStock(stockId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/stocks/\(stockId)/watch")
    }

    /// Real DELETE support -- every other authenticated call so far was GET/POST only,
    /// see `authenticatedPost`'s own doc comment for why the Idempotency-Key handling
    /// lives there; DELETE never needs one (removing an already-removed listing is
    /// naturally idempotent at the database level, unlike a real money-moving POST).
    fileprivate func authenticatedDelete<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "DELETE"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }
}

// MARK: - Overview / Linked accounts, Loans, Credit score, Certificate, Identity,
// Support, Split bill, Contacts, group leave/add-member (2026-07-22 port)
//
// Every one of these was found fully built on the backend during a full
// backend-vs-app audit, with zero client UI on Android/bank-mfe either until the
// same-day ports that preceded this one -- see docs/TOSS_PARITY_MATRIX.md's own
// per-row notes for the full account of each gap. DTOs mirror the Android/bank-mfe
// ports field-for-field against the same real backend contracts.

public struct AccountSummaryDto: Decodable, Identifiable { public let id: String; public let type: String; public let name: String; public let balance: Double; public let currency: String }
public struct OverviewSavingsSummaryDto: Decodable { public let totalSaved: Double; public let goalCount: Int }
public struct OverviewLoansSummaryDto: Decodable { public let totalOutstanding: Double; public let activeCount: Int }
public struct OverviewInvestmentsSummaryDto: Decodable { public let totalCostBasis: Double; public let holdingCount: Int }
public struct OverviewInsuranceSummaryDto: Decodable { public let activePolicyCount: Int; public let totalMonthlyPremium: Double }
public struct LinkedAccountSummaryDto: Decodable, Identifiable { public let id: String; public let provider: String; public let maskedAccountNumber: String; public let status: String; public let demoBalance: Double?; public let demoBalanceCurrency: String?; public let isDemoBalance: Bool }
public struct OverviewResponse: Decodable {
    public let success: Bool
    public let netWorth: Double
    public let accounts: [AccountSummaryDto]
    public let savings: OverviewSavingsSummaryDto
    public let loans: OverviewLoansSummaryDto
    public let investments: OverviewInvestmentsSummaryDto
    public let insurance: OverviewInsuranceSummaryDto
    public let linkedAccounts: [LinkedAccountSummaryDto]
}

public struct LinkAccountRequest: Encodable { public let provider: String; public let externalAccountNumber: String }
public struct LinkedAccountDto: Decodable, Identifiable {
    public let id: String; public let userId: String; public let provider: String; public let externalAccountNumberMasked: String
    public let status: String; public let failureReason: String?; public let linkedAt: String; public let unlinkedAt: String?
    public let demoBalance: Double?; public let demoBalanceCurrency: String?
}
public struct LinkAccountResponse: Decodable { public let success: Bool; public let linkedAccount: LinkedAccountDto }
public struct LinkedAccountsResponse: Decodable { public let success: Bool; public let linkedAccounts: [LinkedAccountDto] }

// Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168/177) -- see
// AutoTopUpService's own doc comment. bank-mfe (item 168) and Android (item 176)
// already have this; this is the iOS port. getSetting real-404s
// (AUTO_TOPUP_SETTING_NOT_FOUND) if this wallet has no setting configured yet.
public struct ConfigureAutoTopUpRequest: Encodable {
    public let linkedAccountId: String
    public let thresholdAmount: Double
    public let topUpAmount: Double
    public let dailyTriggerCap: Int
    public let enabled: Bool
}
public struct AutoTopUpSettingDto: Decodable {
    public let id: String
    public let userId: String
    public let walletId: String
    public let linkedAccountId: String
    public let enabled: Bool
    public let thresholdAmount: Double
    public let topUpAmount: Double
    public let dailyTriggerCap: Int
    public let triggersToday: Int
    public let lastTriggerDate: String?
    public let lastTriggeredAt: String?
}
public struct GetAutoTopUpSettingResponse: Decodable { public let success: Bool; public let setting: AutoTopUpSettingDto }
public struct TriggerAutoTopUpResponse: Decodable { public let success: Bool; public let triggered: Bool; public let reason: String? }

public struct LoanOfferDto: Decodable, Identifiable { public let id: String; public let lenderId: String; public let lenderName: String; public let name: String; public let maxAmount: Double; public let interestRate: Double; public let term: String; public let requirements: String }
public struct LenderDto: Decodable, Identifiable { public let id: String; public let name: String; public let kind: String }
public struct LoanOffersResponse: Decodable { public let success: Bool; public let offers: [LoanOfferDto] }
public struct LendersResponse: Decodable { public let success: Bool; public let lenders: [LenderDto] }
public struct LoanAccountDto: Decodable, Identifiable { public let id: String; public let userId: String; public let walletId: String; public let offerId: String; public let principal: Double; public let outstanding: Double; public let interestRate: Double; public let status: String; public let disbursedAt: String }
public struct MyLoansResponse: Decodable { public let success: Bool; public let loans: [LoanAccountDto] }
public struct ApplyLoanRequest: Encodable { public let loanId: String; public let amount: Double }
public struct ApplyLoanResponse: Decodable { public let success: Bool; public let message: String; public let loan: LoanAccountDto }
public struct RepayLoanRequest: Encodable { public let loanId: String; public let amount: Double }
public struct RepayLoanResponse: Decodable { public let success: Bool; public let message: String; public let remaining: Double; public let newBalance: Double }

// Real 대환대출 (loan refinancing) -- see LoansService.refinanceLoan's own doc comment.
// bank-mfe wired 2026-07-26; found unwired on Android/iOS via the same sweep that
// found the lender filter (item 200).
public struct RefinanceLoanRequest: Encodable { public let loanId: String }
public struct RefinanceResult: Decodable {
    public let success: Bool
    public let message: String
    public let oldLoanId: String
    public let oldInterestRate: Double
    public let newLoanId: String
    public let newInterestRate: Double
    public let newLoanName: String
    public let amount: Double
    public let creditScore: Int
}

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// backend OverdraftAccount.kt's own doc comment. Found 2026-07-29 via a full-backend-
// endpoint sweep: real, live-verified backend (open/draw/repay, real daily interest
// accrual, real security-alert push) with zero client anywhere on any of the 3
// platforms.
public struct OverdraftAccountDto: Decodable {
    public let id: String
    public let userId: String
    public let walletId: String
    public let creditLimit: Double
    public let drawnBalance: Double
    public let interestRate: Double
    public let status: String

    // Explicit memberwise init -- Swift doesn't synthesize a public one across module
    // boundaries, needed so App-side code can construct an updated copy after a
    // draw/repay response.
    public init(id: String, userId: String, walletId: String, creditLimit: Double, drawnBalance: Double, interestRate: Double, status: String) {
        self.id = id
        self.userId = userId
        self.walletId = walletId
        self.creditLimit = creditLimit
        self.drawnBalance = drawnBalance
        self.interestRate = interestRate
        self.status = status
    }
}
public struct OverdraftAccountResponse: Decodable { public let success: Bool; public let account: OverdraftAccountDto? }
public struct OpenOverdraftRequest: Encodable { public let requestedLimit: Double }
public struct OverdraftAmountRequest: Encodable { public let amount: Double }
public struct OverdraftDrawResponse: Decodable {
    public let success: Bool
    public let transactionId: String
    public let amount: Double
    public let drawnBalance: Double
    public let availableCredit: Double
}
public struct OverdraftRepayResponse: Decodable {
    public let success: Bool
    public let transactionId: String
    public let amount: Double
    public let drawnBalance: Double
    public let availableCredit: Double
    public let newBalance: Double
}

// Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line) -- see backend
// PostpaidCreditLine.kt's own doc comment. Genuinely distinct from the overdraft above:
// real interest-free on-time repayment, a much lower real qualification bar, and an
// auto-computed (not user-requested) limit.
public struct PostpaidCreditLineDto: Decodable {
    public let id: String
    public let userId: String
    public let walletId: String
    public let creditLimit: Double
    public let currentBalance: Double
    public let status: String
    public let cycleDueAt: String?
    public let lastLateFeeAccrualAt: String?
    public let createdAt: String
    public let updatedAt: String

    // Explicit memberwise init -- same real cross-module-construction gotcha
    // OverdraftAccountDto's own doc comment already names.
    public init(id: String, userId: String, walletId: String, creditLimit: Double, currentBalance: Double, status: String, cycleDueAt: String?, lastLateFeeAccrualAt: String?, createdAt: String, updatedAt: String) {
        self.id = id
        self.userId = userId
        self.walletId = walletId
        self.creditLimit = creditLimit
        self.currentBalance = currentBalance
        self.status = status
        self.cycleDueAt = cycleDueAt
        self.lastLateFeeAccrualAt = lastLateFeeAccrualAt
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}
public struct PostpaidCreditLineResponse: Decodable { public let success: Bool; public let line: PostpaidCreditLineDto? }
public struct PostpaidCreditAmountRequest: Encodable { public let amount: Double }
public struct PostpaidCreditActionResponse: Decodable {
    public let success: Bool
    public let transactionId: String
    public let amount: Double
    public let currentBalance: Double
    public let availableCredit: Double
    public let newBalance: Double?
}

// Real Toss Bank 체크카드 (check/debit card) client (item 207) -- see backend
// DebitCard.kt's own doc comment. bank-mfe/Android shipped first; this is the iOS
// client. "Paying with your card" is itunda's own honest, ledger-backed simulation of
// a card-present purchase -- no real Visa/Mastercard rail exists.
public struct CardDto: Decodable, Identifiable {
    public let id: String
    public let last4: String
    public let dailyLimit: Double
    public let monthlyLimit: Double
    public let frozen: Bool
    public let issuedAt: String
    public let spentToday: Double
    public let spentThisMonth: Double
    public let remainingToday: Double
    public let remainingThisMonth: Double

    // Explicit memberwise init -- Swift doesn't synthesize a public one across module
    // boundaries, same real gotcha OverdraftAccountDto's own doc comment already
    // documents.
    public init(id: String, last4: String, dailyLimit: Double, monthlyLimit: Double, frozen: Bool, issuedAt: String, spentToday: Double, spentThisMonth: Double, remainingToday: Double, remainingThisMonth: Double) {
        self.id = id
        self.last4 = last4
        self.dailyLimit = dailyLimit
        self.monthlyLimit = monthlyLimit
        self.frozen = frozen
        self.issuedAt = issuedAt
        self.spentToday = spentToday
        self.spentThisMonth = spentThisMonth
        self.remainingToday = remainingToday
        self.remainingThisMonth = remainingThisMonth
    }
}
public struct CardResponse: Decodable { public let success: Bool; public let card: CardDto }
public struct CardTransactionDto: Decodable, Identifiable {
    public let id: String
    public let cardId: String
    public let amount: Double
    public let merchantName: String
    public let createdAt: String
}
public struct CardTransactionsResponse: Decodable { public let success: Bool; public let transactions: [CardTransactionDto]; public let totalElements: Int; public let totalPages: Int }
public struct SetCardLimitsRequest: Encodable { public let dailyLimit: Double; public let monthlyLimit: Double }
public struct ChargeCardRequest: Encodable { public let amount: Double; public let merchantName: String }
public struct ChargeCardResponse: Decodable { public let success: Bool; public let transaction: CardTransactionDto; public let card: CardDto }

public struct CreditScoreFactorDto: Decodable, Identifiable { public let name: String; public let points: Int; public let description: String; public var id: String { name } }
public struct CreditScoreResponse: Decodable { public let success: Bool; public let score: Int; public let factors: [CreditScoreFactorDto]; public let computedAt: String }

// Real Karrot-Score-style numeric trust/reputation badge (item 152) -- distinct from
// the per-listing trustScores batch map used for seller/poster/lister badges on Hood
// cards. GET /api/v1/trust-score returns a user's own full factor breakdown,
// mirroring CreditScoreResponse's shape exactly. Found 2026-07-31 real on bank-mfe
// only, zero UI on Android/iOS despite that.
public struct TrustScoreFactorDto: Decodable, Identifiable { public let name: String; public let points: Int; public let description: String; public var id: String { name } }
public struct TrustScoreResponse: Decodable { public let success: Bool; public let score: Int; public let factors: [TrustScoreFactorDto]; public let computedAt: String }

public struct CertificateDto: Decodable {
    public let id: String; public let userId: String; public let serialNumber: String; public let publicKeyBase64: String
    public let algorithm: String; public let status: String; public let issuedAt: String; public let expiresAt: String; public let revokedAt: String?
}
public struct IssueCertificateResponse: Decodable { public let success: Bool; public let certificate: CertificateDto; public let privateKey: String }
public struct MyCertificateResponse: Decodable { public let success: Bool; public let certificate: CertificateDto? }
public struct RevokeCertificateResponse: Decodable { public let success: Bool; public let certificate: CertificateDto }

public struct SubmitIdentityRequest: Encodable { public let documentType: String; public let documentNumber: String; public let documentReference: String }
public struct KycSubmissionDto: Decodable, Identifiable {
    public let id: String; public let userId: String; public let documentType: String; public let documentNumber: String; public let documentReference: String
    public let status: String; public let submittedAt: String; public let reviewedBy: String?; public let reviewedAt: String?; public let decisionReason: String?
    public let autoVerificationStatus: String?; public let autoVerificationDetail: String?
}
public struct SubmitIdentityResponse: Decodable { public let success: Bool; public let submission: KycSubmissionDto }
public struct IdentityStatusResponse: Decodable { public let success: Bool; public let submissions: [KycSubmissionDto] }

public struct CreateSupportTicketRequest: Encodable { public let transactionId: String; public let category: String; public let description: String }
public struct SupportTicketDto: Decodable, Identifiable {
    public let id: String; public let userId: String; public let transactionId: String; public let category: String; public let description: String
    public let status: String; public let resolution: String?; public let resolutionNotes: String?; public let refundTransactionId: String?
    public let frozeWalletId: String?; public let dueBy: String; public let reviewedBy: String?; public let createdAt: String; public let resolvedAt: String?
}
public struct CreateSupportTicketResponse: Decodable { public let success: Bool; public let ticket: SupportTicketDto }
public struct SupportTicketsResponse: Decodable { public let success: Bool; public let tickets: [SupportTicketDto] }

public struct CreateSplitBillRequest: Encodable {
    public let totalAmount: Double; public let description: String; public let participantUserIds: [String]
    // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25) -- see backend
    // SplitBillService.ladderSplit's own doc comment. "EVEN" is the unchanged v1 default.
    public let mode: String
    public let ladderVarianceLevel: Int?
    public init(totalAmount: Double, description: String, participantUserIds: [String], mode: String = "EVEN", ladderVarianceLevel: Int? = nil) {
        self.totalAmount = totalAmount; self.description = description; self.participantUserIds = participantUserIds
        self.mode = mode; self.ladderVarianceLevel = ladderVarianceLevel
    }
}
public struct SplitBillDto: Decodable, Identifiable {
    public let id: String; public let organizerId: String; public let groupConversationId: String; public let messageId: String
    public let totalAmount: Double; public let description: String; public let status: String; public let settledAt: String?; public let createdAt: String
    public let mode: String; public let ladderVarianceLevel: Int?
    // Real photo receipt attach (2026-07-28) -- see backend SplitBillService.attachReceipt's
    // own doc comment. nil means no receipt attached yet.
    public let receiptImageUrl: String?
    // Real up-to-5 sequential settlement round counter (2026-07-28) -- see backend
    // SplitBillService.requestNextRound's own doc comment. Starts at 1.
    public let currentRound: Int
}
public struct SplitBillParticipantDto: Decodable, Identifiable {
    public let id: String; public let splitBillId: String; public let userId: String; public let shareAmount: Double
    public let status: String; public let paidTransactionId: String?; public let paidAt: String?; public let createdAt: String
}
public struct SplitBillWithParticipants: Decodable, Identifiable { public let splitBill: SplitBillDto; public let participants: [SplitBillParticipantDto]; public var id: String { splitBill.id } }
public struct CreateSplitBillResponse: Decodable { public let success: Bool; public let splitBill: SplitBillDto; public let participants: [SplitBillParticipantDto] }
public struct SplitBillsForGroupResponse: Decodable { public let success: Bool; public let splitBills: [SplitBillWithParticipants] }
public struct PaySplitBillShareResponse: Decodable { public let success: Bool; public let participant: SplitBillParticipantDto }
public struct AttachSplitBillReceiptRequest: Encodable { public let imageUrl: String; public init(imageUrl: String) { self.imageUrl = imageUrl } }
// Distinct from SplitBillsForGroupResponse-shaped responses -- attachReceipt/
// requestNextRound's controller responses carry only {success, splitBill}, no participants.
public struct SplitBillOnlyResponse: Decodable { public let success: Bool; public let splitBill: SplitBillDto }

public struct AddContactRequest: Encodable { public let name: String; public let bank: String?; public let phoneNumber: String }
public struct ContactDto: Decodable, Identifiable { public let id: String; public let userId: String; public let name: String; public let bank: String; public let acc: String; public let phoneNumber: String; public let color: String; public let letter: String }
public struct ContactsResponse: Decodable { public let success: Bool; public let contacts: [ContactDto] }
public struct AddContactResponse: Decodable { public let success: Bool; public let contact: ContactDto }

public struct AddGroupMemberRequest: Encodable { public let userId: String }

extension NetworkClient {
    public func getOverview() async throws -> OverviewResponse { try await get("api/v1/overview") }

    public func getLinkedAccounts() async throws -> LinkedAccountsResponse { try await get("api/v1/accounts/linked") }

    public func linkAccount(provider: String, externalAccountNumber: String) async throws -> LinkAccountResponse {
        try await authenticatedPost("api/v1/accounts/link", body: LinkAccountRequest(provider: provider, externalAccountNumber: externalAccountNumber))
    }

    public func unlinkAccount(accountId: String) async throws -> LinkAccountResponse {
        try await authenticatedPost("api/v1/accounts/link/\(accountId)/unlink", body: EmptyRequest())
    }

    public func getAutoTopUpSetting(walletId: String) async throws -> GetAutoTopUpSettingResponse {
        try await get("api/v1/wallet/\(walletId)/auto-topup")
    }

    public func configureAutoTopUp(walletId: String, linkedAccountId: String, thresholdAmount: Double, topUpAmount: Double, dailyTriggerCap: Int = 3, enabled: Bool = true) async throws -> GetAutoTopUpSettingResponse {
        try await authenticatedPut(
            "api/v1/wallet/\(walletId)/auto-topup",
            body: ConfigureAutoTopUpRequest(linkedAccountId: linkedAccountId, thresholdAmount: thresholdAmount, topUpAmount: topUpAmount, dailyTriggerCap: dailyTriggerCap, enabled: enabled)
        )
    }

    public func triggerAutoTopUp(walletId: String) async throws -> TriggerAutoTopUpResponse {
        try await authenticatedPost("api/v1/wallet/\(walletId)/auto-topup/trigger", body: EmptyRequest())
    }

    public func getLoanOffers(lenderId: String? = nil) async throws -> LoanOffersResponse {
        if let lenderId { return try await get("api/v1/loans/offers", query: [URLQueryItem(name: "lenderId", value: lenderId)]) }
        return try await get("api/v1/loans/offers")
    }

    public func getLenders() async throws -> LendersResponse { try await get("api/v1/loans/lenders") }

    public func getMyLoans() async throws -> MyLoansResponse { try await get("api/v1/loans/my-loans") }

    public func applyForLoan(loanId: String, amount: Double) async throws -> ApplyLoanResponse {
        try await authenticatedPost("api/v1/loans/apply", body: ApplyLoanRequest(loanId: loanId, amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func repayLoan(loanId: String, amount: Double) async throws -> RepayLoanResponse {
        try await authenticatedPost("api/v1/loans/repay", body: RepayLoanRequest(loanId: loanId, amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func refinanceLoan(loanId: String) async throws -> RefinanceResult {
        try await authenticatedPost("api/v1/loans/refinance", body: RefinanceLoanRequest(loanId: loanId), idempotencyKey: UUID().uuidString)
    }

    public func getMyOverdraft() async throws -> OverdraftAccountResponse { try await get("api/v1/loans/overdraft") }

    public func openOverdraft(requestedLimit: Double) async throws -> OverdraftAccountResponse {
        try await authenticatedPost("api/v1/loans/overdraft/open", body: OpenOverdraftRequest(requestedLimit: requestedLimit), idempotencyKey: UUID().uuidString)
    }

    public func drawOverdraft(amount: Double) async throws -> OverdraftDrawResponse {
        try await authenticatedPost("api/v1/loans/overdraft/draw", body: OverdraftAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func repayOverdraft(amount: Double) async throws -> OverdraftRepayResponse {
        try await authenticatedPost("api/v1/loans/overdraft/repay", body: OverdraftAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    // Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line, real since
    // 2026-07-31) -- first iOS client for this feature, mirroring bank-mfe's
    // lib/loans.ts and Android's ApiService.kt exactly.
    public func getMyPostpaidCredit() async throws -> PostpaidCreditLineResponse { try await get("api/v1/loans/postpaid-credit") }

    public func applyForPostpaidCredit() async throws -> PostpaidCreditLineResponse {
        try await authenticatedPost("api/v1/loans/postpaid-credit/apply", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func spendPostpaidCredit(amount: Double) async throws -> PostpaidCreditActionResponse {
        try await authenticatedPost("api/v1/loans/postpaid-credit/spend", body: PostpaidCreditAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func repayPostpaidCredit(amount: Double) async throws -> PostpaidCreditActionResponse {
        try await authenticatedPost("api/v1/loans/postpaid-credit/repay", body: PostpaidCreditAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func issueCard() async throws -> CardResponse {
        try await authenticatedPost("api/v1/card/issue", body: EmptyRequest())
    }

    public func getMyCard() async throws -> CardResponse { try await get("api/v1/card/my-card") }

    public func getCardTransactions() async throws -> CardTransactionsResponse { try await get("api/v1/card/transactions") }

    public func setCardLimits(dailyLimit: Double, monthlyLimit: Double) async throws -> CardResponse {
        try await authenticatedPut("api/v1/card/limits", body: SetCardLimitsRequest(dailyLimit: dailyLimit, monthlyLimit: monthlyLimit))
    }

    public func freezeCard() async throws -> CardResponse {
        try await authenticatedPost("api/v1/card/freeze", body: EmptyRequest())
    }

    public func unfreezeCard() async throws -> CardResponse {
        try await authenticatedPost("api/v1/card/unfreeze", body: EmptyRequest())
    }

    public func chargeCard(amount: Double, merchantName: String) async throws -> ChargeCardResponse {
        try await authenticatedPost("api/v1/card/charge", body: ChargeCardRequest(amount: amount, merchantName: merchantName), idempotencyKey: UUID().uuidString)
    }

    public func getCreditScore() async throws -> CreditScoreResponse { try await get("api/v1/credit-score") }
    public func getTrustScore() async throws -> TrustScoreResponse { try await get("api/v1/trust-score") }

    public func issueCertificate() async throws -> IssueCertificateResponse {
        try await authenticatedPost("api/v1/certificate/issue", body: EmptyRequest())
    }

    public func getMyCertificate() async throws -> MyCertificateResponse { try await get("api/v1/certificate/me") }

    public func revokeCertificate() async throws -> RevokeCertificateResponse {
        try await authenticatedPost("api/v1/certificate/revoke", body: EmptyRequest())
    }

    public func submitIdentity(documentType: String, documentNumber: String, documentReference: String) async throws -> SubmitIdentityResponse {
        try await authenticatedPost("api/v1/identity/submit", body: SubmitIdentityRequest(documentType: documentType, documentNumber: documentNumber, documentReference: documentReference))
    }

    public func getIdentityStatus() async throws -> IdentityStatusResponse { try await get("api/v1/identity/status") }

    public func createSupportTicket(transactionId: String, category: String, description: String) async throws -> CreateSupportTicketResponse {
        try await authenticatedPost("api/v1/support/tickets", body: CreateSupportTicketRequest(transactionId: transactionId, category: category, description: description))
    }

    public func getSupportTickets() async throws -> SupportTicketsResponse { try await get("api/v1/support/tickets") }

    public func createSplitBill(groupConversationId: String, totalAmount: Double, description: String, participantUserIds: [String], mode: String = "EVEN", ladderVarianceLevel: Int? = nil) async throws -> CreateSplitBillResponse {
        try await authenticatedPost(
            "api/v1/split-bills/conversations/\(groupConversationId)",
            body: CreateSplitBillRequest(totalAmount: totalAmount, description: description, participantUserIds: participantUserIds, mode: mode, ladderVarianceLevel: ladderVarianceLevel),
            idempotencyKey: UUID().uuidString
        )
    }

    public func getSplitBillsForGroup(groupConversationId: String) async throws -> SplitBillsForGroupResponse {
        try await get("api/v1/split-bills/conversations/\(groupConversationId)")
    }

    public func paySplitBillShare(splitBillId: String) async throws -> PaySplitBillShareResponse {
        try await authenticatedPost("api/v1/split-bills/\(splitBillId)/pay", body: EmptyRequest(), idempotencyKey: UUID().uuidString)
    }

    public func attachSplitBillReceipt(splitBillId: String, imageUrl: String) async throws -> SplitBillOnlyResponse {
        try await authenticatedPost("api/v1/split-bills/\(splitBillId)/receipt", body: AttachSplitBillReceiptRequest(imageUrl: imageUrl))
    }

    public func requestSplitBillNextRound(splitBillId: String) async throws -> SplitBillOnlyResponse {
        try await authenticatedPost("api/v1/split-bills/\(splitBillId)/next-round", body: EmptyRequest())
    }

    public func getContacts() async throws -> ContactsResponse { try await get("api/v1/contacts") }

    public func addContact(name: String, phoneNumber: String, bank: String? = nil) async throws -> AddContactResponse {
        try await authenticatedPost("api/v1/contacts", body: AddContactRequest(name: name, bank: bank, phoneNumber: phoneNumber))
    }

    // Real leave-group/add-member (found 2026-07-22 fully built on the backend with
    // zero UI anywhere, despite group chat itself being fully wired).
    public func addGroupMember(groupId: String, userId: String) async throws -> GroupResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/members", body: AddGroupMemberRequest(userId: userId))
    }

    public func leaveGroup(groupId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/groups/\(groupId)/members/me")
    }

    public func setGroupPhotoUrl(groupId: String, photoUrl: String) async throws -> GroupSummaryResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/photo", body: SetGroupPhotoUrlRequest(photoUrl: photoUrl))
    }

    public func setGroupDescription(groupId: String, description: String) async throws -> GroupSummaryResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/description", body: SetGroupDescriptionRequest(description: description))
    }
}

// Real KakaoBank 26주적금 (26-week savings) equivalent (2026-07-21) -- the first iOS UI
// this feature has ever had; mirrors WeeklySavingsController.kt/WeeklySavingsPlan.kt/
// WeeklySavingsInstallment.kt exactly, same field names, so JSONDecoder reads the real
// backend's JSON directly. Distinct from SavingsGoal/InterestJar above: the weekly
// auto-debit amount escalates on a real schedule, interest accrues per-installment, and
// a streak-gated bonus rate only survives an unbroken run to real 26-week maturity --
// see WeeklySavingsService.kt's own doc comment for the full sourced mechanics. No
// Idempotency-Key on any of these calls -- unlike buyStock/sellStock, this controller
// genuinely doesn't declare that header.
public struct WeeklySavingsPlanDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let walletId: String
    public let name: String
    public let baseWeeklyAmount: Double
    public let escalationRate: Double
    public let openingWeekday: Int
    public let baseRate: Double
    public let bonusRate: Double
    public let installmentsCollected: Int
    public let weeksElapsed: Int
    public let currentAmount: Double
    public let streakBroken: Bool
    public let status: String
    public let nextInstallmentDueAt: String
    public let createdAt: String
    public let maturedAt: String?
    public let cancelledAt: String?
    public let withdrawnAt: String?
    public let totalInterestPaid: Double?
}

public struct WeeklySavingsInstallmentDto: Decodable, Identifiable {
    public let id: String
    public let planId: String
    public let weekNumber: Int
    public let amount: Double
    public let depositedAt: String
}

public struct WeeklySavingsPlansResponse: Decodable { public let success: Bool; public let plans: [WeeklySavingsPlanDto] }

public struct WeeklySavingsPlanDetailResponse: Decodable {
    public let success: Bool
    public let plan: WeeklySavingsPlanDto
    public let walletBalance: Double
    public let installments: [WeeklySavingsInstallmentDto]
}

public struct CreateWeeklySavingsPlanRequest: Encodable {
    public let name: String
    public let baseWeeklyAmount: Double
    public let escalationRate: Double
}

public struct WeeklySavingsActionResponse: Decodable {
    public let success: Bool
    public let message: String
    public let plan: WeeklySavingsPlanDto
    public let walletBalance: Double
    public let installments: [WeeklySavingsInstallmentDto]
}

// Real display-only constants (2026-07-21) -- mirror WeeklySavingsService's own
// TERM_WEEKS/ESCALATION_STEP_WEEKS exactly (there's no endpoint for these; the backend
// is still the actual source of truth for every real number returned per-plan).
public enum WeeklySavingsConstants {
    public static let termWeeks = 26
    public static let escalationStepWeeks = 4
    // Real KakaoBank step-up presets -- must match
    // WeeklySavingsService.allowedEscalationRates exactly, or plan creation real-400s
    // with INVALID_ESCALATION_RATE.
    public static let escalationRates: [Double] = [0.00, 0.10, 0.20, 0.30, 0.50, 1.00]
}

extension NetworkClient {
    public func getWeeklySavingsPlans() async throws -> WeeklySavingsPlansResponse { try await get("api/v1/weekly-savings/plans") }

    public func getWeeklySavingsPlan(id: String) async throws -> WeeklySavingsPlanDetailResponse {
        try await get("api/v1/weekly-savings/plans/\(id)")
    }

    public func createWeeklySavingsPlan(name: String, baseWeeklyAmount: Double, escalationRate: Double) async throws -> WeeklySavingsActionResponse {
        try await authenticatedPost(
            "api/v1/weekly-savings/plans",
            body: CreateWeeklySavingsPlanRequest(name: name, baseWeeklyAmount: baseWeeklyAmount, escalationRate: escalationRate)
        )
    }

    public func cancelWeeklySavingsPlan(id: String) async throws -> WeeklySavingsActionResponse {
        try await authenticatedPost("api/v1/weekly-savings/plans/\(id)/cancel", body: EmptyBody())
    }

    public func withdrawWeeklySavingsPlan(id: String) async throws -> WeeklySavingsActionResponse {
        try await authenticatedPost("api/v1/weekly-savings/plans/\(id)/withdraw", body: EmptyBody())
    }
}
