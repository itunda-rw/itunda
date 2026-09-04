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
    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
    // DeviceKeyManager.exportPublicKeyIfPresent's own doc comment. The SAME real
    // Secure-Enclave-backed key item 246 already established, just published at
    // register time too (not only via the separate opt-in device-verification flow)
    // so a brand new device can go straight to biometric-only login next time -- see
    // AuthService.register's own doc comment on the backend for
    // DeviceService.registerKeyDuringAuth.
    public let devicePublicKey: String?
    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) enforcement, added to
    // the backend/bank-mfe 2026-08-18 but never ported to this struct -- found
    // 2026-08-30 during a market-readiness audit: AuthService.register real-400s
    // (RequiredTermsNotAcceptedException) whenever the required terms ids are
    // missing, and this field's absence meant every native registration silently
    // sent an empty list, so registration on this platform has been completely
    // broken since that date. See TermsCatalog.kt's own doc comment for the full
    // sourced account.
    public let acceptedTermsIds: [String]
    public init(phoneNumber: String, email: String?, firstName: String, lastName: String, password: String, referralCode: String?, deviceId: String?, deviceName: String?, devicePublicKey: String? = nil, acceptedTermsIds: [String] = []) { self.phoneNumber = phoneNumber; self.email = email; self.firstName = firstName; self.lastName = lastName; self.password = password; self.referralCode = referralCode; self.deviceId = deviceId; self.deviceName = deviceName; self.devicePublicKey = devicePublicKey; self.acceptedTermsIds = acceptedTermsIds }
}

// Mirrors services/backend/core/.../TermsDocument.kt exactly -- see RegisterRequest
// .acceptedTermsIds' own doc comment for why this exists on this platform now.
public struct TermsDocument: Decodable, Identifiable {
    public let id: String
    public let title: String
    public let version: String
    public let required: Bool
    public let summary: String
}
private struct TermsResponse: Decodable {
    let success: Bool
    let terms: [TermsDocument]
}

// Mirrors services/backend/core/.../LegalDocumentCatalog.kt exactly -- real
// itunda-branded Terms of Service/Privacy Policy/Credit Data Policy full text,
// closing SettingsScreen.swift's own missing "Legal Documents" section.
public struct LegalDocument: Decodable, Identifiable {
    public let id: String
    public let title: String
    public let version: String
    public let bodyMarkdown: String
}
private struct LegalDocumentsResponse: Decodable {
    let success: Bool
    let documents: [LegalDocument]
}

// deviceId/deviceName added 2026-07-21 -- mirrors bank-mfe's real device-binding
// login call exactly. See DeviceStore.swift for how these are generated.
// devicePublicKey added 2026-08-23 -- mirrors RegisterRequest's own field exactly,
// same real reasoning (see its own doc comment).
public struct LoginRequest: Encodable {
    public let phoneNumber: String
    public let password: String
    public let deviceId: String?
    public let deviceName: String?
    public let devicePublicKey: String?
    public init(phoneNumber: String, password: String, deviceId: String?, deviceName: String?, devicePublicKey: String? = nil) { self.phoneNumber = phoneNumber; self.password = password; self.deviceId = deviceId; self.deviceName = deviceName; self.devicePublicKey = devicePublicKey }
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
    // Real second neighborhood (2026-07-30) -- see AuthService.setSecondNeighborhood's own
    // doc comment. Same real-coordinate, reverse-geocoded-server-side rule as `neighborhood`.
    public let secondNeighborhood: String?
    // Real age-eligibility gate for the Youth account (2026-07-28) -- see
    // YouthAccountService.kt's own doc comment. Set via NetworkClient.setBirthDate.
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
    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see backend
    // User.pinSet's own doc comment. false only for a pre-PIN-era account whose
    // existing password hasn't been upgraded to a real 6-digit PIN yet -- gates
    // PinUpgradeCard, never blocks the existing password login either way. Optional
    // (not defaulted to true like the backend's own field) since PublicUser relies on
    // synthesized Decodable conformance, matching this struct's existing
    // emailVerified/phoneVerified fields' own nil-safe pattern -- nil is treated the
    // same as true (no prompt) at every call site.
    public let pinSet: Bool?
}

public struct UpdateProfilePhotoRequest: Encodable { public let profilePhotoUrl: String }

public struct ConfirmEmailVerificationRequest: Encodable { public let token: String }
public struct ConfirmPhoneVerificationRequest: Encodable { public let code: String }

public struct SetNeighborhoodRequest: Encodable {
    public let latitude: Double
    public let longitude: Double
}

public struct SetSecondNeighborhoodRequest: Encodable {
    public let latitude: Double
    public let longitude: Double
}

public struct SetBirthDateRequest: Encodable {
    public let birthDate: String
}

// Real KakaoBank mini-style capped starter account -- see YouthAccountService.kt's own
// doc comment (real balance/daily/monthly caps plus a real 7-18 age-eligibility gate).
public struct OpenYouthAccountResponse: Decodable { public let success: Bool; public let account: Account }
public struct DepositYouthAccountRequest: Encodable { public let amount: Double }
public struct DepositYouthAccountResponse: Decodable { public let success: Bool; public let id: String; public let amount: Double; public let completedAt: String }

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- mirrors
// GroupAccount.kt/GroupAccountService.kt exactly.
public struct GroupAccountDto: Decodable {
    public let id: String
    public let name: String
    public let ownerId: String
    public let accountId: String
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

// Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See the
// backend's Ikimina.kt doc comment for the full sourced account. Distinct from
// GroupAccountDto above (Kakao Bank 모임통장): that feature has one permanent owner
// with sole withdrawal authority; an ikimina rotates the full pot to a different
// member each real round, until everyone has been paid exactly once. Genuinely the
// first feature in this codebase not sourced from Toss/Kakao/Naver/Coupang. Mirrors
// bank-mfe's lib/ikimina.ts exactly.
public struct IkiminaDto: Decodable {
    public let id: String
    public let name: String
    public let organizerId: String
    public let accountId: String
    public let contributionAmount: Double
    public let cycleFrequencyDays: Int
    public let memberCap: Int
    public let currentRound: Int
    public let status: String
    public let createdAt: String
}
public struct IkiminaMemberDto: Decodable {
    public let userId: String
    public let firstName: String
    public let lastName: String
    public let payoutOrder: Int
    public let hasReceivedPayout: Bool
    public let isOrganizer: Bool
}
public struct IkiminaContributionStatusDto: Decodable { public let userId: String; public let contributed: Bool }
public struct CreateIkiminaRequest: Encodable { public let name: String; public let contributionAmount: Double; public let cycleFrequencyDays: Int; public let memberCap: Int }
public struct CreateIkiminaResponse: Decodable { public let success: Bool; public let ikimina: IkiminaDto }
public struct IkiminasResponse: Decodable { public let success: Bool; public let ikiminas: [IkiminaDto] }
public struct IkiminaDetailResponse: Decodable {
    public let success: Bool
    public let ikimina: IkiminaDto
    public let balance: Double
    public let members: [IkiminaMemberDto]
    public let currentRoundContributions: [IkiminaContributionStatusDto]
}
public struct InviteIkiminaMemberRequest: Encodable { public let phoneNumber: String }
public struct InviteIkiminaMemberResponse: Decodable { public let success: Bool; public let member: IkiminaMemberDto }
public struct IkiminaPayoutResponse: Decodable { public let success: Bool; public let ikimina: IkiminaDto; public let recipientUserId: String; public let amount: Double }

// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model (416 real sector SACCOs, 4M+ members, RWF 200B+ deposits
// as of 2024). Distinct from IkiminaDto (informal rotating-pot ROSCA, no shares/
// dividends): a SACCO member buys real shares and receives periodic real dividend
// distributions tied to the pool's real performance. Mirrors bank-mfe's lib/sacco.ts
// exactly.
public struct SaccoShareholdingDto: Decodable {
    public let id: String
    public let userId: String
    public let accountId: String
    public let sharesHeld: Double
    public let totalContributed: Double
    public let createdAt: String
}
public struct SaccoAmountRequest: Encodable { public let amount: Double }
public struct SaccoShareholdingResponse: Decodable { public let success: Bool; public let shareholding: SaccoShareholdingDto?; public let currentValue: Double? }
public struct SaccoDividendPayoutDto: Decodable {
    public let id: String
    public let distributionId: String
    public let shareholdingId: String
    public let amount: Double
    public let payoutTransactionId: String
    public let createdAt: String
}
public struct SaccoDividendPayoutsResponse: Decodable { public let success: Bool; public let payouts: [SaccoDividendPayoutDto] }

// Real Rwanda coffee-cooperative harvest-advance / input financing -- sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems, grounded in
// Rwanda's own real coffee sector (Rwanda Coffee Cooperatives Federation: 13 member
// cooperatives, ~19,000 producer members). A direct itunda-to-farmer lending
// relationship mirroring the regular Loans feature's own loan_payable receivable
// shape -- never a shared/pooled account. Mirrors bank-mfe's lib/harvestAdvance.ts
// exactly, including the post-fix repay contract (amount must equal the full real
// outstanding principal, no partial repayment).
public struct CooperativeDto: Decodable {
    public let id: String
    public let name: String
    public let cropType: String
    public let registrationNumber: String?
    public let createdAt: String
}
public struct CooperativeMembershipDto: Decodable {
    public let id: String
    public let cooperativeId: String
    public let userId: String
    public let accountId: String
    public let memberSince: String
    public let active: Bool
}
public struct HarvestAdvanceDto: Decodable, Identifiable {
    public let id: String
    public let membershipId: String
    public let accountId: String
    public let principalAmount: Double
    public let purpose: String
    public let expectedHarvestDate: String
    public let repaymentDueDate: String
    public let status: String
    public let disbursedAt: String?
    public let repaidAt: String?
    public let createdAt: String
}
public struct RegisterCooperativeRequest: Encodable { public let name: String; public let cropType: String; public let registrationNumber: String? }
public struct RequestAdvanceRequest: Encodable {
    public let membershipId: String
    public let principalAmount: Double
    public let purpose: String
    public let expectedHarvestDate: String
}
public struct RepayAdvanceRequest: Encodable { public let amount: Double }
public struct CooperativeResponse: Decodable { public let success: Bool; public let cooperative: CooperativeDto }
public struct CooperativeMembershipResponse: Decodable { public let success: Bool; public let membership: CooperativeMembershipDto }
public struct CooperativeMembershipsResponse: Decodable { public let success: Bool; public let memberships: [CooperativeMembershipDto] }
public struct HarvestAdvanceResponse: Decodable { public let success: Bool; public let advance: HarvestAdvanceDto }
public struct HarvestAdvancesResponse: Decodable { public let success: Bool; public let advances: [HarvestAdvanceDto] }

// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
// microloan -- sourced beyond this session's usual Toss/Kakao/Naver/Coupang reference
// ecosystems. VUP, run by LODA since 2008, subsidizes microloans for income-generating
// activities (farming, livestock, small business) targeted at households in poorer
// Ubudehe categories (NISR EICV7 2023/24: ~100,000 RWF average loan). Since a real
// 2014-07-29 Cabinet decision, administration moved to Umurenge SACCOs, which set the
// rate at 11% (Rwanda Inspirer: uptake fell after that rate hike). Honest v1
// limitation: declaredUbudeheCategory is self-declared by the user, not verified
// against Rwanda's real government Ubudehe household-classification registry. Mirrors
// bank-mfe's lib/vupLoan.ts exactly.
public struct VupLoanDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let declaredUbudeheCategory: Int
    public let purpose: String
    public let principalAmount: Double
    public let outstandingPrincipal: Double
    public let interestRate: Double
    public let status: String
    public let appliedAt: String
    public let disbursedAt: String?
    public let dueDate: String?
}
public struct ApplyForVupLoanRequest: Encodable { public let declaredUbudeheCategory: Int; public let purpose: String; public let amount: Double }
public struct RepayVupLoanRequest: Encodable { public let amount: Double }
public struct VupLoanResponse: Decodable { public let success: Bool; public let loan: VupLoanDto }
public struct VupLoansResponse: Decodable { public let success: Bool; public let loans: [VupLoanDto] }
// Real Rwanda moto-taxi ownership savings-to-loan plan -- sourced beyond this
// session's usual Toss/Kakao/Naver/Coupang reference ecosystems. A real ~600,000 RWF
// entry-level moto-taxi bike is a documented purchase price (Anadolu Agency, 14 May
// 2021 -- profiles a rider who saved for years to buy her own bike after paying daily
// rent to a bike owner). Rent-to-own is a proven-relevant mechanic in this exact
// sector (Frontier Tech Hub's Kigali e-moto pilot: Ampersand's rent-to-own model
// increased driver revenue 78%/month; WeeTracker/WEF coverage of the same). This
// fills the gap left by Rwanda's dissolved taxi-moto cooperatives (Africa-Press,
// 2026). Honest v1 limitation: once converted to a loan, this is an UNSECURED
// facility -- itunda has no path to a real chattel lien or RURA vehicle-registry
// hold, so it cannot repossess the bike or verify it was actually purchased. Mirrors
// bank-mfe's lib/motoOwnership.ts exactly.
public struct MotoOwnershipPlanDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let bikePrice: Double
    public let downPaymentTarget: Double
    public let savedAmount: Double
    public let dailyContribution: Double
    public let loanOutstanding: Double
    public let status: String
    public let lastAutoContributionAt: String?
    public let createdAt: String
}
public struct CreateMotoOwnershipPlanRequest: Encodable { public let bikePrice: Double; public let dailyContribution: Double }
public struct ContributeToMotoOwnershipPlanRequest: Encodable { public let amount: Double }
public struct RepayMotoOwnershipPlanRequest: Encodable { public let amount: Double }
public struct MotoOwnershipPlanResponse: Decodable { public let success: Bool; public let plan: MotoOwnershipPlanDto }
public struct MotoOwnershipPlansResponse: Decodable { public let success: Bool; public let plans: [MotoOwnershipPlanDto] }

public struct VupLoanEligibilityResponse: Decodable {
    public let success: Bool
    public let hasActiveLoan: Bool
    public let canApply: Bool
    public let minUbudeheCategory: Int
    public let maxUbudeheCategory: Int
    public let interestRate: Double
    public let maxAmount: Double
}

// Real gap found live (2026-08-31, market-readiness audit) -- itunda Bank iOS had zero
// client for the real BRD (Development Bank of Rwanda) higher-education student loan
// (backend + bank-mfe + Android all shipped 2026-08-04, iOS never got one). See
// StudentLoanService.kt's own doc comment for the full sourced account. Mirrors
// bank-mfe's lib/studentLoan.ts exactly. Honest v1 limitation: declaredAnnualHouseholdIncome
// is self-declared, not verified against BRD's real Financial Means Testing (FMT)
// process, and the real 8%-of-income payroll deduction is only ever a SUGGESTED
// amount here -- itunda has no payroll/RRA-integration path to enforce it.
public struct StudentLoanDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let level: String
    public let declaredAnnualHouseholdIncome: Double
    public let principalAmount: Double
    public let outstandingBalance: Double
    public let interestRate: Double
    public let status: String
    public let appliedAt: String
    public let disbursedAt: String?
    public let expectedGraduationDate: String
    public let graceEndsAt: String?
}
public struct ApplyForStudentLoanRequest: Encodable { public let level: String; public let declaredAnnualHouseholdIncome: Double; public let amount: Double; public let expectedGraduationDate: String }
public struct RepayStudentLoanRequest: Encodable { public let amount: Double }
public struct StudentLoanResponse: Decodable { public let success: Bool; public let loan: StudentLoanDto }
public struct StudentLoansResponse: Decodable { public let success: Bool; public let loans: [StudentLoanDto] }
public struct StudentLoanSuggestedPaymentResponse: Decodable {
    public let success: Bool
    public let loanId: String
    public let outstandingBalance: Double
    public let suggestedMonthlyPayment: Double
    public let note: String
}

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
    // Real age-eligibility gate for the Youth account (2026-07-28) -- purely additive,
    // same rationale as deviceNotVerified above: thrown only from the Youth account's
    // own dedicated request methods, which decode the real ApiError.code on a 422.
    case youthAccountBirthDateRequired
    case youthAccountAgeIneligible
    // Real cash-agent operator gate (AgentOperatorController's own
    // AGENT_OPERATOR_NOT_AUTHORIZED, 403) -- purely additive, same rationale as
    // deviceNotVerified/youthAccount* above. Thrown only from getAgentTill's own
    // dedicated request method below, which decodes the real ApiError.code on a 403.
    case agentOperatorNotAuthorized
    // Real gap found 2026-08-08 (Toss Simplicity21 "adding innovation upon innovation"
    // research pass, auditing whether P2P transfer's mature/assumed-solid error handling
    // actually was): self-payment/account-frozen/family-spend-limit/rate-limit declines
    // all fell through to httpError's bare status code, so TransferViewModel showed a
    // generic "Something went wrong" for all of them even though the backend already
    // sends specific text per decline reason -- bank-mfe's ApiError already surfaced
    // that real text (Android had the identical gap, fixed same day via
    // apiErrorMessage). Purely additive, same rationale as the cases above; thrown only
    // from postP2p below (sendDirect/payP2pRequest), not from the shared
    // authenticatedPost every other endpoint uses -- widening httpError itself would be
    // exactly the "broader networking-layer change" TalkScreen.swift's own errorMessage
    // doc comment already named and deliberately deferred.
    case httpErrorWithMessage(statusCode: Int, message: String?)
    // Real gap found 2026-08-30 (Toss-style error-handling sweep): several endpoints
    // can return the SAME HTTP status for two or more distinct, unrelated backend
    // exceptions (e.g. loan-repay's LOAN_ALREADY_PAID vs IDEMPOTENCY_KEY_CONFLICT,
    // split-bill-pay's SPLIT_BILL_SHARE_ALREADY_PAID vs the same, ride-trusted-contact's
    // TRUSTED_CONTACT_ALREADY_ADDED vs TOO_MANY_TRUSTED_CONTACTS) -- a bare statusCode
    // check would misreport one as the other, and neither httpError nor
    // httpErrorWithMessage carries the real ApiError.code needed to branch correctly.
    // Purely additive, same rationale as every case above; thrown only from
    // authenticatedPostWithCode below, not from the shared authenticatedPost every
    // other endpoint still uses.
    case httpErrorWithCode(statusCode: Int, code: String?, message: String?)
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
    let baseURL = URL(string: NetworkClient.baseURLString)!
    private let session = URLSession(configuration: .default)

    private lazy var encoder: JSONEncoder = JSONEncoder()
    lazy var decoder: JSONDecoder = JSONDecoder()

    private init() {}

    // Real silent session-refresh (2026-08-15) -- Android's twin `refreshAuthenticator`
    // in ApiService.kt has the full account: a dead-endpoint sweep found `refresh()`
    // had ZERO callers anywhere on ANY platform, meaning an expired 24h access token
    // (JwtService's real expiry) just surfaced as a raw, unrecoverable 401 with no
    // path forward -- iOS's gap was actually the plainer of the two, since it didn't
    // even have Android's pre-existing "hard logout on any error" fallback. An `actor`
    // here (not a plain stored `Task?`) is load-bearing: NetworkClient.shared is hit
    // concurrently from many screens' own Tasks, and a data race between two 401s each
    // independently deciding "no refresh in flight yet" would burn the single-use
    // rotating refresh token twice, permanently locking the second caller out.
    private actor RefreshCoordinator {
        private var inFlight: Task<String?, Never>?

        func refreshedToken(_ refresh: @escaping () async -> String?) async -> String? {
            if let inFlight { return await inFlight.value }
            let task = Task<String?, Never> { await refresh() }
            inFlight = task
            let result = await task.value
            inFlight = nil
            return result
        }
    }
    private let refreshCoordinator = RefreshCoordinator()

    /// Every raw `session.data(for:)` call site in this file should route through
    /// here instead -- on a real 401 from an authenticated request, refreshes once
    /// (single-flight, see RefreshCoordinator above) and retries the exact same
    /// request with the new token. `/auth/refresh` itself is excluded from the retry
    /// to avoid a hard loop if the refresh call somehow 401s.
    func dataWithRefresh(for request: URLRequest) async throws -> (Data, URLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 401,
              request.value(forHTTPHeaderField: "Authorization") != nil,
              request.url?.path.hasSuffix("/auth/refresh") != true
        else {
            return (data, response)
        }
        guard let newToken = await refreshCoordinator.refreshedToken({ [weak self] in
            await self?.performTokenRefresh()
        }) else {
            return (data, response)
        }
        var retried = request
        retried.setValue("Bearer \(newToken)", forHTTPHeaderField: "Authorization")
        return try await session.data(for: retried)
    }

    private func performTokenRefresh() async -> String? {
        guard let refreshToken = KeychainTokenStore.shared.getRefreshToken() else { return nil }
        do {
            let response: AuthResponse = try await post(
                "api/v1/auth/refresh", body: RefreshRequest(refreshToken: refreshToken), authToken: nil
            )
            KeychainTokenStore.shared.saveSession(
                userId: response.user.id, accessToken: response.accessToken, refreshToken: response.refreshToken
            )
            return response.accessToken
        } catch {
            // Refresh token itself is invalid/expired (7-day expiry, or already
            // consumed) -- a real logout, not a dead end: clear the local session and
            // notify (CoreNetwork can't import App -> SessionManager directly, one-way
            // dependency, see Project.swift's own comment on this exact boundary) so
            // App's SessionManager can take the session back to a real login screen
            // instead of every remaining screen showing raw 401s forever. Matches
            // Android's forceLocalLogout exactly, just decoupled across the module
            // boundary Android doesn't have (SessionManager.kt lives IN :core:network).
            KeychainTokenStore.shared.clearSession()
            NotificationCenter.default.post(name: NetworkClient.sessionExpiredNotification, object: nil)
            return nil
        }
    }

    /// Posted when a refresh token itself is invalid/expired and the local session had
    /// to be cleared -- App's SessionManager observes this to flip `sessionState` back
    /// to `.loggedOut` (see SessionManager.swift's own observer, added alongside this).
    public static let sessionExpiredNotification = Notification.Name("rw.itunda.sessionExpired")

    public func register(_ request: RegisterRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/register", body: request, authToken: nil)
    }

    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) catalog -- see
    // RegisterRequest.acceptedTermsIds' own doc comment for why this is only being
    // added now. Public (SecurityConfig permitAll), called before registration.
    public func getTerms() async throws -> [TermsDocument] {
        let response: TermsResponse = try await get("api/v1/auth/terms")
        return response.terms
    }

    // Real itunda-branded legal document bodies -- see LegalDocument's own doc
    // comment. Public (SecurityConfig permitAll), reference material, not a
    // registration consent gate.
    public func getLegalDocuments() async throws -> [LegalDocument] {
        let response: LegalDocumentsResponse = try await get("api/v1/auth/legal-documents")
        return response.documents
    }

    public func login(_ request: LoginRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login", body: request, authToken: nil)
    }

    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see this session's
    // real, sourced research on Toss's own actual mechanism (support.toss.im/
    // toss.im/tosscert): registration is phone + OTP, then a real 6-digit "비밀번호"
    // (Toss's own literal term -- not zero credential), with biometric as day-to-day
    // login's real fast path and the PIN as its standing fallback -- exactly what
    // AuthController.kt's own doc comment on the backend implements. Unauthenticated
    // (permitAll, see SecurityConfig.kt) since this IS the initial login itself, not a
    // step-up re-verification of an already-authenticated session like
    // issueDeviceChallenge/verifyDeviceSignature below -- resolves the user from
    // phoneNumber, not a JWT claim, so this uses the plain `post` helper (no auth
    // token) rather than `authenticatedPost`.
    public func loginDeviceChallenge(_ request: LoginDeviceChallengeRequest) async throws -> LoginDeviceChallengeResponse {
        try await post("api/v1/auth/login/device/challenge", body: request, authToken: nil)
    }

    public func loginWithDeviceSignature(_ request: LoginWithDeviceSignatureRequest) async throws -> AuthResponse {
        try await post("api/v1/auth/login/device/verify", body: request, authToken: nil)
    }

    // Real PIN upgrade (2026-08-23) -- see AuthService.setPin's own doc comment on the
    // backend. currentCredential re-proves ownership of the EXISTING password/PIN
    // (whatever shape it currently is) before it's replaced -- same real cost as
    // registerDeviceKey's own password re-entry above, never trusting a client-only
    // check for a credential change. Authenticated (needs the existing session's JWT),
    // unlike the two calls above.
    // Real, specific backend message (wrong current credential vs. an invalid new PIN
    // shape) matters here, so this uses its own dedicated PUT path rather than the
    // generic authenticatedPut -- same "dedicated function scoped to the one flow that
    // needs it" convention postP2p's own doc comment already established, not
    // widening authenticatedPut for every other caller.
    public func setAccountPin(currentCredential: String, newPin: String) async throws -> SetAccountPinResponse {
        try await authenticatedPutWithMessage("api/v1/auth/pin", body: SetAccountPinRequest(currentCredential: currentCredential, newPin: newPin))
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

        let (data, response) = try await dataWithRefresh(for: urlRequest)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return data
    }
}

// Mirrors services/backend/core/.../domain/Account.kt / SavingsGoal.kt / InterestJar.kt
// exactly -- same field names, so JSONDecoder reads the real backend's JSON directly
// (2026-07-11, alongside BankView.swift's real-data wiring; same DTOs Android's
// ApiService.kt just gained).
public struct Account: Decodable {
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

public struct AccountsResponse: Decodable { public let success: Bool; public let accounts: [Account] }

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (item 160) -- see the
// backend's ForeignCurrencyAccountService.kt doc comment: scoped to USD/EUR/GBP, real
// live mid-market rate + a real 1.5% itunda margin, real double-entry conversion
// entirely between a user's own RWF and foreign-currency accounts. Reuses `Account`
// above for the foreign-currency account itself (same real domain shape, `type` ==
// "FOREIGN_CURRENCY"). Android's main app already has this (`ForeignCurrencyScreen.kt`);
// this is the iOS port -- bank-mfe got it in item 154.
public struct ForeignAccountsResponse: Decodable { public let success: Bool; public let accounts: [Account] }
public struct ExchangeRateResponse: Decodable { public let success: Bool; public let from: String; public let to: String; public let rate: Double }
public struct OpenForeignAccountRequest: Encodable {
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

// Real Toss 외환 환율 알림 (exchange rate alert, section 121/168) -- see the backend's
// ExchangeRateAlert.kt doc comment. Shipped backend-only with a live-verified-safe
// scheduler (ExchangeRateAlertScheduler) but zero client caller anywhere, found via a
// fresh uncalled-endpoint sweep -- same pattern as section 113/167's stock
// target-price alert.
public struct SetRateAlertRequest: Encodable {
    public let fromCurrency: String; public let toCurrency: String; public let targetRate: Double; public let direction: String
    public init(fromCurrency: String, toCurrency: String, targetRate: Double, direction: String) {
        self.fromCurrency = fromCurrency; self.toCurrency = toCurrency; self.targetRate = targetRate; self.direction = direction
    }
}
public struct ExchangeRateAlertDto: Decodable, Identifiable {
    public let id: String
    public let fromCurrency: String
    public let toCurrency: String
    public let targetRate: Double
    public let direction: String
    public let alertTriggeredAt: String?
}
public struct SetRateAlertResponse: Decodable { public let success: Bool; public let alert: ExchangeRateAlertDto }
public struct RateAlertsResponse: Decodable { public let success: Bool; public let alerts: [ExchangeRateAlertDto] }

// Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) equivalent
// (item 161) -- see UpfrontInterestDepositService.kt's own doc comment: the full year's
// 2.80% interest is paid immediately on opening, principal locks in its own dedicated
// account for a genuine 12-month term with deliberately no early withdrawal. Android's
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

// Real Kakao Pay 페이아이 소비 리포트 (AI spending report) -- see
// AccountService.getMonthlySpendingReport's own doc comment on the backend
// ([[project_itunda_monthly_spending_report]], real+live-verified since 2026-08-16).
// Real on bank-mfe/Android the same day it shipped; iOS never got a client at all
// despite SpendingScreenView.swift already existing for the neighboring all-time
// getSpendingInsight() endpoint (found via a fresh iOS platform-parity sweep,
// 2026-08-16). `percentChange` is nullable -- null (not a fabricated 0%) means a
// category genuinely has no prior-month spend to compare against.
public struct SpendingComparisonCategoryDto: Decodable, Identifiable {
    public let name: String
    public let currentAmount: Double
    public let previousAmount: Double
    public let percentChange: Int?
    public var id: String { name }
}
public struct MonthlySpendingReportResponse: Decodable {
    public let success: Bool
    public let currentTotal: Double
    public let previousTotal: Double
    public let percentChange: Int?
    public let categories: [SpendingComparisonCategoryDto]
}

// Real Toss budgets/limits equivalent (item 165/173) -- AccountService.setBudget/
// getBudgets, exposed on the pre-existing AccountController. bank-mfe (item 165) and
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
    public let accountId: String
    public let status: String
    public let available: Bool
    public let currentLatitude: Double?
    public let currentLongitude: Double?
    public let locationUpdatedAt: String?
    // Real Uber "Destination Filter" (item 247) -- see NetworkClient's own
    // setRideDriverDestination doc comment.
    public let destinationLatitude: Double?
    public let destinationLongitude: Double?
    // Real gap found live (2026-08-31, market-readiness audit) -- see
    // RegisterRideDriverRequest's own doc comment.
    public let licenseNumber: String?
}
public struct RideDriverResponse: Decodable { public let success: Bool; public let driver: RideDriverDto }
// Real gap found live (2026-08-31, market-readiness audit): this, the biggest and most
// central driver-role registration in the backend, had zero identity/license info at
// all -- see backend RideDriverService.kt's own doc comment for the full account. An
// honest, self-declared informational text field, not a real license-verification gate
// this backend has no path to check.
public struct RegisterRideDriverRequest: Encodable { public let licenseNumber: String; public init(licenseNumber: String) { self.licenseNumber = licenseNumber } }
public struct SetRideDriverAvailabilityRequest: Encodable { public let available: Bool }
public struct UpdateRideDriverLocationRequest: Encodable { public let latitude: Double; public let longitude: Double }
public struct SetRideDriverDestinationRequest: Encodable { public let latitude: Double; public let longitude: Double }
public struct RideDailyEarnings: Decodable {
    public let date: String
    public let tripCount: Int
    public let grossFare: Double
    public let platformFees: Double
    public let netEarnings: Double
}
public struct RideEarningsResponse: Decodable { public let success: Bool; public let from: String; public let to: String; public let days: [RideDailyEarnings] }
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
    // Real Uber post-trip tipping -- see RideTripService.tipDriver's own doc comment.
    // Ported from bank-mfe/Android (2026-09-03). Non-nil once tipped.
    public let tipAmount: Double?
}
public struct RideTripResponse: Decodable { public let success: Bool; public let trip: RideTripDto }
public struct TipRideTripRequest: Encodable { public let amount: Double; public init(amount: Double) { self.amount = amount } }
public struct RideTripsResponse: Decodable { public let success: Bool; public let trips: [RideTripDto] }
public struct StartRideTripRequest: Encodable { public let pin: String }
public struct RideTripPinResponse: Decodable { public let success: Bool; public let pin: String }
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
public struct RideDriverReviewsResponse: Decodable { public let success: Bool; public let reviews: [RideTripReviewDto] }

// Real Uber Safety "Trusted Contacts" -- mirrors the backend's RideTrustedContact.kt
// entity/Android's RideTrustedContactDto exactly.
public struct RideTrustedContactDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let contactUserId: String
    public let contactName: String
    public let createdAt: String
}
public struct RideTrustedContactResponse: Decodable { public let success: Bool; public let contact: RideTrustedContactDto }
public struct RideTrustedContactsResponse: Decodable { public let success: Bool; public let contacts: [RideTrustedContactDto] }
public struct AddRideTrustedContactRequest: Encodable { public let phoneNumber: String; public let name: String }
public struct SendStatusToTrustedContactsResponse: Decodable { public let success: Bool; public let sentCount: Int }

// Real Kakao T 대리운전 (designated driver, item 221) -- a professional driver comes to
// the customer's location and drives the CUSTOMER'S OWN CAR home for them, distinct
// from ride-hailing above (driver uses their own vehicle). Mirrors
// DesignatedDriver.kt/DesignatedDriverTrip.kt exactly. bank-mfe/Android already have
// this; this is the first iOS client.
public struct DesignatedDriverDto: Decodable {
    public let id: String
    public let userId: String
    public let accountId: String
    public let licenseNumber: String
    public let available: Bool
    public let currentLatitude: Double?
    public let currentLongitude: Double?
    public let createdAt: String
}
public struct DesignatedDriverResponse: Decodable { public let success: Bool; public let driver: DesignatedDriverDto? }
public struct RegisterDesignatedDriverRequest: Encodable { public let licenseNumber: String }
public struct SetDesignatedDriverAvailabilityRequest: Encodable { public let available: Bool }
public struct UpdateDesignatedDriverLocationRequest: Encodable { public let latitude: Double; public let longitude: Double }
public struct DesignatedDriverTripDto: Decodable, Identifiable {
    public let id: String
    public let customerId: String
    public let driverId: String?
    public let pickupAddress: String
    public let pickupLatitude: Double
    public let pickupLongitude: Double
    public let dropoffAddress: String
    public let dropoffLatitude: Double
    public let dropoffLongitude: Double
    public let vehicleMake: String
    public let vehicleModel: String
    public let vehiclePlate: String
    public let distanceKm: Double
    public let fare: Double
    public let platformFee: Double
    public let status: String
    public let createdAt: String
}
public struct DesignatedDriverTripResponse: Decodable { public let success: Bool; public let trip: DesignatedDriverTripDto }
public struct DesignatedDriverTripsResponse: Decodable { public let success: Bool; public let trips: [DesignatedDriverTripDto] }
public struct RequestDesignatedDriverTripRequest: Encodable {
    public let pickupAddress: String
    public let pickupLatitude: Double
    public let pickupLongitude: Double
    public let dropoffAddress: String
    public let dropoffLatitude: Double
    public let dropoffLongitude: Double
    public let vehicleMake: String
    public let vehicleModel: String
    public let vehiclePlate: String
}

// Real Kakao T 바이크 (Kakao T Bike, item 222) -- real PEER-TO-PEER bike/scooter rental
// pool (any user self-registers a bike they own), billed by elapsed TIME at rental end
// -- distinct from ride-hailing/designated-driver above, which both know their fare up
// front. Mirrors Bike.kt/BikeRentalSession.kt exactly. bank-mfe/Android already have
// this; this is the first iOS client.
public struct RegisterBikeRequest: Encodable { public let type: String; public let latitude: Double; public let longitude: Double }
public struct BikeDto: Decodable, Identifiable {
    public let id: String
    public let ownerUserId: String
    public let accountId: String
    public let type: String
    public let currentLatitude: Double
    public let currentLongitude: Double
    public let available: Bool
    public let createdAt: String
}
public struct BikeResponse: Decodable { public let success: Bool; public let bike: BikeDto }
public struct BikesResponse: Decodable { public let success: Bool; public let bikes: [BikeDto] }
public struct SetBikeAvailabilityRequest: Encodable { public let available: Bool }
public struct UpdateBikeLocationRequest: Encodable { public let latitude: Double; public let longitude: Double }
public struct StartBikeRentalRequest: Encodable { public let bikeId: String; public let startLatitude: Double; public let startLongitude: Double }
public struct EndBikeRentalRequest: Encodable { public let endLatitude: Double; public let endLongitude: Double }
public struct BikeRentalSessionDto: Decodable, Identifiable {
    public let id: String
    public let bikeId: String
    public let riderUserId: String
    public let startedAt: String
    public let endedAt: String?
    public let startLatitude: Double
    public let startLongitude: Double
    public let endLatitude: Double?
    public let endLongitude: Double?
    public let durationMinutes: Int?
    public let totalFare: Double?
    public let platformFee: Double?
    public let status: String
}
public struct BikeRentalResponse: Decodable { public let success: Bool; public let rental: BikeRentalSessionDto }
public struct BikeRentalsResponse: Decodable { public let success: Bool; public let rentals: [BikeRentalSessionDto] }

// Real Kakao T 주차 (Kakao T Parking, item 223) -- real PEER-TO-PEER parking-spot
// rental pool (any user self-lists a spot they own/control), billed by elapsed HOURS
// at checkout -- same "settle at end, no fare known up front" shape Bike already
// establishes, just hourly instead of per-minute. Mirrors ParkingSpot.kt/
// ParkingSession.kt exactly. bank-mfe/Android already have this; this is the first iOS
// client.
public struct RegisterParkingSpotRequest: Encodable { public let address: String; public let latitude: Double; public let longitude: Double; public let hourlyRate: Double }
public struct ParkingSpotDto: Decodable, Identifiable {
    public let id: String
    public let ownerUserId: String
    public let accountId: String
    public let address: String
    public let latitude: Double
    public let longitude: Double
    public let hourlyRate: Double
    public let available: Bool
    public let createdAt: String
}
public struct ParkingSpotResponse: Decodable { public let success: Bool; public let spot: ParkingSpotDto }
public struct ParkingSpotsResponse: Decodable { public let success: Bool; public let spots: [ParkingSpotDto] }
public struct SetParkingSpotAvailabilityRequest: Encodable { public let available: Bool }
public struct StartParkingSessionRequest: Encodable { public let spotId: String }
public struct ParkingSessionDto: Decodable, Identifiable {
    public let id: String
    public let spotId: String
    public let renterUserId: String
    public let startedAt: String
    public let endedAt: String?
    public let durationMinutes: Int?
    public let totalFare: Double?
    public let platformFee: Double?
    public let status: String
}
public struct ParkingSessionResponse: Decodable { public let success: Bool; public let session: ParkingSessionDto }
public struct ParkingSessionsResponse: Decodable { public let success: Bool; public let sessions: [ParkingSessionDto] }

// Real Kakao T 시외버스 (intercity bus booking, item 224) -- real PEER-TO-PEER
// coach-operator trip pool, fare charged in FULL at booking time (not settled at end
// like Parking/Bike). Mirrors BusTrip.kt/BusBooking.kt exactly. bank-mfe/Android
// already have this; this is the first iOS client.
public struct PostBusTripRequest: Encodable {
    public let origin: String; public let destination: String; public let departureTime: String
    public let totalSeats: Int; public let farePerSeat: Double
}
public struct BusTripDto: Decodable, Identifiable {
    public let id: String
    public let operatorUserId: String
    public let accountId: String
    public let origin: String
    public let destination: String
    public let departureTime: String
    public let totalSeats: Int
    public let availableSeats: Int
    public let farePerSeat: Double
    public let createdAt: String
}
public struct BusTripResponse: Decodable { public let success: Bool; public let trip: BusTripDto }
public struct BusTripsResponse: Decodable { public let success: Bool; public let trips: [BusTripDto] }
public struct BookBusSeatsRequest: Encodable { public let tripId: String; public let seatCount: Int }
public struct BusBookingDto: Decodable, Identifiable {
    public let id: String
    public let tripId: String
    public let riderUserId: String
    public let seatCount: Int
    public let totalFare: Double
    public let platformFee: Double
    public let paymentTransactionId: String
    public let status: String
    public let refundTransactionId: String?
    public let createdAt: String
}
public struct BusBookingResponse: Decodable { public let success: Bool; public let booking: BusBookingDto }
public struct BusBookingsResponse: Decodable { public let success: Bool; public let bookings: [BusBookingDto] }

// Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- a genuinely
// different shape from the trip/rental structs above: no account movement, no location,
// just a real question -> competing answers -> asker-adopts-one-best-answer content
// flow. Mirrors KnowledgeQuestion.kt/KnowledgeAnswer.kt exactly. bank-mfe/Android
// already have this; this is the first iOS client.
public struct KnowledgeCategory: Decodable, Identifiable { public let id: String; public let label: String }
public struct KnowledgeCategoriesResponse: Decodable { public let success: Bool; public let categories: [KnowledgeCategory] }
public struct PostKnowledgeQuestionRequest: Encodable { public let category: String; public let title: String; public let body: String }
public struct KnowledgeQuestionDto: Decodable, Identifiable {
    public let id: String
    public let askerId: String
    public let category: String
    public let title: String
    public let body: String
    public let adoptedAnswerId: String?
    public let createdAt: String
}
public struct KnowledgeQuestionResponse: Decodable { public let success: Bool; public let question: KnowledgeQuestionDto }
public struct KnowledgeQuestionsResponse: Decodable { public let success: Bool; public let questions: [KnowledgeQuestionDto] }
public struct PostKnowledgeAnswerRequest: Encodable { public let body: String }
public struct KnowledgeAnswerDto: Decodable, Identifiable {
    public let id: String
    public let questionId: String
    public let answererId: String
    public let body: String
    public let isAdopted: Bool
    public let createdAt: String
}
public struct KnowledgeAnswerResponse: Decodable { public let success: Bool; public let answer: KnowledgeAnswerDto }
public struct KnowledgeAnswersResponse: Decodable { public let success: Bool; public let answers: [KnowledgeAnswerDto] }
public struct KnowledgeReputationResponse: Decodable { public let success: Bool; public let adoptedAnswerCount: Int }

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- mirrors
// bank-mfe's lib/vehicleInspection.ts exactly. bank-mfe and Android already have this;
// this is the first iOS client.
public struct VehicleInspectionMechanicDto: Decodable {
    public let id: String
    public let userId: String
    public let accountId: String
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
    public let accountBalance: Double
    public let recentTransactions: [TransactionDto]
}
public struct FamilyLinkResponse: Decodable { public let success: Bool; public let link: FamilyLinkDto }
public struct FamilyLinksResponse: Decodable { public let success: Bool; public let invites: [FamilyLinkDto] }
public struct FamilyLinkViewsResponse: Decodable { public let success: Bool; public let children: [FamilyLinkViewDto]?; public let guardians: [FamilyLinkViewDto]? }
public struct ChildOverviewResponse: Decodable { public let success: Bool; public let overview: ChildOverviewDto }

public struct SavingsGoal: Decodable {
    public let id: String
    public let userId: String
    public let accountId: String
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

// Real named-goal creation (2026-08-22, product-feel/Toss-parity work) -- iOS could
// view and deposit into existing SavingsGoals but had no way to ever create one, a
// real standing gap vs Android's MainViewModel.createSavingsGoal and web's own
// createGoal (BankDashboard.tsx's CreateGoalForm) -- both real, already-shipped.
// Mirrors SavingsController.CreateGoalRequest exactly (services/backend/savings):
// name + targetAmount required, monthlyContribution/targetDate/category optional.
public struct CreateSavingsGoalRequest: Encodable {
    public let name: String
    public let targetAmount: Double
    public let monthlyContribution: Double?
    public let targetDate: String?
    public let category: String?
}

// Real backend response for POST /api/v1/savings/goals is {success, goal} --
// SavingsController.createGoal's own body literal -- not the SavingsGoalsResponse
// (plural `goals`) shape used by the GET list endpoint above.
public struct CreateSavingsGoalResponse: Decodable {
    public let success: Bool
    public let goal: SavingsGoal
}

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
    public let accountId: String
    public let balance: Double
    public let rate: Double
    public let earnedThisMonth: Double
    public let earnedTotal: Double
}

public struct InterestJarResponse: Decodable { public let success: Bool; public let jar: InterestJar }

// Real per-bucket ledger (2026-08-31, direct user-supplied Toss Bank screenshots:
// 보관하기/매일모으기 each get their own full-screen ledger) -- mirrors backend's own
// BucketTransactionDto exactly (services/backend/savings/.../BucketTransactionDto.kt).
// One normalized shape every savings bucket's own transaction endpoint returns.
public struct BucketTransactionDto: Decodable, Identifiable {
    public let id: String
    public let description: String
    public let amount: Double
    public let isCredit: Bool
    public let balanceAfter: Double
    public let createdAt: String

    // Explicit public init -- Swift's synthesized memberwise initializer for a
    // public struct is never more visible than internal, so cross-module callers
    // (App/Sources constructs this directly for the Youth account, which has no
    // dedicated ledger account and adapts its plain Transaction history instead)
    // would otherwise fail to find a callable initializer at all.
    public init(id: String, description: String, amount: Double, isCredit: Bool, balanceAfter: Double, createdAt: String) {
        self.id = id
        self.description = description
        self.amount = amount
        self.isCredit = isCredit
        self.balanceAfter = balanceAfter
        self.createdAt = createdAt
    }
}

public struct BucketTransactionsResponse: Decodable { public let success: Bool; public let transactions: [BucketTransactionDto] }

// Real Deposit Protection Fund status (2026-08-11) -- see backend's
// DepositProtectionFund.kt doc comment. coverageCapPerUser/contributionRateBps are
// itunda's own chosen policy figures, not a claimed real BNR-backed scheme -- every
// client rendering this must keep that framing, not present it as real deposit
// insurance.
public struct DepositProtectionStatus: Decodable {
    public let fundReserveBalance: Double
    public let coverageCapPerUser: Double
    public let contributionRateBps: Int
    public let lastContributionAt: String?
    public let yourTotalDeposits: Double
    public let yourCoveredBalance: Double
}
public struct DepositProtectionStatusResponse: Decodable { public let success: Bool; public let status: DepositProtectionStatus }

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
    // Real server-side ranking (2026-08-11) -- see backend DiscoverService's own doc
    // comment (Toss Intelligence-banner research). Default 0 only matters for a
    // hypothetical old cached response missing this field.
    public let priority: Int
}
public struct DiscoverResponse: Decodable { public let success: Bool; public let items: [DiscoverItem] }

/// Authenticated GET helper for feature screens that need to call the rest of
/// services/backend's API once logged in -- reads the bearer token from
/// KeychainTokenStore so callers never have to thread it through manually. First
/// real use (2026-07-11): BankView.swift's account/savings/interest-jar data, closing
/// the "iOS has no real feature data-fetching wired in" gap this comment used to name.
extension NetworkClient {
    public func getAccounts() async throws -> AccountsResponse { try await get("api/v1/account") }

    public func openForeignAccount(_ request: OpenForeignAccountRequest) async throws -> ForeignAccountsResponse {
        try await authenticatedPost("api/v1/account/foreign-currency/accounts", body: request)
    }

    public func getForeignAccounts() async throws -> ForeignAccountsResponse { try await get("api/v1/account/foreign-currency/accounts") }

    public func getExchangeRate(from: String, to: String) async throws -> ExchangeRateResponse {
        try await get("api/v1/account/foreign-currency/rate", query: [
            URLQueryItem(name: "from", value: from),
            URLQueryItem(name: "to", value: to),
        ])
    }

    public func convertCurrency(_ request: ConvertCurrencyRequest) async throws -> ConvertCurrencyResponse {
        try await authenticatedPost("api/v1/account/foreign-currency/convert", body: request)
    }

    public func getMyConversions() async throws -> CurrencyConversionsResponse { try await get("api/v1/account/foreign-currency/conversions") }

    // SetRateAlertRequest's own doc comment.
    public func setRateAlert(fromCurrency: String, toCurrency: String, targetRate: Double, direction: String) async throws -> SetRateAlertResponse {
        try await authenticatedPost("api/v1/account/foreign-currency/rate-alert", body: SetRateAlertRequest(fromCurrency: fromCurrency, toCurrency: toCurrency, targetRate: targetRate, direction: direction))
    }

    public func clearRateAlert(fromCurrency: String, toCurrency: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/account/foreign-currency/rate-alert", query: [
            URLQueryItem(name: "fromCurrency", value: fromCurrency),
            URLQueryItem(name: "toCurrency", value: toCurrency),
        ])
    }

    public func getMyRateAlerts() async throws -> RateAlertsResponse { try await get("api/v1/account/foreign-currency/rate-alerts") }

    public func getUpfrontDeposits() async throws -> UpfrontDepositsResponse { try await get("api/v1/upfront-deposits") }

    public func getUpfrontDepositTransactions(id: String) async throws -> BucketTransactionsResponse { try await get("api/v1/upfront-deposits/\(id)/transactions") }

    // Real bug found 2026-08-15 (same pass that found WeeklySavings' identical gap --
    // a full cross-reference of every backend endpoint requiring a non-nullable
    // Idempotency-Key header against every authenticatedPost call in this file that
    // omits idempotencyKey): UpfrontInterestDepositController's create and withdraw
    // both declare `@RequestHeader("Idempotency-Key") idempotencyKey: String`
    // (non-nullable), but neither call here ever sent it -- both have been silently
    // 400ing with IDEMPOTENCY_KEY_REQUIRED since they shipped.
    public func openUpfrontDeposit(_ request: OpenUpfrontDepositRequest) async throws -> OpenUpfrontDepositResponse {
        try await authenticatedPost("api/v1/upfront-deposits", body: request, idempotencyKey: UUID().uuidString)
    }

    public func withdrawUpfrontDeposit(id: String) async throws -> OpenUpfrontDepositResponse {
        try await authenticatedPostWithCode("api/v1/upfront-deposits/\(id)/withdraw", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    // Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.account.
    // AccountService.getSpendingInsight, real since 2026-07-13) -- first iOS client for
    // this feature (item 108, found backend-only via a fresh matrix scan; bank-mfe/
    // Android ported the same day as items 106/107).
    public func getSpendingInsight() async throws -> SpendingInsightResponse { try await get("api/v1/account/spending") }
    public func getMonthlySpendingReport() async throws -> MonthlySpendingReportResponse { try await get("api/v1/account/spending/monthly-report") }

    public func getBudgets() async throws -> GetBudgetsResponse { try await get("api/v1/account/budgets") }

    public func setBudget(category: String?, monthlyLimit: Double) async throws -> SetBudgetResponse {
        try await authenticatedPost("api/v1/account/budgets", body: SetBudgetRequest(category: category, monthlyLimit: monthlyLimit))
    }

    // Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) --
    // first iOS client for this feature (item 110, found via a fresh matrix scan;
    // bank-mfe has had it since the same day, Android ported it the same day as item
    // 109). Mirrors bank-mfe's lib/rideshare.ts and Android's ApiService.kt exactly.
    public func registerAsRideDriver(licenseNumber: String) async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/register", body: RegisterRideDriverRequest(licenseNumber: licenseNumber))
    }

    public func getMyRideDriverProfile() async throws -> RideDriverResponse { try await get("api/v1/rides/drivers/me") }

    public func setRideDriverAvailability(available: Bool) async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/availability", body: SetRideDriverAvailabilityRequest(available: available))
    }

    public func updateRideDriverLocation(latitude: Double, longitude: Double) async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/location", body: UpdateRideDriverLocationRequest(latitude: latitude, longitude: longitude))
    }

    // Real Uber "Destination Filter" + driver earnings report (uncalled-endpoint
    // sweep follow-up, item 247) -- see RideDriverService.setDestination/
    // RideTripService.getMyEarnings's own real doc comments. Both real, fully-built
    // backend endpoints found with zero client anywhere; bank-mfe/Android already
    // have this; this is the iOS port. A driver heading somewhere real (e.g. home)
    // sets it here and only gets offered trips heading that direction.
    public func setRideDriverDestination(latitude: Double, longitude: Double) async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/destination", body: SetRideDriverDestinationRequest(latitude: latitude, longitude: longitude))
    }
    public func clearRideDriverDestination() async throws -> RideDriverResponse {
        try await authenticatedPost("api/v1/rides/drivers/destination/clear", body: EmptyBody())
    }
    public func getMyRideEarnings(from: String? = nil, to: String? = nil) async throws -> RideEarningsResponse {
        try await get("api/v1/rides/trips/my-earnings", query: [
            URLQueryItem(name: "from", value: from),
            URLQueryItem(name: "to", value: to),
        ])
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
    // Real Uber "Verify Your Ride" PIN (uber.com/pl/en/blog/pin-number) -- the driver
    // must enter the exact 4-digit code the passenger reads aloud before the trip (and
    // the fare clock) actually starts.
    public func startRideTrip(id: String, pin: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/start", body: StartRideTripRequest(pin: pin))
    }

    // Real passenger-only PIN lookup -- a stranger, or even the trip's own driver, gets
    // a real 404 from the backend.
    public func getRideTripPin(id: String) async throws -> RideTripPinResponse {
        try await get("api/v1/rides/trips/\(id)/pin")
    }
    public func completeRideTrip(id: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/complete", body: EmptyBody())
    }
    public func cancelRideTrip(id: String) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(id)/cancel", body: EmptyBody())
    }

    // Real Uber post-trip tipping -- see RideTripDto.tipAmount's own doc comment. Real
    // Idempotency-Key required -- a tip is a real account-to-account transfer.
    public func tipRideDriver(tripId: String, amount: Double) async throws -> RideTripResponse {
        try await authenticatedPost("api/v1/rides/trips/\(tripId)/tip", body: TipRideTripRequest(amount: amount), idempotencyKey: UUID().uuidString)
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

    /// Real "meet your driver" rating + reviews during an active trip (item 233) --
    /// found via the uncalled-endpoint sweep, see bank-mfe's lib/rideshare.ts own doc
    /// comment on fetchDriverReviews for the full sourced account. bank-mfe/Android
    /// shipped this first (2026-08-05); this is the iOS port.
    public func getRideDriverReviews(driverId: String) async throws -> RideDriverReviewsResponse {
        try await get("api/v1/rides/drivers/\(driverId)/reviews", query: [URLQueryItem(name: "size", value: "10")])
    }

    // Real Uber Safety "Trusted Contacts" (help.uber.com) -- a persistent, up-to-5
    // contact list set up once, distinct from shareTripStatus's per-share conversation
    // pick above. Last remaining client platform for this feature (item 161; bank-mfe
    // wired it first, Android ported it the same day). Mirrors Android's ApiService.kt
    // getRideTrustedContacts/addRideTrustedContact/removeRideTrustedContact/
    // sendStatusToRideTrustedContacts exactly. See the backend's RideTrustedContact.kt
    // doc comment for the full sourcing.
    public func getRideTrustedContacts() async throws -> RideTrustedContactsResponse { try await get("api/v1/rides/trusted-contacts") }

    public func addRideTrustedContact(phoneNumber: String, name: String) async throws -> RideTrustedContactResponse {
        try await authenticatedPostWithCode("api/v1/rides/trusted-contacts", body: AddRideTrustedContactRequest(phoneNumber: phoneNumber, name: name))
    }

    public func removeRideTrustedContact(contactId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/rides/trusted-contacts/\(contactId)")
    }

    public func sendStatusToRideTrustedContacts(tripId: String) async throws -> SendStatusToTrustedContactsResponse {
        try await authenticatedPost("api/v1/rides/trips/\(tripId)/send-status", body: EmptyBody())
    }

    // Real Kakao T 대리운전 (designated driver, item 221) -- first iOS client for this
    // feature. bank-mfe/Android already have this; mirrors ApiService.kt exactly.
    public func registerAsDesignatedDriver(licenseNumber: String) async throws -> DesignatedDriverResponse {
        try await authenticatedPost("api/v1/designated-driver/drivers/register", body: RegisterDesignatedDriverRequest(licenseNumber: licenseNumber))
    }

    public func getMyDesignatedDriverProfile() async throws -> DesignatedDriverResponse { try await get("api/v1/designated-driver/drivers/me") }

    public func setDesignatedDriverAvailability(available: Bool) async throws -> DesignatedDriverResponse {
        try await authenticatedPost("api/v1/designated-driver/drivers/availability", body: SetDesignatedDriverAvailabilityRequest(available: available))
    }

    public func updateDesignatedDriverLocation(latitude: Double, longitude: Double) async throws -> DesignatedDriverResponse {
        try await authenticatedPost("api/v1/designated-driver/drivers/location", body: UpdateDesignatedDriverLocationRequest(latitude: latitude, longitude: longitude))
    }

    // Real bug found live (2026-08-02): missing Idempotency-Key -- see the backend's
    // DesignatedDriverController.requestTrip doc comment for the full account.
    public func requestDesignatedDriverTrip(
        pickupAddress: String, pickupLatitude: Double, pickupLongitude: Double,
        dropoffAddress: String, dropoffLatitude: Double, dropoffLongitude: Double,
        vehicleMake: String, vehicleModel: String, vehiclePlate: String
    ) async throws -> DesignatedDriverTripResponse {
        try await authenticatedPost(
            "api/v1/designated-driver/trips",
            body: RequestDesignatedDriverTripRequest(
                pickupAddress: pickupAddress, pickupLatitude: pickupLatitude, pickupLongitude: pickupLongitude,
                dropoffAddress: dropoffAddress, dropoffLatitude: dropoffLatitude, dropoffLongitude: dropoffLongitude,
                vehicleMake: vehicleMake, vehicleModel: vehicleModel, vehiclePlate: vehiclePlate
            ),
            idempotencyKey: UUID().uuidString
        )
    }

    public func getAvailableDesignatedDriverTrips() async throws -> DesignatedDriverTripsResponse { try await get("api/v1/designated-driver/trips/available") }
    public func getMyDesignatedDriverTrips() async throws -> DesignatedDriverTripsResponse { try await get("api/v1/designated-driver/trips/my-trips") }
    public func getMyDesignatedDriverDriverTrips() async throws -> DesignatedDriverTripsResponse { try await get("api/v1/designated-driver/trips/my-driver-trips") }

    public func acceptDesignatedDriverTrip(id: String) async throws -> DesignatedDriverTripResponse {
        try await authenticatedPost("api/v1/designated-driver/trips/\(id)/accept", body: EmptyBody())
    }
    public func startDesignatedDriverTrip(id: String) async throws -> DesignatedDriverTripResponse {
        try await authenticatedPost("api/v1/designated-driver/trips/\(id)/start-driving", body: EmptyBody())
    }
    public func completeDesignatedDriverTrip(id: String) async throws -> DesignatedDriverTripResponse {
        try await authenticatedPost("api/v1/designated-driver/trips/\(id)/complete", body: EmptyBody())
    }
    public func cancelDesignatedDriverTrip(id: String) async throws -> DesignatedDriverTripResponse {
        try await authenticatedPost("api/v1/designated-driver/trips/\(id)/cancel", body: EmptyBody())
    }

    // Real Kakao T 바이크 (Kakao T Bike, item 222) -- first iOS client for this feature.
    // bank-mfe/Android already have this; mirrors ApiService.kt exactly.
    public func registerBike(type: String, latitude: Double, longitude: Double) async throws -> BikeResponse {
        try await authenticatedPost("api/v1/bikeshare/bikes", body: RegisterBikeRequest(type: type, latitude: latitude, longitude: longitude))
    }

    public func getMyBikes() async throws -> BikesResponse { try await get("api/v1/bikeshare/bikes/mine") }

    public func setBikeAvailability(bikeId: String, available: Bool) async throws -> BikeResponse {
        try await authenticatedPost("api/v1/bikeshare/bikes/\(bikeId)/availability", body: SetBikeAvailabilityRequest(available: available))
    }

    public func updateBikeLocation(bikeId: String, latitude: Double, longitude: Double) async throws -> BikeResponse {
        try await authenticatedPost("api/v1/bikeshare/bikes/\(bikeId)/location", body: UpdateBikeLocationRequest(latitude: latitude, longitude: longitude))
    }

    public func getNearbyBikes(latitude: Double, longitude: Double, radiusKm: Double = 5.0) async throws -> BikesResponse {
        try await get("api/v1/bikeshare/bikes/nearby", query: [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "radiusKm", value: String(radiusKm)),
        ])
    }

    public func startBikeRental(bikeId: String, startLatitude: Double, startLongitude: Double) async throws -> BikeRentalResponse {
        try await authenticatedPost("api/v1/bikeshare/rentals", body: StartBikeRentalRequest(bikeId: bikeId, startLatitude: startLatitude, startLongitude: startLongitude))
    }

    public func endBikeRental(sessionId: String, endLatitude: Double, endLongitude: Double) async throws -> BikeRentalResponse {
        try await authenticatedPost("api/v1/bikeshare/rentals/\(sessionId)/end", body: EndBikeRentalRequest(endLatitude: endLatitude, endLongitude: endLongitude))
    }

    public func getMyBikeRentalHistory() async throws -> BikeRentalsResponse { try await get("api/v1/bikeshare/rentals/my-history") }

    // Real Kakao T 주차 (Kakao T Parking, item 223) -- first iOS client for this
    // feature. bank-mfe/Android already have this; mirrors ApiService.kt exactly.
    public func registerParkingSpot(address: String, latitude: Double, longitude: Double, hourlyRate: Double) async throws -> ParkingSpotResponse {
        try await authenticatedPost("api/v1/parking/spots", body: RegisterParkingSpotRequest(address: address, latitude: latitude, longitude: longitude, hourlyRate: hourlyRate))
    }

    public func getMyParkingSpots() async throws -> ParkingSpotsResponse { try await get("api/v1/parking/spots/mine") }

    public func setParkingSpotAvailability(spotId: String, available: Bool) async throws -> ParkingSpotResponse {
        try await authenticatedPost("api/v1/parking/spots/\(spotId)/availability", body: SetParkingSpotAvailabilityRequest(available: available))
    }

    public func getNearbyParkingSpots(latitude: Double, longitude: Double, radiusKm: Double = 5.0) async throws -> ParkingSpotsResponse {
        try await get("api/v1/parking/spots/nearby", query: [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "radiusKm", value: String(radiusKm)),
        ])
    }

    public func startParkingSession(spotId: String) async throws -> ParkingSessionResponse {
        try await authenticatedPost("api/v1/parking/sessions", body: StartParkingSessionRequest(spotId: spotId))
    }

    public func endParkingSession(sessionId: String) async throws -> ParkingSessionResponse {
        try await authenticatedPost("api/v1/parking/sessions/\(sessionId)/end", body: EmptyBody())
    }

    public func getMyParkingHistory() async throws -> ParkingSessionsResponse { try await get("api/v1/parking/sessions/my-history") }

    // Real Kakao T 시외버스 (intercity bus booking, item 224) -- first iOS client for
    // this feature. bank-mfe/Android already have this; mirrors ApiService.kt exactly.
    public func postBusTrip(origin: String, destination: String, departureTime: String, totalSeats: Int, farePerSeat: Double) async throws -> BusTripResponse {
        try await authenticatedPost("api/v1/bus/trips", body: PostBusTripRequest(origin: origin, destination: destination, departureTime: departureTime, totalSeats: totalSeats, farePerSeat: farePerSeat))
    }

    public func getMyBusTrips() async throws -> BusTripsResponse { try await get("api/v1/bus/trips/mine") }

    public func getBusTripBookings(tripId: String) async throws -> BusBookingsResponse { try await get("api/v1/bus/trips/\(tripId)/bookings") }

    public func searchBusTrips(origin: String?, destination: String?) async throws -> BusTripsResponse {
        var query: [URLQueryItem] = []
        if let origin, !origin.isEmpty { query.append(URLQueryItem(name: "origin", value: origin)) }
        if let destination, !destination.isEmpty { query.append(URLQueryItem(name: "destination", value: destination)) }
        return try await get("api/v1/bus/trips/search", query: query)
    }

    // Real bug found live (2026-08-02): missing Idempotency-Key -- see the backend's
    // BusController.bookSeats doc comment for the full account.
    public func bookBusSeats(tripId: String, seatCount: Int) async throws -> BusBookingResponse {
        try await authenticatedPost("api/v1/bus/bookings", body: BookBusSeatsRequest(tripId: tripId, seatCount: seatCount), idempotencyKey: UUID().uuidString)
    }

    public func cancelBusBooking(bookingId: String) async throws -> BusBookingResponse {
        try await authenticatedPost("api/v1/bus/bookings/\(bookingId)/cancel", body: EmptyBody())
    }

    public func getMyBusBookings() async throws -> BusBookingsResponse { try await get("api/v1/bus/bookings/my-history") }

    // Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- first
    // iOS client for this feature. bank-mfe/Android already have this; mirrors
    // ApiService.kt exactly.
    public func getKnowledgeCategories() async throws -> KnowledgeCategoriesResponse { try await get("api/v1/knowledge/categories") }

    public func postKnowledgeQuestion(category: String, title: String, body: String) async throws -> KnowledgeQuestionResponse {
        try await authenticatedPost("api/v1/knowledge/questions", body: PostKnowledgeQuestionRequest(category: category, title: title, body: body))
    }

    public func getKnowledgeQuestions(category: String?) async throws -> KnowledgeQuestionsResponse {
        var query: [URLQueryItem] = []
        if let category, !category.isEmpty { query.append(URLQueryItem(name: "category", value: category)) }
        return try await get("api/v1/knowledge/questions", query: query)
    }

    public func getMyKnowledgeQuestions() async throws -> KnowledgeQuestionsResponse { try await get("api/v1/knowledge/questions/my-questions") }

    public func getMyKnowledgeAnswers() async throws -> KnowledgeAnswersResponse { try await get("api/v1/knowledge/answers/my-answers") }

    public func getMyKnowledgeReputation() async throws -> KnowledgeReputationResponse { try await get("api/v1/knowledge/reputation/me") }

    public func getKnowledgeQuestion(questionId: String) async throws -> KnowledgeQuestionResponse { try await get("api/v1/knowledge/questions/\(questionId)") }

    public func getKnowledgeAnswers(questionId: String) async throws -> KnowledgeAnswersResponse { try await get("api/v1/knowledge/questions/\(questionId)/answers") }

    public func postKnowledgeAnswer(questionId: String, body: String) async throws -> KnowledgeAnswerResponse {
        try await authenticatedPost("api/v1/knowledge/questions/\(questionId)/answers", body: PostKnowledgeAnswerRequest(body: body))
    }

    public func adoptKnowledgeAnswer(questionId: String, answerId: String) async throws -> KnowledgeAnswerResponse {
        try await authenticatedPost("api/v1/knowledge/questions/\(questionId)/answers/\(answerId)/adopt", body: EmptyBody())
    }

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
    // Real gap found 2026-09-04: FamilyLinkService.inviteChild's real "No itunda
    // account found for this phone number" (404) and "Cannot link your own account
    // as a child" (400) were both falling into FamilyLinkScreenView's generic
    // "Could not send this invitation." catch-all -- only the 409 case was already
    // handled specifically.
    public func inviteFamilyChild(childPhoneNumber: String) async throws -> FamilyLinkResponse {
        try await authenticatedPostWithMessage("api/v1/family/invite", body: InviteChildRequest(childPhoneNumber: childPhoneNumber))
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
    public func createSavingsGoal(name: String, targetAmount: Double, monthlyContribution: Double?, targetDate: String?, category: String? = nil) async throws -> CreateSavingsGoalResponse {
        try await authenticatedPost(
            "api/v1/savings/goals",
            body: CreateSavingsGoalRequest(name: name, targetAmount: targetAmount, monthlyContribution: monthlyContribution, targetDate: targetDate, category: category),
            idempotencyKey: UUID().uuidString
        )
    }
    // Real per-bucket ledger (2026-08-31) -- see BucketTransactionDto's own doc
    // comment for the full account of the isolation gap this closes.
    public func getSavingsGoalTransactions(goalId: String) async throws -> BucketTransactionsResponse {
        try await get("api/v1/savings/goals/\(goalId)/transactions")
    }

    public func getInterestJar() async throws -> InterestJarResponse { try await get("api/v1/savings/interest-jar") }

    public func getInterestJarTransactions() async throws -> BucketTransactionsResponse { try await get("api/v1/savings/interest-jar/transactions") }
    public func getDepositProtectionStatus() async throws -> DepositProtectionStatusResponse { try await get("api/v1/savings/deposit-protection") }

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
    public func getTransactionHistory() async throws -> TransactionHistoryResponse { try await get("api/v1/account/transactions") }
    // Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21) --
    // scoped to one account's own transactions, not getTransactionHistory's mix of
    // every account. See AccountService.getAccountTransactionHistory on the backend.
    public func getAccountTransactionHistory(accountId: String) async throws -> TransactionHistoryResponse { try await get("api/v1/account/\(accountId)/transactions") }
    // Real account settings screen (2026-07-12).
    public func getProfile() async throws -> ProfileResponse { try await get("api/v1/auth/profile") }

    // Real hyperlocal neighborhood (2026-07-20) -- a real coordinate in, reverse-geocoded
    // server-side into a real neighborhood/sector name. See AuthService.setNeighborhood
    // and bank-mfe's lib/neighborhood.ts, which this mirrors exactly.
    public func setNeighborhood(latitude: Double, longitude: Double) async throws -> ProfileResponse {
        try await authenticatedPost("api/v1/auth/profile/neighborhood", body: SetNeighborhoodRequest(latitude: latitude, longitude: longitude))
    }

    // Real second neighborhood (2026-07-30) -- see PublicUser.secondNeighborhood's own
    // doc comment. Mirrors setNeighborhood exactly; separate add/remove endpoints since
    // the second neighborhood is optional and independently clearable.
    public func setSecondNeighborhood(latitude: Double, longitude: Double) async throws -> ProfileResponse {
        try await authenticatedPost("api/v1/auth/profile/second-neighborhood", body: SetSecondNeighborhoodRequest(latitude: latitude, longitude: longitude))
    }

    public func clearSecondNeighborhood() async throws -> ProfileResponse {
        try await authenticatedDelete("api/v1/auth/profile/second-neighborhood")
    }

    // Real profile photo (URL, not a binary upload) -- see PublicUser.profilePhotoUrl's
    // own doc comment.
    public func updateProfilePhoto(profilePhotoUrl: String) async throws -> ProfileResponse {
        try await authenticatedPut("api/v1/auth/profile/photo", body: UpdateProfilePhotoRequest(profilePhotoUrl: profilePhotoUrl))
    }

    // Real age-eligibility gate for the Youth account (2026-07-28) -- see
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

    // Real KakaoBank mini-style capped starter account (rw.itunda.account.
    // YouthAccountService, 2026-07-28) -- first iOS client for this feature (item 101),
    // mirroring bank-mfe's lib/youthAccount.ts and Android's ApiService.kt equivalents.
    // open() carries no Idempotency-Key (a second call naturally just returns the same
    // real, already-open account, no duplicate side effect), so it keeps its own
    // dedicated request path, decoding the real ApiError.code directly on a 422 for the
    // birth-date/age-eligibility cases neither authenticatedPost nor deposit's own error
    // surface needs. deposit() moved to authenticatedPost's idempotency-gated path
    // 2026-08-05 (real bug found live via a repo-wide idempotency-coverage audit):
    // YouthAccountController.kt's own deposit endpoint posted a real account-to-account
    // ledger transaction on every call with no Idempotency-Key requirement -- a
    // network-timeout retry of the exact same deposit would move the same money twice.
    public func openYouthAccount() async throws -> OpenYouthAccountResponse {
        try await postYouthAccount("api/v1/account/youth/open", body: EmptyBody())
    }

    public func depositYouthAccount(amount: Double) async throws -> DepositYouthAccountResponse {
        try await authenticatedPost("api/v1/account/youth/deposit", body: DepositYouthAccountRequest(amount: amount), idempotencyKey: UUID().uuidString)
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
        try await authenticatedPostWithCode("api/v1/group-accounts/\(id)/members", body: InviteMemberRequest(phoneNumber: phoneNumber))
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

    // Real ikimina (Rwanda's own rotating savings & credit association) -- see
    // IkiminaDto's own doc comment. Genuinely distinct from every Toss/Kakao/Naver/
    // Coupang-sourced feature in this backend.
    public func createIkimina(name: String, contributionAmount: Double, cycleFrequencyDays: Int, memberCap: Int) async throws -> CreateIkiminaResponse {
        try await authenticatedPost("api/v1/ikiminas", body: CreateIkiminaRequest(name: name, contributionAmount: contributionAmount, cycleFrequencyDays: cycleFrequencyDays, memberCap: memberCap))
    }

    public func getMyIkiminas() async throws -> IkiminasResponse { try await get("api/v1/ikiminas") }

    public func getIkimina(id: String) async throws -> IkiminaDetailResponse { try await get("api/v1/ikiminas/\(id)") }

    public func inviteIkiminaMember(id: String, phoneNumber: String) async throws -> InviteIkiminaMemberResponse {
        try await authenticatedPostWithCode("api/v1/ikiminas/\(id)/members", body: InviteIkiminaMemberRequest(phoneNumber: phoneNumber))
    }

    public func startIkiminaCycle(id: String) async throws -> CreateIkiminaResponse {
        try await authenticatedPost("api/v1/ikiminas/\(id)/start", body: EmptyBody())
    }

    public func contributeToIkimina(id: String) async throws -> CreateIkiminaResponse {
        try await authenticatedPostWithCode("api/v1/ikiminas/\(id)/contribute", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func triggerIkiminaPayout(id: String) async throws -> IkiminaPayoutResponse {
        try await authenticatedPost("api/v1/ikiminas/\(id)/payout", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    // Real Umurenge SACCO-style shares & dividends -- see SaccoShareholdingDto's own
    // doc comment. Genuinely distinct from every Toss/Kakao/Naver/Coupang-sourced
    // feature in this backend and from Ikimina (rotating-pot ROSCA).
    public func buySaccoShares(amount: Double) async throws -> SaccoShareholdingResponse {
        try await authenticatedPost("api/v1/sacco/shares/buy", body: SaccoAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func redeemSaccoShares(amount: Double) async throws -> SaccoShareholdingResponse {
        try await authenticatedPost("api/v1/sacco/shares/redeem", body: SaccoAmountRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func getMySaccoShareholding() async throws -> SaccoShareholdingResponse { try await get("api/v1/sacco/shares/me") }

    public func getMySaccoDividendHistory() async throws -> SaccoDividendPayoutsResponse { try await get("api/v1/sacco/dividends/me") }

    // Real Rwanda coffee-cooperative harvest-advance / input financing -- see
    // CooperativeDto's own doc comment. The third feature in this backend not sourced
    // from Toss/Kakao/Naver/Coupang. amount must equal the advance's own
    // principalAmount exactly -- a real debt-forgiveness bug this session caught and
    // fixed before shipping (no partial repayment).
    public func registerCooperative(name: String, cropType: String, registrationNumber: String?) async throws -> CooperativeResponse {
        try await authenticatedPost("api/v1/cooperatives", body: RegisterCooperativeRequest(name: name, cropType: cropType, registrationNumber: registrationNumber))
    }

    public func joinCooperative(cooperativeId: String) async throws -> CooperativeMembershipResponse {
        try await authenticatedPost("api/v1/cooperatives/\(cooperativeId)/join", body: EmptyBody())
    }

    public func getMyCooperativeMemberships() async throws -> CooperativeMembershipsResponse { try await get("api/v1/cooperatives/my-memberships") }

    public func requestHarvestAdvance(membershipId: String, principalAmount: Double, purpose: String, expectedHarvestDate: String) async throws -> HarvestAdvanceResponse {
        try await authenticatedPost(
            "api/v1/cooperatives/advances",
            body: RequestAdvanceRequest(membershipId: membershipId, principalAmount: principalAmount, purpose: purpose, expectedHarvestDate: expectedHarvestDate),
        )
    }

    public func disburseHarvestAdvance(advanceId: String) async throws -> HarvestAdvanceResponse {
        try await authenticatedPost("api/v1/cooperatives/advances/\(advanceId)/disburse", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func repayHarvestAdvance(advanceId: String, amount: Double) async throws -> HarvestAdvanceResponse {
        try await authenticatedPost("api/v1/cooperatives/advances/\(advanceId)/repay", body: RepayAdvanceRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func getMyHarvestAdvances() async throws -> HarvestAdvancesResponse { try await get("api/v1/cooperatives/advances/my-advances") }

    // Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
    // microloan -- see VupLoanDto's own doc comment. No Idempotency-Key on apply (not
    // money movement itself, matching the backend's own contract); disburse/repay both
    // require one, same convention as every other money-moving call in this file.
    public func applyForVupLoan(declaredUbudeheCategory: Int, purpose: String, amount: Double) async throws -> VupLoanResponse {
        try await authenticatedPost("api/v1/loans/vup/apply", body: ApplyForVupLoanRequest(declaredUbudeheCategory: declaredUbudeheCategory, purpose: purpose, amount: amount))
    }

    public func disburseVupLoan(loanId: String) async throws -> VupLoanResponse {
        try await authenticatedPost("api/v1/loans/vup/\(loanId)/disburse", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func repayVupLoan(loanId: String, amount: Double) async throws -> VupLoanResponse {
        try await authenticatedPost("api/v1/loans/vup/\(loanId)/repay", body: RepayVupLoanRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func getMyVupLoans() async throws -> VupLoansResponse { try await get("api/v1/loans/vup/my") }

    public func getVupLoanEligibility() async throws -> VupLoanEligibilityResponse { try await get("api/v1/loans/vup/eligibility") }

    // Real BRD higher-education student loan -- see StudentLoanDto's own doc comment.
    // No Idempotency-Key on apply/declareGraduated (neither is money movement itself,
    // matching the backend's own contract); disburse/repay both require one, same
    // convention as every other money-moving call in this file.
    public func applyForStudentLoan(level: String, declaredAnnualHouseholdIncome: Double, amount: Double, expectedGraduationDate: String) async throws -> StudentLoanResponse {
        try await authenticatedPost("api/v1/loans/student/apply", body: ApplyForStudentLoanRequest(level: level, declaredAnnualHouseholdIncome: declaredAnnualHouseholdIncome, amount: amount, expectedGraduationDate: expectedGraduationDate))
    }

    public func disburseStudentLoan(loanId: String) async throws -> StudentLoanResponse {
        try await authenticatedPost("api/v1/loans/student/\(loanId)/disburse", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func declareStudentLoanGraduated(loanId: String) async throws -> StudentLoanResponse {
        try await authenticatedPost("api/v1/loans/student/\(loanId)/declare-graduated", body: EmptyBody())
    }

    public func repayStudentLoan(loanId: String, amount: Double) async throws -> StudentLoanResponse {
        try await authenticatedPost("api/v1/loans/student/\(loanId)/repay", body: RepayStudentLoanRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func getMyStudentLoans() async throws -> StudentLoansResponse { try await get("api/v1/loans/student/my") }

    public func getStudentLoanSuggestedPayment(loanId: String) async throws -> StudentLoanSuggestedPaymentResponse { try await get("api/v1/loans/student/\(loanId)/suggested-payment") }

    public func getVupLoan(loanId: String) async throws -> VupLoanResponse { try await get("api/v1/loans/vup/\(loanId)") }

    // Real Rwanda moto-taxi ownership savings-to-loan plan -- see
    // MotoOwnershipPlanDto's own doc comment. No Idempotency-Key on create (not
    // money movement itself, matching the backend's own contract); contribute/
    // cancel/convert-to-loan/repay all require one, same convention as every other
    // money-moving call in this file.
    public func createMotoOwnershipPlan(bikePrice: Double, dailyContribution: Double) async throws -> MotoOwnershipPlanResponse {
        try await authenticatedPost("api/v1/moto-ownership/plans", body: CreateMotoOwnershipPlanRequest(bikePrice: bikePrice, dailyContribution: dailyContribution))
    }

    public func contributeToMotoOwnershipPlan(planId: String, amount: Double) async throws -> MotoOwnershipPlanResponse {
        try await authenticatedPost("api/v1/moto-ownership/plans/\(planId)/contribute", body: ContributeToMotoOwnershipPlanRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func cancelMotoOwnershipPlan(planId: String) async throws -> MotoOwnershipPlanResponse {
        try await authenticatedPost("api/v1/moto-ownership/plans/\(planId)/cancel", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func convertMotoOwnershipPlanToLoan(planId: String) async throws -> MotoOwnershipPlanResponse {
        try await authenticatedPost("api/v1/moto-ownership/plans/\(planId)/convert-to-loan", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func repayMotoOwnershipPlan(planId: String, amount: Double) async throws -> MotoOwnershipPlanResponse {
        try await authenticatedPost("api/v1/moto-ownership/plans/\(planId)/repay", body: RepayMotoOwnershipPlanRequest(amount: amount), idempotencyKey: UUID().uuidString)
    }

    public func getMyMotoOwnershipPlans() async throws -> MotoOwnershipPlansResponse { try await get("api/v1/moto-ownership/plans/me") }

    public func getMotoOwnershipPlan(planId: String) async throws -> MotoOwnershipPlanResponse { try await get("api/v1/moto-ownership/plans/\(planId)") }

    func authenticatedPut<Body: Encodable, Response: Decodable>(_ path: String, body: Body) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return try decoder.decode(Response.self, from: data)
    }

    // Same shape as authenticatedPut, real PATCH method -- first real caller is
    // updateListingPrice below (backend uses @PatchMapping, not @PutMapping).
    func authenticatedPatch<Body: Encodable, Response: Decodable>(_ path: String, body: Body) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return try decoder.decode(Response.self, from: data)
    }

    // Real photo upload (item 152-adjacent, added 2026-08-01) -- see UploadController.kt's
    // own doc comment: POST /api/v1/uploads (multipart, 5MB cap, JPEG/PNG/WebP only)
    // stores real files on itunda-dc-a and hands back a URL any "photoUrl"/"documentUrl"
    // field can use. Android's uploadPhoto (ApiService.kt) already proved this real
    // pipeline out for the property-ownership-verification and marketplace-listing-photo
    // flows; this is the first iOS client, closing an honest "documentUrl text field, not
    // a real photo picker" scope-down this session's own ownership-verification port
    // (2026-07-31) named directly.
    public func uploadPhoto(data: Data, filename: String, mimeType: String) async throws -> UploadResponse {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: baseURL.appendingPathComponent("api/v1/uploads"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (responseData, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return try decoder.decode(UploadResponse.self, from: responseData)
    }

    private func postYouthAccount<Body: Encodable, Response: Decodable>(_ path: String, body: Body) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            if let errorBody = try? decoder.decode(ApiErrorBody.self, from: data) {
                switch errorBody.code {
                case "YOUTH_ACCOUNT_BIRTH_DATE_REQUIRED": throw NetworkError.youthAccountBirthDateRequired
                case "YOUTH_ACCOUNT_AGE_INELIGIBLE": throw NetworkError.youthAccountAgeIneligible
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

    func get<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await dataWithRefresh(for: request)
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
    func get<Response: Decodable>(_ path: String, query: [URLQueryItem]) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.filter { $0.value != nil && !($0.value!.isEmpty) }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Authenticated POST, with an optional Idempotency-Key -- every money-moving
    /// call below needs one so a retried tap after a timeout replays the original
    /// result instead of double-spending, same contract as Android's equivalent.
    func authenticatedPost<Body: Encodable, Response: Decodable>(
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
        let (data, response) = try await dataWithRefresh(for: request)
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

    // Real gap found 2026-09-04 (device step-up, while auditing the shared
    // DeviceStepUpHost's own generic-error catch block): verifyDevice went through
    // the plain authenticatedPost above, so DeviceStepUpHost.swift's catch block
    // could only tell 400 apart from everything else, hardcoding "Incorrect
    // password." for every 400 even though DeviceService.verifyDevice's OTHER 400
    // ("This session has no device id to verify") is a completely different, real
    // cause -- and collapsing the real 429 rate-limit message ("Too many attempts,
    // please try again later") and 404 ("Device not found") into one generic
    // "Something went wrong" bucket, on a component shared by Gift/Commerce/Eats/
    // Stocks step-up, not just this one screen. Same additive-function shape as
    // postP2p/authenticatedPutWithMessage -- never widen the plain authenticatedPost
    // used by ~230 other unaudited callers.
    func authenticatedPostWithMessage<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            let message = try? decoder.decode(ApiErrorBody.self, from: data).message
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: message ?? nil)
        }
        return try decoder.decode(Response.self, from: data)
    }

    // Real gap found 2026-09-04 (Eats checkout, while adding a new backend validation
    // -- InvalidEatsOrderChargeException for a pickup+promotion discount-stacking
    // fund-safety fix): placeEatsOrder/placeDineInOrder both went through the shared
    // authenticatedPost above, which only ever throws the bare, message-less
    // NetworkError.httpError -- so EatsCheckout.swift's catch block fell back to
    // TalkScreen.errorMessage's generic per-status-code bucket for EVERY 4xx/422 from
    // either endpoint, including the pre-existing MinOrderAmountNotMetException,
    // MenuItemSoldOutException, MissingRequiredMenuOptionException, etc. -- all of
    // which the backend already sends a specific, real message for. A 422 specifically
    // showed "Insufficient funds for this order." regardless of the real cause, which
    // is actively WRONG for a discount-stacking rejection, not just generic.
    // Dedicated function rather than widening authenticatedPost itself, same
    // "additive, narrow-scoped" precedent as postP2p/authenticatedPostWithCode just
    // below/above -- authenticatedPost's message-less throw is a deliberate, load-
    // bearing choice for its other 230+ callers (see NetworkError.httpErrorWithMessage's
    // own doc comment), not something to change wholesale.
    func postEatsOrder<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        idempotencyKey: String
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            let message = try? decoder.decode(ApiErrorBody.self, from: data).message
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: message ?? nil)
        }
        return try decoder.decode(Response.self, from: data)
    }

    // Real gap found 2026-09-04 (same iOS error-message-gap pass as postEatsOrder
    // above): depositToGoal/withdrawFromGoal went through the shared authenticatedPost,
    // so TransferViewModel's catch blocks fell back to the generic per-status-code
    // bucket for every SavingsService validation failure -- including
    // InsufficientGoalBalanceException's own dynamic "Cannot withdraw more than this
    // goal's current balance ($X)" message (a real number a generic bucket could never
    // reproduce), "Amount must be positive" (shared by both deposit and withdraw), and
    // GoalAlreadyCompletedException's "This goal has already reached its target".
    // Dedicated function rather than widening authenticatedPost itself, same rationale
    // as postEatsOrder just above.
    func postSavingsGoal<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        idempotencyKey: String
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            let message = try? decoder.decode(ApiErrorBody.self, from: data).message
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: message ?? nil)
        }
        return try decoder.decode(Response.self, from: data)
    }

    // Real gap found 2026-09-04 (same pass): boostListing went through the shared
    // authenticatedPost, so HoodMarketplaceCard's catch block fell back to the generic
    // bucket for InvalidBoostDurationException's real "Choose a real boost duration --
    // X days" message (which names the actual valid durations) instead of a vague
    // "Please check what you entered and try again." Dedicated function rather than
    // widening authenticatedPost itself, same rationale as postEatsOrder above.
    func postListingAction<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        idempotencyKey: String
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            let message = try? decoder.decode(ApiErrorBody.self, from: data).message
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: message ?? nil)
        }
        return try decoder.decode(Response.self, from: data)
    }

    // See NetworkError.httpErrorWithCode's own doc comment -- a dedicated function for
    // the handful of endpoints whose 409s are genuinely ambiguous without the real
    // code, same "additive, narrow-scoped" precedent as authenticatedPutWithMessage/
    // postP2p rather than widening authenticatedPost for every caller.
    func authenticatedPostWithCode<Body: Encodable, Response: Decodable>(
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
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            let errorBody = try? decoder.decode(ApiErrorBody.self, from: data)
            // Same real device-binding check as authenticatedPost above -- claimGift
            // (the one caller of this function that also carries an Idempotency-Key)
            // still needs it, so this mustn't regress that flow.
            if idempotencyKey != nil, httpResponse.statusCode == 403, errorBody?.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            throw NetworkError.httpErrorWithCode(statusCode: httpResponse.statusCode, code: errorBody?.code, message: errorBody?.message)
        }
        return try decoder.decode(Response.self, from: data)
    }

    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
    // setAccountPin's own doc comment for why this exists instead of reusing
    // authenticatedPut, same real reasoning as postP2p just below.
    func authenticatedPutWithMessage<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            let message = try? decoder.decode(ApiErrorBody.self, from: data).message
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: message ?? nil)
        }
        return try decoder.decode(Response.self, from: data)
    }

    // Dedicated request path for sendDirect/payP2pRequest only -- see
    // NetworkError.httpErrorWithMessage's own doc comment for why this doesn't reuse
    // authenticatedPost. Mirrors postYouthAccount's own precedent (a dedicated function
    // scoped to the one flow that needs to decode extra fields) rather than widening a
    // shared helper every other endpoint also calls.
    fileprivate func postP2p<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        idempotencyKey: String
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 403,
               let errorBody = try? decoder.decode(ApiErrorBody.self, from: data),
               errorBody.code == "DEVICE_NOT_VERIFIED" {
                throw NetworkError.deviceNotVerified
            }
            let message = try? decoder.decode(ApiErrorBody.self, from: data).message
            throw NetworkError.httpErrorWithMessage(statusCode: httpResponse.statusCode, message: message ?? nil)
        }
        return try decoder.decode(Response.self, from: data)
    }
}

struct ApiErrorBody: Decodable { let code: String?; let message: String? }

// Mirrors services/backend/account's AccountController.kt/TransferQuote.kt and
// services/backend/savings's SavingsController.kt exactly (2026-07-12) -- wires
// the real send-money and savings deposit/claim flows, same DTOs Android's
// ApiService.kt just gained.
public struct QuoteTransferRequest: Encodable {
    public let amount: Double
    public let recipient: String
}

public struct TransferQuoteDto: Decodable {
    public let id: String
    public let fromAccountId: String
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
// itunda user's account, even when the recipient is a real itunda account (confirmed via
// a direct MySQL check while building the real fix on the backend one day earlier). This
// is the real one -- no quote step needed, since there's no external rail decision to
// quote.
// fromAccountId added 2026-08-31 (direct user reference of their own Toss app's
// "which account should the money come from" picker) -- nil keeps the existing
// default-to-MAIN backend behavior for every pre-existing caller unchanged.
public struct SendDirectP2pRequest: Encodable { public let recipient: String; public let amount: Double; public let description: String; public let fromAccountId: String? }

// Real fixed-amount person-to-person payment request (item 171) -- the P2P
// counterpart to a merchant's own PaymentIntent (see backend P2pPaymentRequest.kt's
// own doc comment). A real 15-minute-expiring code the requester shares; anyone who
// has the code can pay it directly, real account-to-account, no fee. Real (rate-limited,
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
    // Real, friendly post-send fraud-heuristic warnings (2026-09-02, Toss security
    // research thread) -- P2pService.sendDirect's own doc comment confirms this is
    // always present (an empty array, never omitted/null), matching Android/bank-mfe's
    // identical field. Purely informational: the transfer this is attached to has
    // already completed, matching Toss's own real post-payment FDS notice.
    public let fraudWarnings: [String]
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

// Real Korean 지연이체서비스 (Delayed Transfer Service) -- see backend
// P2pDelayedTransfer.kt's own doc comment for the full sourced account (a real,
// government-documented anti-voice-phishing safeguard every major Korean bank
// offers: KakaoBank/Toss/IBK/KB all let a sender opt to hold an outgoing transfer
// for a real minimum window instead of it landing instantly). Genuinely distinct
// from a scheduled/future-dated transfer (a user-chosen FUTURE send date, which
// this codebase has no iOS client for either): this is a SAFETY delay on a
// transfer the sender wants to send right now. Fully built on the backend and
// already had a real web client (lib/delayedTransfers.ts) but no iOS client at
// all until now.
public enum DelayedTransferStatus: String, Codable { case PENDING, COMPLETED, CANCELLED }
public struct DelayedTransferDto: Decodable, Identifiable {
    public let id: String
    public let senderUserId: String
    public let recipientUserId: String
    public let amount: Double
    public let description: String
    public let status: DelayedTransferStatus
    public let releaseAt: String
    public let createdAt: String
}
public struct SendDelayedTransferRequest: Encodable {
    public let recipient: String
    public let amount: Double
    public let description: String
}
public struct DelayedTransferResponse: Decodable { public let success: Bool; public let transfer: DelayedTransferDto }
public struct DelayedTransfersListResponse: Decodable { public let success: Bool; public let transfers: [DelayedTransferDto] }

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

// Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인") -- see
// P2pService.resolveRecipient's own doc comment. Already had real web (bank-mfe) and
// Android clients; iOS had none until this (2026-08-23, found while porting the same
// real Toss "Sent" success-screen reference screenshot to iOS) -- meaning iOS's
// transfer flow only ever showed the raw account number, never a resolved name.
public struct P2pRecipientPreviewDto: Decodable { public let recipientUserId: String; public let displayName: String }
public struct ResolveRecipientResponse: Decodable { public let success: Bool; public let recipient: P2pRecipientPreviewDto }

// Real Toss Bank "Transfer limit" row (2026-09-01) -- mirrors web's lib/p2p.ts
// fetchTransferLimit / Android's TransferLimitResponse / backend P2pController's
// GET /api/v1/p2p/transfer-limit.
public struct TransferLimitResponse: Decodable {
    public let success: Bool
    public let perTransferLimit: Double
    public let dailyLimit: Double
    public let remainingToday: Double
}

public struct DepositRequest: Encodable { public let goalId: String; public let amount: Double }
public struct DepositResponse: Decodable { public let success: Bool; public let message: String; public let goal: SavingsGoal }
// Real gap found live (2026-08-31, direct user re-reference of the real Toss
// "얼마나 꺼낼까요?" (withdraw) screenshot) -- see backend SavingsService
// .withdrawFromGoal's own doc comment for the full account.
public struct WithdrawRequest: Encodable { public let goalId: String; public let amount: Double }
public struct WithdrawResponse: Decodable { public let success: Bool; public let message: String; public let goal: SavingsGoal }
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
    // Real field this struct was missing (2026-09-01, same real gap found in bank-mfe's
    // TrustedDevice TS type) -- TrustedDevice.kt's real publicKey column was already in
    // every real response, just undeclared here. Non-nil iff this device completed real
    // biometric/passwordless device-key registration (DeviceService.registerDeviceKey).
    public let publicKey: String?
}
public struct DevicesResponse: Decodable { public let success: Bool; public let devices: [TrustedDeviceDto] }
public struct VerifyDeviceRequest: Encodable { public let password: String }
public struct VerifyDeviceResponse: Decodable { public let success: Bool; public let device: TrustedDeviceDto }
public struct RevokeDeviceResponse: Decodable { public let success: Bool }

// Real Keystore/Secure-Enclave-signed-challenge device verification (item 246) -- see
// AuthController.kt's own doc comments on the backend for the exact contract. Same
// password re-proof as VerifyDeviceRequest above, plus a hardware-backed key generated
// by DeviceKeyManager -- makes every FUTURE step-up a biometric prompt instead of
// retyping a password.
public struct RegisterDeviceKeyRequest: Encodable { public let publicKey: String; public let password: String }
public struct DeviceChallengeResponse: Decodable { public let success: Bool; public let challenge: String }
public struct VerifyDeviceSignatureRequest: Encodable { public let signature: String }

// Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
// NetworkClient.loginDeviceChallenge/loginWithDeviceSignature's own doc comment.
public struct LoginDeviceChallengeRequest: Encodable { public let phoneNumber: String; public let deviceId: String
    public init(phoneNumber: String, deviceId: String) { self.phoneNumber = phoneNumber; self.deviceId = deviceId }
}
public struct LoginDeviceChallengeResponse: Decodable { public let success: Bool; public let challenge: String }
public struct LoginWithDeviceSignatureRequest: Encodable { public let phoneNumber: String; public let deviceId: String; public let signature: String
    public init(phoneNumber: String, deviceId: String, signature: String) { self.phoneNumber = phoneNumber; self.deviceId = deviceId; self.signature = signature }
}
public struct SetAccountPinRequest: Encodable { public let currentCredential: String; public let newPin: String }
public struct SetAccountPinResponse: Decodable { public let success: Bool; public let user: PublicUser }

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
        try await authenticatedPostWithMessage("api/v1/auth/devices/verify", body: VerifyDeviceRequest(password: password))
    }

    public func revokeDevice(deviceId: String) async throws -> RevokeDeviceResponse {
        try await authenticatedDelete("api/v1/auth/devices/\(deviceId)")
    }

    public func registerDeviceKey(publicKey: String, password: String) async throws -> VerifyDeviceResponse {
        try await authenticatedPostWithMessage("api/v1/auth/devices/register-key", body: RegisterDeviceKeyRequest(publicKey: publicKey, password: password))
    }

    public func issueDeviceChallenge() async throws -> DeviceChallengeResponse {
        try await authenticatedPost("api/v1/auth/devices/challenge", body: EmptyBody())
    }

    @discardableResult
    public func recordAnalyticsEvent(_ eventName: String, metadata: String? = nil) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/analytics/events", body: RecordAnalyticsEventRequest(eventName: eventName, metadata: metadata))
    }

    // Real, minimal fire-and-forget analytics helper (2026-08-10) -- same "a failed
    // best-effort call must never surface to the user or block the real action it's
    // attached to" reasoning this file's own registerDeviceToken already establishes.
    public func recordAnalyticsEventBestEffort(_ eventName: String, metadata: String? = nil) {
        Task { try? await recordAnalyticsEvent(eventName, metadata: metadata) }
    }

    public func verifyDeviceSignature(signature: String) async throws -> VerifyDeviceResponse {
        try await authenticatedPost("api/v1/auth/devices/verify-signature", body: VerifyDeviceSignatureRequest(signature: signature))
    }

    public func registerDeviceToken(_ request: RegisterDeviceTokenRequest) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/notifications/device-tokens", body: request)
    }

    /// Real push unregister-on-logout (item 232) -- found via a defined-but-uncalled-
    /// endpoint sweep, see backend DeviceTokenController.unregister's own doc comment.
    /// bank-mfe/Android shipped this first (2026-08-05); this is the iOS port. Called
    /// from SessionManager.logout() before store.clearSession() -- authenticatedDelete
    /// reads the still-valid keychain token at call time, same ordering Android's
    /// explicit-header version needed for the same reason.
    public func unregisterDeviceToken(_ token: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/notifications/device-tokens/\(token)")
    }
}

extension NetworkClient {
    public func quoteTransfer(amount: Double, recipient: String) async throws -> QuoteTransferResponse {
        try await authenticatedPost("api/v1/account/transfer/quote", body: QuoteTransferRequest(amount: amount, recipient: recipient))
    }

    public func confirmTransfer(quoteId: String) async throws -> ConfirmTransferResponse {
        try await authenticatedPost(
            "api/v1/account/transfer/confirm",
            body: ConfirmTransferRequest(quoteId: quoteId),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real direct P2P push-transfer (2026-07-20) -- see SendDirectP2pRequest's own doc
    // comment for why this replaces quoteTransfer/confirmTransfer above in
    // TransferViewModel.sendTransfer.
    public func sendDirect(recipient: String, amount: Double, memo: String = "", fromAccountId: String? = nil) async throws -> SendDirectP2pResponse {
        try await postP2p(
            "api/v1/p2p/send",
            body: SendDirectP2pRequest(recipient: recipient, amount: amount, description: memo, fromAccountId: fromAccountId),
            idempotencyKey: UUID().uuidString
        )
    }

    public func generateP2pRequest(amount: Double, description: String) async throws -> GenerateP2pRequestResponse {
        try await authenticatedPost("api/v1/p2p/request", body: GenerateP2pRequest(amount: amount, description: description))
    }

    public func getMyP2pRequests() async throws -> GetP2pRequestsResponse { try await get("api/v1/p2p/requests") }

    public func payP2pRequest(requestId: String) async throws -> PayP2pRequestResponse {
        try await postP2p("api/v1/p2p/pay/\(requestId)", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    // Real Toss Bank 자동이체 (auto-transfer) equivalent (2026-07-24 port) -- see
    // AutoTransferDto's own doc comment above. pause/resume/cancel mutate an existing
    // row by id and stay idempotent without a key. create is different (item 235,
    // found via a periodic Idempotency-Key coverage audit, corrects this file's own
    // earlier reasoning above): a retry created a second active recurring-transfer
    // row to the same recipient, which the scheduler then executes independently -- a
    // real duplicate charge every period going forward. See backend
    // AutoTransferController.create's own doc comment for the full account.
    // Real gap found 2026-09-04: AutoTransferService.create shares P2pSelfPaymentException
    // with P2pService.sendDirect/sendDelayed -- "Auto-transfers need a different
    // recipient -- you can't send to yourself" was falling back to the generic
    // "That recipient, amount, or schedule isn't valid." text.
    public func createAutoTransfer(
        recipient: String, amount: Double, frequency: AutoTransferFrequency,
        dayOfWeek: Int?, dayOfMonth: Int?, description: String
    ) async throws -> AutoTransferResponse {
        try await postP2p(
            "api/v1/p2p/auto-transfers",
            body: CreateAutoTransferRequest(recipient: recipient, amount: amount, frequency: frequency, dayOfWeek: dayOfWeek, dayOfMonth: dayOfMonth, description: description),
            idempotencyKey: UUID().uuidString
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

    // Real Korean 지연이체서비스 (Delayed Transfer Service) -- see
    // DelayedTransferDto's own doc comment above. sendDelayed moves real money
    // immediately into a holding account, so it carries an Idempotency-Key the
    // same way every other money-moving call in this file does; cancel only
    // mutates an existing PENDING row by id and stays idempotent without one,
    // matching cancelAutoTransfer's own reasoning just above.
    public func getMyDelayedTransfers() async throws -> DelayedTransfersListResponse { try await get("api/v1/p2p/delayed-transfers") }

    // Real gap found 2026-09-04: sendDelayed shares P2pDelayedTransferService's exact
    // exception surface with P2pService.sendDirect (self-payment, transfer-limit,
    // recipient-not-found, insufficient-funds) -- sendDirect already gets real
    // messages via postP2p below, but this delayed twin was going through the plain
    // authenticatedPost, so DelayedTransferListScreen.swift's catch fell back to a
    // 4-status generic switch instead of the same real messages sendDirect shows.
    public func sendDelayed(recipient: String, amount: Double, description: String) async throws -> DelayedTransferResponse {
        try await postP2p(
            "api/v1/p2p/send-delayed",
            body: SendDelayedTransferRequest(recipient: recipient, amount: amount, description: description),
            idempotencyKey: UUID().uuidString
        )
    }

    public func cancelDelayedTransfer(_ id: String) async throws -> DelayedTransferResponse {
        try await authenticatedPost("api/v1/p2p/delayed-transfers/\(id)/cancel", body: EmptyBody())
    }

    public func checkScamStatus(identifier: String) async throws -> ScamCheckResponse {
        try await get("api/v1/p2p/scam-reports/check?identifier=\(identifier.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? identifier)")
    }

    public func resolveRecipient(identifier: String) async throws -> ResolveRecipientResponse {
        try await get("api/v1/p2p/recipient?identifier=\(identifier.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? identifier)")
    }

    public func getTransferLimit() async throws -> TransferLimitResponse {
        try await get("api/v1/p2p/transfer-limit")
    }

    public func reportScam(identifier: String, reason: String) async throws -> ScamReportResponse {
        try await authenticatedPost("api/v1/p2p/scam-reports", body: ReportScamRequest(identifier: identifier, reason: reason))
    }

    public func getMyScamReports() async throws -> ScamReportsListResponse { try await get("api/v1/p2p/scam-reports/mine") }

    public func depositToGoal(goalId: String, amount: Double) async throws -> DepositResponse {
        try await postSavingsGoal(
            "api/v1/savings/deposit",
            body: DepositRequest(goalId: goalId, amount: amount),
            idempotencyKey: UUID().uuidString
        )
    }

    public func withdrawFromGoal(goalId: String, amount: Double) async throws -> WithdrawResponse {
        try await postSavingsGoal(
            "api/v1/savings/withdraw",
            body: WithdrawRequest(goalId: goalId, amount: amount),
            idempotencyKey: UUID().uuidString
        )
    }

    public func claimInterest() async throws -> ClaimInterestResponse {
        try await postSavingsGoal(
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

struct EmptyBody: Encodable {}

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
    // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
    // .archived's own doc comment. Same private-to-me model as quiet.
    public let archived: Bool?
    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see backend
    // MessagingController's own doc comment distinguishing this from the existing
    // per-message pin (pinnedMessageId above). Already returned by GET /conversations
    // for every summary since MessagingController shipped it; bank-mfe wired a client
    // for it (Section 165) but iOS never read this field back until now. Same
    // defined-but-uncalled shape as archived above.
    public let pinnedToTop: Bool?
    // Real KakaoTalk-style "favorite" chat toggle (itunda Talk redesign, 2026-08-28)
    // -- see backend ConversationPreference.favorite's own doc comment. Already
    // returned by GET /conversations for every summary.
    public let favorite: Bool?
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
    // Real fix, found live 2026-08-05 while adding Thread support: this field was
    // declared but never actually decoded -- CodingKeys/init(from:) below never
    // mentioned it, so it silently read back as nil on every real message regardless
    // of what the backend sent, unlike Android's own MessageDto (which decodes it
    // correctly). Real reply context was invisibly broken on iOS this whole time.
    public let replyToMessageId: String?
    public let reactions: [ReactionGroupDto]
    // Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
    // recommendation #3's own account. A real, read-time-computed count of direct
    // replies to this message (0 for a message no one has replied to).
    public let replyCount: Int
    // Real KakaoTalk Emoticon Store (item 136) -- see EmoticonPackDto's own doc
    // comment. Only ever set on a message actually created via the real
    // /api/v1/emoticons/.../send endpoints.
    public let emoticonId: String?
    // Real photo message -- ports Android TalkScreen.kt's own identical addition
    // (2026-08-04) to iOS. Set only on a message sent with a real uploaded photo
    // (POST /api/v1/uploads -> imageUrl passed to sendMessage), never client-asserted.
    public let imageUrl: String?
    // Real message forwarding -- ports Android TalkScreen.kt's own identical addition
    // (2026-08-04) to iOS. A real provenance label, only ever set on a message
    // actually created via the forward endpoint, never client-asserted.
    public let forwardedFromMessageId: String?
    public let forwardedFromType: String?

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
        self.replyToMessageId = nil
        self.reactions = reactions
        self.replyCount = 0
        self.emoticonId = nil
        self.imageUrl = nil
        self.forwardedFromMessageId = nil
        self.forwardedFromType = nil
    }

    // Real fix (found 2026-08-05 while adding GroupMessageDto.unreadCount): applying a
    // real-time reaction update by rebuilding via the memberwise init above silently
    // dropped imageUrl/forwardedFromMessageId/forwardedFromType, since that init always
    // hardcodes them to nil -- a photo or forward label would flicker away for a
    // message the instant its reaction changed, until the next 4s poll refresh. This
    // preserves every field and only replaces reactions.
    public func withReactions(_ reactions: [ReactionGroupDto]) -> MessageDto {
        MessageDto(id: id, conversationId: conversationId, senderId: senderId, body: body, sentAt: sentAt, readAt: readAt, deletedAt: deletedAt, replyToMessageId: replyToMessageId, reactions: reactions, replyCount: replyCount, emoticonId: emoticonId, imageUrl: imageUrl, forwardedFromMessageId: forwardedFromMessageId, forwardedFromType: forwardedFromType)
    }

    private init(id: String, conversationId: String, senderId: String, body: String, sentAt: String, readAt: String?, deletedAt: String?, replyToMessageId: String?, reactions: [ReactionGroupDto], replyCount: Int, emoticonId: String?, imageUrl: String?, forwardedFromMessageId: String?, forwardedFromType: String?) {
        self.id = id
        self.conversationId = conversationId
        self.senderId = senderId
        self.body = body
        self.sentAt = sentAt
        self.readAt = readAt
        self.deletedAt = deletedAt
        self.replyToMessageId = replyToMessageId
        self.reactions = reactions
        self.replyCount = replyCount
        self.emoticonId = emoticonId
        self.imageUrl = imageUrl
        self.forwardedFromMessageId = forwardedFromMessageId
        self.forwardedFromType = forwardedFromType
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
        replyToMessageId = try container.decodeIfPresent(String.self, forKey: .replyToMessageId)
        reactions = try container.decodeIfPresent([ReactionGroupDto].self, forKey: .reactions) ?? []
        replyCount = try container.decodeIfPresent(Int.self, forKey: .replyCount) ?? 0
        emoticonId = try container.decodeIfPresent(String.self, forKey: .emoticonId)
        imageUrl = try container.decodeIfPresent(String.self, forKey: .imageUrl)
        forwardedFromMessageId = try container.decodeIfPresent(String.self, forKey: .forwardedFromMessageId)
        forwardedFromType = try container.decodeIfPresent(String.self, forKey: .forwardedFromType)
    }

    private enum CodingKeys: String, CodingKey { case id, conversationId, senderId, body, sentAt, readAt, deletedAt, replyToMessageId, reactions, replyCount, emoticonId, imageUrl, forwardedFromMessageId, forwardedFromType }
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
    public let imageUrl: String?
    public init(body: String, replyToMessageId: String? = nil, imageUrl: String? = nil) {
        self.body = body
        self.replyToMessageId = replyToMessageId
        self.imageUrl = imageUrl
    }
}
public struct TalkContactDto: Decodable, Identifiable { public let userId: String; public let name: String; public var id: String { userId } }
public struct TalkContactsResponse: Decodable { public let success: Bool; public let contacts: [TalkContactDto] }
public struct ConversationQuietResponse: Decodable { public let success: Bool; public let quiet: Bool }
// Real recoverable archive (2026-08-05) -- see backend ConversationPreference
// .archived's own doc comment.
public struct ConversationArchivedResponse: Decodable { public let success: Bool; public let archived: Bool }
public struct SetConversationArchivedRequest: Encodable { public let archived: Bool }
// Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see
// ConversationSummaryDto.pinnedToTop's own doc comment. Named ConversationPinnedToTop
// (not ConversationPinned) to stay distinct from the existing per-message
// pin/unpin(ConversationMessage) pair, same naming discipline bank-mfe's own
// lib/messaging.ts already established for this endpoint.
public struct ConversationPinnedToTopResponse: Decodable { public let success: Bool; public let pinned: Bool }
public struct SetConversationPinnedToTopRequest: Encodable { public let pinned: Bool }
public struct ConversationFavoriteResponse: Decodable { public let success: Bool; public let favorite: Bool }
public struct SetConversationFavoriteRequest: Encodable { public let favorite: Bool }
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
    public let imageUrl: String?
    public init(body: String, replyToMessageId: String? = nil, imageUrl: String? = nil) {
        self.body = body
        self.replyToMessageId = replyToMessageId
        self.imageUrl = imageUrl
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

// Real open-group DTOs -- distinct shape from GroupSummaryDto (a real joinCode,
// but no memberCount/lastMessage/etc. yet since the group was just created).
public struct CreateOpenGroupRequest: Encodable { public let name: String }
public struct OpenGroupDto: Decodable { public let id: String; public let name: String; public let joinCode: String }
public struct OpenGroupResponse: Decodable { public let success: Bool; public let group: OpenGroupDto }
public struct JoinGroupByCodeRequest: Encodable { public let joinCode: String }
public struct JoinedGroupDto: Decodable { public let id: String; public let name: String }
public struct JoinGroupResponse: Decodable { public let success: Bool; public let group: JoinedGroupDto }
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
    // Real photo message -- see MessageDto's own identical doc comment.
    public let imageUrl: String?
    // Real message forwarding -- see MessageDto's own identical doc comment.
    public let forwardedFromMessageId: String?
    public let forwardedFromType: String?
    // Real Kakao-style per-message unread countdown (backend since 2026-07-26, client
    // gap found 2026-08-05 via a doc-accuracy audit) -- see
    // GroupMessagingController.getMessages's own doc comment. Counts real members
    // whose lastReadAt is still before this message's sentAt; decrements live as
    // members open the thread.
    public let unreadCount: Int
    // Real Thread support (2026-08-05) -- see MessageDto.replyCount's own doc comment.
    public let replyCount: Int

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
        self.imageUrl = nil
        self.forwardedFromMessageId = nil
        self.forwardedFromType = nil
        self.unreadCount = 0
        self.replyCount = 0
    }

    // Real fix -- see MessageDto's own identical withReactions doc comment; same bug,
    // same fix, for the group side (also would have dropped the new unreadCount).
    public func withReactions(_ reactions: [ReactionGroupDto]) -> GroupMessageDto {
        GroupMessageDto(id: id, groupConversationId: groupConversationId, senderId: senderId, body: body, sentAt: sentAt, deletedAt: deletedAt, replyToMessageId: replyToMessageId, reactions: reactions, emoticonId: emoticonId, imageUrl: imageUrl, forwardedFromMessageId: forwardedFromMessageId, forwardedFromType: forwardedFromType, unreadCount: unreadCount, replyCount: replyCount)
    }

    private init(id: String, groupConversationId: String, senderId: String, body: String, sentAt: String, deletedAt: String?, replyToMessageId: String?, reactions: [ReactionGroupDto], emoticonId: String?, imageUrl: String?, forwardedFromMessageId: String?, forwardedFromType: String?, unreadCount: Int, replyCount: Int) {
        self.id = id
        self.groupConversationId = groupConversationId
        self.senderId = senderId
        self.body = body
        self.sentAt = sentAt
        self.deletedAt = deletedAt
        self.replyToMessageId = replyToMessageId
        self.reactions = reactions
        self.emoticonId = emoticonId
        self.imageUrl = imageUrl
        self.forwardedFromMessageId = forwardedFromMessageId
        self.forwardedFromType = forwardedFromType
        self.unreadCount = unreadCount
        self.replyCount = replyCount
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
        imageUrl = try container.decodeIfPresent(String.self, forKey: .imageUrl)
        forwardedFromMessageId = try container.decodeIfPresent(String.self, forKey: .forwardedFromMessageId)
        forwardedFromType = try container.decodeIfPresent(String.self, forKey: .forwardedFromType)
        unreadCount = try container.decodeIfPresent(Int.self, forKey: .unreadCount) ?? 0
        replyCount = try container.decodeIfPresent(Int.self, forKey: .replyCount) ?? 0
    }

    private enum CodingKeys: String, CodingKey { case id, groupConversationId, senderId, body, sentAt, deletedAt, replyToMessageId, reactions, emoticonId, imageUrl, forwardedFromMessageId, forwardedFromType, unreadCount, replyCount }
}
public struct GroupResponse: Decodable { public let success: Bool; public let group: GroupSummaryDto }
public struct GroupsResponse: Decodable { public let success: Bool; public let groups: [GroupSummaryDto] }
public struct GroupMessagesResponse: Decodable { public let success: Bool; public let messages: [GroupMessageDto] }
public struct GroupMessageResponse: Decodable { public let success: Bool; public let message: GroupMessageDto }
public struct PinnedGroupMessageResponse: Decodable { public let success: Bool; public let message: GroupMessageDto? }
// Real message forwarding -- ports Android TalkScreen.kt's own identical addition
// (2026-08-04) to iOS. Deliberately mirrors bank-mfe's own simpler
// {success, destinationType} response (not the full forwarded message) -- the picker
// only needs a success/fail signal, matching lib/messaging.ts's own forwardMessage.
public struct ForwardMessageRequest: Encodable { public let destinationType: String; public let destinationId: String }
public struct ForwardMessageResponse: Decodable { public let success: Bool; public let destinationType: String }
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

// ListingDto/CreateListingRequest moved to NetworkClient+Hood.swift (2026-08-28,
// itunda Hood redesign) -- extracted to make room for real 당근카 vehicle fields
// without pushing this already-at-baseline file over its frozen file-size-lint limit,
// same "extract, don't just trim" precedent NetworkClient+Maps.swift already set.
public struct ListingResponse: Decodable { public let success: Bool; public let listing: ListingDto }
public struct MarkSoldRequest: Encodable { public let buyerPhoneNumber: String? }
// Real seller-paid sponsored placement -- mirrors Android's ApiService.kt exactly.
public struct BoostListingRequest: Encodable {
    public let days: Int
    public init(days: Int) { self.days = days }
}
public struct UpdateListingPriceRequest: Encodable {
    public let price: Double
    public init(price: Double) { self.price = price }
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

// Real "pay via itunda" Marketplace escrow (backend since 2026-07-25) -- an opt-in
// safer alternative to the existing in-person cash handoff, never replacing it. Real
// gap found+closed 2026-08-15: this had existed on the backend and Android for weeks
// with ZERO client on iOS or bank-mfe (confirmed by grep). First iOS client, mirroring
// bank-mfe's own web client (built the same session) and Android's own real bug fix
// found while researching this: escrow status is fetched and shown for BOTH the buyer
// and seller of a SOLD listing (the real backend already allows both via
// MarketplaceService.getEscrow), not buyer-only -- otherwise a seller has no way to
// ever see the real delivery address a buyer typed in. deliveryAddress is real,
// optional (당근마켓 바로구매-style shipped-item support) -- leaving it blank keeps
// the original in-person handoff this feature has always assumed.
public struct MarketplaceEscrowDto: Decodable {
    public let id: String
    public let listingId: String
    public let buyerId: String
    public let sellerId: String
    public let amount: Double
    public let fee: Double
    public let status: String
    public let holdTransactionId: String
    public let resolutionTransactionId: String?
    public let disputeReason: String?
    public let deliveryAddress: String?
    public let createdAt: String
    public let updatedAt: String
}
public struct MarketplaceEscrowResponse: Decodable { public let success: Bool; public let escrow: MarketplaceEscrowDto }
public struct PayEscrowRequest: Encodable { public let deliveryAddress: String? }
public struct DisputeEscrowRequest: Encodable { public let reason: String }
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
// comment. Money leaves the sender's account into a real escrow account the moment a
// gift is sent, and only reaches the recipient's account once they explicitly claim it
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
public struct SendGiftRequest: Encodable { public let recipientPhoneNumber: String; public let amount: Double; public let note: String?; public let theme: String? }
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
    // Real 동네생활 topic chip + AI-generated 모임 summary (2026-08-28) -- see backend
    // CommunityPost.topic/aiSummary's own doc comments.
    public let topic: String?
    public let aiSummary: String?
    public let aiSummaryGeneratedAt: String?
    public init(id: String, authorId: String, category: String, title: String, body: String, status: String, likeCount: Int, commentCount: Int, createdAt: String, latitude: Double?, longitude: Double?, groupConversationId: String? = nil, eventDate: String? = nil, capacity: Int? = nil, topic: String? = nil, aiSummary: String? = nil, aiSummaryGeneratedAt: String? = nil) { self.id = id; self.authorId = authorId; self.category = category; self.title = title; self.body = body; self.status = status; self.likeCount = likeCount; self.commentCount = commentCount; self.createdAt = createdAt; self.latitude = latitude; self.longitude = longitude; self.groupConversationId = groupConversationId; self.eventDate = eventDate; self.capacity = capacity; self.topic = topic; self.aiSummary = aiSummary; self.aiSummaryGeneratedAt = aiSummaryGeneratedAt }
}
public struct CreateCommunityPostRequest: Encodable {
    public let category: String; public let title: String; public let body: String
    public let latitude: Double?; public let longitude: Double?
    public let eventDate: String?; public let capacity: Int?; public let topic: String?
}
public struct CommunityPostResponse: Decodable { public let success: Bool; public let post: CommunityPostDto }
// joinedCounts added 2026-07-24 -- postId -> real member count of that meetup's group
// chat, closing docs/DESIGN_REFERENCES.md Section 4 recommendation #4.
public struct CommunityPostsResponse: Decodable { public let success: Bool; public let posts: [CommunityPostDto]; public let joinedCounts: [String: Int]? }
public struct CommunityCategoriesResponse: Decodable { public let success: Bool; public let categories: [CommunityCategoryDto] }
// Real Karrot 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) -- ported
// from bank-mfe (2026-09-03). Scoped to MY posts only.
public struct CommentNotificationsEnabledResponse: Decodable { public let success: Bool; public let commentNotificationsEnabled: Bool }
public struct SetCommentNotificationsEnabledRequest: Encodable { public let enabled: Bool }
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
    // Real résumé attach at submission time (2026-08-28) -- see backend
    // JobApplication.resumeSnapshotJson's own doc comment. Only ever checked for
    // presence to show "Résumé attached" -- never parsed/re-rendered here.
    public let resumeSnapshotJson: String?
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
    // Real hyperlocal neighborhood -- the backend has stamped this on every listing
    // since 2026-07-20 and serializes the entity directly; iOS never had this field.
    // Needed to key the real 살아본 후기 (neighborhood-lived reviews) section.
    public let neighborhood: String?
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

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see the backend's
// PropertyListingService.estimateValue doc comment. Read-only, computed fresh from
// real comparable listings on every call, nothing persisted.
public struct PropertyValuationEstimateDto: Decodable {
    public let estimatedValue: Double; public let comparableCount: Int; public let averagePricePerSqm: Double; public let radiusKm: Double
}
public struct PropertyValuationResponse: Decodable { public let success: Bool; public let estimate: PropertyValuationEstimateDto }
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
    // Real merchant-set phone/hours (2026-08-09) -- see Merchant.kt's own doc comment on
    // the backend. Nil unless the merchant has actually set one. DTO field only this
    // pass -- MapScreenView's own detail sheet doesn't yet have the richer photo/rating/
    // category card Android and bank-mfe already got (a real, separate, larger gap:
    // that infrastructure doesn't exist on iOS's Maps screen at all yet, not just these
    // 2 fields), tracked honestly rather than silently left out.
    public let phoneNumber: String?
    public let openingHours: String?
    // Real per-restaurant WOW membership badge + max-active-discount (2026-08-28,
    // see backend Merchant.participatesInEatsMembership / getMaxDiscountByMerchantIds).
    public let participatesInEatsMembership: Bool
    public let maxDiscountPercent: Int?
    public var id: String { merchantId }

    public init(
        merchantId: String, businessName: String, category: String?, cashbackRate: String, latitude: Double? = nil, longitude: Double? = nil,
        photoUrl: String? = nil, minOrderAmount: Double? = nil, rating: Double? = nil, reviewCount: Int? = nil,
        distanceKm: Double? = nil, deliveryTimeMinutes: Int? = nil, phoneNumber: String? = nil, openingHours: String? = nil,
        participatesInEatsMembership: Bool = false, maxDiscountPercent: Int? = nil
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
        self.phoneNumber = phoneNumber
        self.openingHours = openingHours
        self.participatesInEatsMembership = participatesInEatsMembership
        self.maxDiscountPercent = maxDiscountPercent
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
    // rating/reviewCount added 2026-08-25 -- real batched ProductReview data (see
    // ShoppingController.getDeals' own doc comment), same field this app's Android
    // client just added. nil rating means genuinely zero reviews yet -- render no
    // stars, never a fabricated default.
    public let rating: Double?
    public let reviewCount: Int?
    // Real "Best seller" badge (2026-08-28) -- a genuine, derived signal (real gross
    // order count per product, see backend ShoppingController.bestSellerProductIds'
    // own doc comment), not fabricated marketing copy. Always present in the real
    // JSON (Kotlin's `false` default still serializes), so non-optional.
    public let isBestSeller: Bool
}
public struct DealsResponse: Decodable { public let success: Bool; public let products: [DealProductDto] }
public struct MembershipDayStatusResponse: Decodable { public let success: Bool; public let isMembershipDay: Bool; public let multiplier: Double }

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
// ported from bank-mfe/Android (2026-09-03), see backend ShoppingMissionService's own
// doc comment. Every mission pays real RWF straight into the real account -- itunda
// has never had a separate "points" currency.
public struct ShoppingMissionDto: Decodable { public let type: String; public let label: String; public let rewardAmount: Double; public let completedToday: Bool; public let claimedEver: Bool }
public struct SpinOutcomeDto: Decodable { public let amount: Double; public let odds: Double }
public struct ShoppingMissionsResponse: Decodable { public let success: Bool; public let missions: [ShoppingMissionDto]; public let spinOutcomes: [SpinOutcomeDto] }
public struct MissionCompleteResponse: Decodable { public let success: Bool; public let type: String; public let amountEarned: Double; public let newAccountBalance: Double }

// Real Coupang Eats-style dish grid (itunda Eats redesign, 2026-08-28) -- see
// backend EatsDishRecommendationService.getDishes' own doc comment. Fully built on
// the backend since 2026-08-03 (Android already ported it); this is the first iOS
// client, found via a real grep sweep showing zero iOS caller anywhere. recommended
// is real (has the buyer actually ordered from this restaurant before), never
// fabricated.
public struct EatsDishDto: Decodable, Identifiable {
    public let id: String
    public let merchantId: String
    public let merchantName: String
    public let name: String
    public let price: Double
    public let imageUrl: String?
    public let recommended: Bool
}
public struct EatsDishesResponse: Decodable { public let success: Bool; public let dishes: [EatsDishDto] }

// Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28)
// -- see backend OrderItemRepository.getFrequentlyOrderedWith's own doc comment: a
// real, derived co-occurrence signal, never a fabricated pairing.
public struct FrequentlyOrderedWithItemDto: Decodable, Identifiable {
    public let id: String
    public let merchantId: String
    public let merchantName: String
    public let name: String
    public let price: Double
    public let imageUrl: String?
    public let originalPrice: Double?
    public let discountPercent: Int?
    public let stockQuantity: Int?
}
public struct FrequentlyOrderedWithResponse: Decodable { public let success: Bool; public let products: [FrequentlyOrderedWithItemDto] }

// Real Coupang 타임특가 (Time Deal, item 226) -- see the backend TimeDeal.kt's own doc
// comment. A time-boxed, quantity-capped discount OVERLAY on an existing product,
// distinct from DealProductDto above (a permanent discount, not a scheduled event).
// bank-mfe/Android already have this; this is the first iOS client (consumer browse
// only -- merchant creation lives on merchant-mfe, matching this app's existing
// consumer/merchant app split).
public struct TimeDealDto: Decodable {
    public let id: String
    public let merchantId: String
    public let productId: String
    public let dealPrice: Double
    public let originalPrice: Double
    public let totalQuantity: Int
    public let remainingQuantity: Int
    public let startsAt: String
    public let endsAt: String
    public let createdAt: String
}
public struct TimeDealViewDto: Decodable, Identifiable {
    public let deal: TimeDealDto
    public let productName: String
    public let productImageUrl: String?
    public let businessName: String
    public var id: String { deal.id }
}
public struct TimeDealsResponse: Decodable { public let success: Bool; public let deals: [TimeDealViewDto] }

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
// Android's ProductSubscriptionDto doc comment. bank-mfe already had this; found
// 2026-08-01 with zero client on Android/iOS despite that.
public struct ProductSubscriptionDto: Decodable, Identifiable {
    public let id: String; public let merchantId: String; public let productId: String; public let quantity: Int; public let intervalDays: Int
    public let deliveryAddress: String; public let status: String; public let nextDeliveryAt: String; public let createdAt: String
    public let lastDeliveredAt: String?; public let deliveryCount: Int; public let lastFailureReason: String?; public let cancelledAt: String?
}
public struct CreateProductSubscriptionRequest: Encodable { public let merchantId: String; public let productId: String; public let quantity: Int; public let intervalDays: Int; public let deliveryAddress: String }
public struct ProductSubscriptionResponse: Decodable { public let success: Bool; public let subscription: ProductSubscriptionDto }
public struct ProductSubscriptionsResponse: Decodable { public let success: Bool; public let subscriptions: [ProductSubscriptionDto] }

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
// isPublic added 2026-08-18 (ported from Android/bank-mfe's own real Naver Map-style
// public/private folder + share) -- see MapBookmark.isPublic's own doc comment on the
// backend. Always present in the real JSON (Kotlin's `= false` default still serializes
// the field), so this is a plain non-optional Bool, not a decode-time fallback.
// isPublic is `var`, not `let` -- this struct's memberwise init is only `internal`
// (Swift never auto-synthesizes a `public` one across a module boundary, only
// `init(from:)` via the Decodable conformance), so a caller in the App module can't
// reconstruct a whole new value the way `let` would require; mutating a copy's `var`
// field is the real, minimal way to update this one field from outside CoreNetwork.
public struct MapBookmarkDto: Decodable, Identifiable { public let id: String; public let displayName: String; public let latitude: Double; public let longitude: Double; public let folderName: String; public let color: String; public var isPublic: Bool; public let createdAt: String }
public struct MapBookmarksResponse: Decodable { public let success: Bool; public let bookmarks: [MapBookmarkDto] }
public struct AddMapBookmarkRequest: Encodable { public let displayName: String; public let latitude: Double; public let longitude: Double; public let folderName: String?; public let color: String? }
public struct AddMapBookmarkResponse: Decodable { public let success: Bool; public let bookmark: MapBookmarkDto }
// Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc comment.
public struct MoveMapBookmarkRequest: Encodable { public let folderName: String; public let color: String }
public struct MoveMapBookmarkResponse: Decodable { public let success: Bool; public let bookmark: MapBookmarkDto }
// Real Naver Map-style public/private folder + share, and Kakao Map-style "구독"
// (subscribe) -- ported from Android/bank-mfe (2026-08-18). iOS previously had no
// client for any of this: no isPublic field, no share toggle, no shared-link view, no
// subscribe -- Android/bank-mfe's own share links have always silently 404'd on iOS.
public struct SetMapFolderVisibilityRequest: Encodable { public let folderName: String; public let isPublic: Bool }
public struct SetMapFolderVisibilityResponse: Decodable { public let success: Bool; public let updatedCount: Int }
public struct SharedMapFolderResponse: Decodable { public let success: Bool; public let bookmarks: [MapBookmarkDto] }
public struct SubscribeToSharedMapFolderResponse: Decodable { public let success: Bool; public let copiedCount: Int }

// Real Kakao Map-style "친구위치" (Friend Location) live location sharing -- a real,
// moving position shared for a bounded window, distinct from the static bookmark-folder
// share/subscribe above. Ported from bank-mfe/maps-mfe (2026-09-03) -- itunda's own v1 is
// ALWAYS time-bounded (no "unlimited" option); "live" means periodically-refreshed via
// polling, not a push channel (itunda has no WebSocket infra for this feature specifically).
public struct LiveLocationShareDto: Decodable, Identifiable {
    public let id: String; public let sharerUserId: String; public let recipientUserId: String
    public let latitude: Double?; public let longitude: Double?; public let locationUpdatedAt: String?
    public let expiresAt: String; public let revoked: Bool; public let createdAt: String
}
public struct StartLocationShareRequest: Encodable { public let recipientPhoneNumber: String; public let durationHours: Int }
public struct StartLocationShareResponse: Decodable { public let success: Bool; public let share: LiveLocationShareDto }
public struct UpdateLocationShareRequest: Encodable { public let latitude: Double; public let longitude: Double }
public struct UpdateLocationShareResponse: Decodable { public let success: Bool; public let updatedShareCount: Int }
public struct ExtendLocationShareRequest: Encodable { public let additionalHours: Int }
public struct ExtendLocationShareResponse: Decodable { public let success: Bool; public let share: LiveLocationShareDto }
public struct LocationSharesResponse: Decodable { public let success: Bool; public let shares: [LiveLocationShareDto] }
public struct LocationShareResponse: Decodable { public let success: Bool; public let share: LiveLocationShareDto }
public struct StopLocationShareResponse: Decodable { public let success: Bool }

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
    // Real additions (2026-08-09), same pass as the backend's own MapPlaceCategory.kt enum --
    // live-verified against itunda's real self-hosted Nominatim before adding.
    MapPlaceCategory(id: "MARKET", label: "Markets"),
    MapPlaceCategory(id: "BUS_STOP", label: "Bus stops"),
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
    // required/multiSelect added 2026-08-28 -- the backend has always returned
    // these real fields (4 real group combinations, MenuOptionGroup.kt), never
    // declared here, so every group rendered as a required-single-select radio.
    public let required: Bool
    public let multiSelect: Bool
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
    // Real bookable-service duration -- a non-null value means this "product" is
    // actually a real appointment-bookable service (e.g. a 30-minute haircut). See
    // MerchantProduct.kt's own doc comment on the backend. merchant-mfe/Android
    // already have this; this is the first iOS client.
    public let durationMinutes: Int?
    // Real Kakao Hair Shop-style prepay-to-book -- only meaningful when
    // durationMinutes is set.
    public let requiresPrepay: Bool?
    // Real "Best seller" badge (2026-08-28) -- see DealProductDto's own doc comment.
    public let isBestSeller: Bool
}
public struct PriceTierDto: Decodable { public let minQuantity: Int; public let unitPrice: Double }
public struct MerchantSummaryDto: Decodable { public let id: String; public let businessName: String }
public struct MerchantProductsResponse: Decodable { public let success: Bool; public let merchant: MerchantSummaryDto; public let products: [MerchantProductDto] }

// Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) -- see
// AffiliateLink.kt's own doc comment on the backend. bank-mfe/Android already have
// this; this is the first iOS client. Referral capture-at-checkout stays bank-mfe-only,
// a named, honest v1 scope-down (no deep-link precedent exists on this app).
public struct CreateAffiliateLinkRequest: Encodable { public let productId: String; public init(productId: String) { self.productId = productId } }
public struct AffiliateLinkDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let productId: String
    public let code: String
    public let clickCount: Int
    public let createdAt: String
}
public struct AffiliateLinkResponse: Decodable { public let success: Bool; public let link: AffiliateLinkDto }
public struct AffiliateLinksResponse: Decodable { public let success: Bool; public let links: [AffiliateLinkDto] }
public struct AffiliateCommissionDto: Decodable, Identifiable {
    public let id: String
    public let linkId: String
    public let referrerId: String
    public let orderId: String
    public let buyerId: String
    public let commissionAmount: Double
    public let payoutTransactionId: String
    public let createdAt: String
}
public struct AffiliateCommissionsResponse: Decodable { public let success: Bool; public let commissions: [AffiliateCommissionDto] }

// Real local-business appointment booking, customer side -- see
// rw.itunda.merchant.MerchantBookingService on the backend for the full account.
// merchant-mfe/Android already have this; this is the first iOS client.
public struct BookingSlotDto: Decodable, Equatable { public let startTime: String; public let endTime: String }
public struct BookingSlotsResponse: Decodable { public let success: Bool; public let slots: [BookingSlotDto] }
public struct CreateBookingRequest: Encodable {
    public let merchantId: String; public let serviceId: String; public let date: String; public let startTime: String; public let notes: String?
    public init(merchantId: String, serviceId: String, date: String, startTime: String, notes: String?) {
        self.merchantId = merchantId; self.serviceId = serviceId; self.date = date; self.startTime = startTime; self.notes = notes
    }
}
public struct MerchantBookingDto: Decodable, Identifiable {
    public let id: String
    public let merchantId: String
    public let customerId: String
    public let serviceId: String
    public let serviceName: String
    public let bookingDate: String
    public let startTime: String
    public let endTime: String
    public let status: String
    public let notes: String?
    public let createdAt: String
    public let updatedAt: String
}
public struct MerchantBookingDetailResponse: Decodable { public let success: Bool; public let booking: MerchantBookingDto }
public struct MerchantBookingsResponse: Decodable { public let success: Bool; public let bookings: [MerchantBookingDto] }

// Real post-appointment reviews (item 143) -- see MerchantBookingReview.kt's own doc
// comment. The owner-side list+reply half is real on merchant-mfe; this is the
// CUSTOMER-facing submit-a-review half, real on bank-mfe/Android since 2026-08-01 --
// this is the first iOS client.
public struct SubmitBookingReviewRequest: Encodable {
    public let rating: Int; public let comment: String?
    public init(rating: Int, comment: String?) { self.rating = rating; self.comment = comment }
}
public struct MerchantBookingReviewDto: Decodable, Identifiable {
    public let id: String
    public let bookingId: String
    public let merchantId: String
    public let customerId: String
    public let serviceName: String
    public let rating: Int
    public let comment: String?
    public let ownerReply: String?
    public let ownerRepliedAt: String?
    public let createdAt: String
}
public struct MerchantBookingReviewResponse: Decodable { public let success: Bool; public let review: MerchantBookingReviewDto }
public struct MerchantBookingReviewsResponse: Decodable { public let success: Bool; public let reviews: [MerchantBookingReviewDto] }
public struct MerchantBookingRatingDto: Decodable { public let average: Double?; public let count: Int }
public struct MerchantReviewsWithRatingResponse: Decodable { public let success: Bool; public let reviews: [MerchantBookingReviewDto]; public let rating: MerchantBookingRatingDto }

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
    // Real "Best seller" badge (2026-08-28) -- see DealProductDto's own doc comment.
    public let isBestSeller: Bool
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

// Real read-only payment-code preview (item 149) -- see MerchantService.previewIntent's
// own doc comment. Lets a payer see the merchant/amount/their own real coupon
// eligibility before committing to collectPayment -- mirrors bank-mfe's lib/shopping.ts
// PaymentIntentPreview/MerchantCouponView exactly. bank-mfe/Android already have this;
// this is the first iOS client.
public struct MerchantCouponPreviewDto: Decodable {
    public let id: String
    public let merchantId: String
    public let title: String
    public let description: String?
    public let discountType: String
    public let discountValue: Double
    public let regularsOnly: Bool
    public let active: Bool
    public let expiresAt: String?
    public let createdAt: String
}
public struct MerchantCouponViewDto: Decodable, Identifiable {
    public let coupon: MerchantCouponPreviewDto
    public let eligible: Bool
    public let alreadyRedeemed: Bool
    public var id: String { coupon.id }
}
public struct MerchantCouponsForCustomerResponse: Decodable { public let success: Bool; public let coupons: [MerchantCouponPreviewDto] }

// Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
// Coupon box + Membership screens). Both endpoints these DTOs back had ZERO
// controller endpoint anywhere before this pass -- see MerchantCouponService.kt's
// own doc comments (browseCoupons/getMyRedemptions) and
// MerchantLoyaltyPointsService.kt's (getMyBalances). Mirrors bank-mfe's
// lib/coupons.ts exactly.
public struct CouponBrowseViewDto: Decodable, Identifiable {
    public let coupon: MerchantCouponPreviewDto
    public let merchantName: String
    public let eligible: Bool
    public let alreadyRedeemed: Bool
    public var id: String { coupon.id }
}
public struct CouponBrowseResponse: Decodable { public let success: Bool; public let coupons: [CouponBrowseViewDto] }
public struct CouponRedemptionDto: Decodable, Identifiable {
    public let id: String
    public let couponId: String
    public let merchantId: String
    public let transactionId: String
    public let discountAmount: Double
    public let redeemedAt: String
}
public struct CouponRedemptionsResponse: Decodable { public let success: Bool; public let redemptions: [CouponRedemptionDto] }
public struct LoyaltyBalanceDto: Decodable, Identifiable {
    public let merchantId: String
    public let merchantName: String
    public let pointBalance: Double
    public var id: String { merchantId }
}
public struct LoyaltyBalancesResponse: Decodable { public let success: Bool; public let balances: [LoyaltyBalanceDto]; public let total: Double }

// Real customer-presented payment code (Pay-parity port, §239) -- see
// MerchantService.generateCustomerPaymentCode's own doc comment, and bank-mfe's
// lib/shopping.ts generateCustomerPaymentCode for the identical real contract this
// mirrors: a short-lived (2-min), single-use, opaque code a merchant scans and
// charges. The QR a caller builds from `code` must encode the RAW string -- no
// `itunda://...` URL wrapping -- matching exactly what the real merchant-side
// scanner passes through unparsed.
public struct GenerateCustomerPaymentCodeRequest: Encodable {
    public let accountId: String?
    public init(accountId: String? = nil) { self.accountId = accountId }
}
public struct CustomerPaymentCodeResponse: Decodable {
    public let success: Bool
    public let code: String
    public let expiresAt: String
    public let accountId: String?
}
public struct PaymentIntentPreviewResponse: Decodable {
    public let success: Bool
    public let merchantId: String
    public let businessName: String
    public let amount: Double
    public let description: String?
    public let coupons: [MerchantCouponViewDto]
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

// Real RewardsService task list -- mirrors bank-mfe's lib/rewards.ts RewardTasksResult
// and Android's identical RewardTaskDto/RewardTasksResponse (ApiService.kt), added the
// same session for the real Toss Pay home "Get more rewards" preview.
public struct RewardTaskDto: Decodable, Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let rewardAmount: Double
    public let claimed: Bool
    public let claimedAt: String?
    public let eligible: Bool
}
public struct RewardTasksResponse: Decodable { public let success: Bool; public let tasks: [RewardTaskDto]; public let rewardsTotal: Double }
public struct ClaimRewardTaskRequest: Encodable { public let taskId: String }
public struct ClaimRewardTaskResponse: Decodable { public let success: Bool; public let message: String; public let rewardAmount: Double; public let newBalance: Double }

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
    // Real Naver Shopping 가격 변동 알림 (price-drop alert, item 227) -- true once the
    // product's real current price has dropped below the price it was at when
    // favorited. See the backend's ProductFavoriteService.getMyFavorites doc comment.
    public let priceDropped: Bool
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
// (item 174) already have this; this is the iOS port, buyer side only. Merchant-side
// approve/reject queue closed on Android/iOS together as item 234 below.
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
public struct UpdateOrderStatusRequest: Encodable { public let status: String }
public struct DecideOrderReturnRequest: Encodable { public let approve: Bool }

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
    // helpfulCount added 2026-08-25 -- real Coupang/Naver-style "도움돼요" counter, see
    // ProductReview.kt's own doc comment on the backend.
    public let helpfulCount: Int?
    public let createdAt: String
}
public struct ToggleHelpfulReviewResponse: Decodable { public let success: Bool; public let helpful: Bool }
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

// Real recurring-payment ("subscription") detection -- mirrors bank-mfe's lib/account.ts
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

// Real Toss Pay home reference -- mirrors bank-mfe/Android's identical
// NearbyMerchant/NearbyMerchantDto exactly (MerchantDiscoveryService.kt).
public struct NearbyMerchantDto: Decodable, Identifiable {
    public let id: String
    public let businessName: String
    public let category: String?
    public let cashbackRate: Double
    public let latitude: Double
    public let longitude: Double
    public let distanceKm: Double
}
public struct NearbyMerchantsResponse: Decodable { public let success: Bool; public let merchants: [NearbyMerchantDto] }

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

    // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- a public
    // memberwise init wasn't needed before (every existing instance came straight off
    // the wire via Decodable), but EatsRestaurantMenu.swift's own bookmark-quick-select
    // fix now needs to construct one client-side from a MapBookmarkDto. The compiler's
    // own synthesized memberwise init is only `internal` for a struct declared in this
    // module, not visible from the App target.
    public init(displayName: String, latitude: Double, longitude: Double) {
        self.displayName = displayName
        self.latitude = latitude
        self.longitude = longitude
    }
}
public struct AddressSearchResponse: Decodable { public let success: Bool; public let suggestions: [AddressSuggestionDto] }

// Real post-delivery ratings & reviews (2026-07-18) -- see EatsReviewService's own doc
// comment. Ported from bank-mfe's own review UI, the template for this iOS version.
public struct SubmitEatsReviewRequest: Encodable {
    public let restaurantRating: Int
    public let restaurantComment: String?
    public let riderRating: Int
    public let riderComment: String?
    // Real optional review photo (itunda Eats redesign, 2026-08-28) -- see
    // EatsReviewDto.photoUrl's own doc comment. itunda has no upload/storage
    // pipeline, so this is a real "paste your own already-hosted URL" field.
    public let photoUrl: String?
    // Real tag-based good points (itunda Maps redesign, 2026-08-28) -- ports the same
    // preset-tag pattern already shipped for Hood marketplace reviews.
    public let goodPoints: [String]
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
    // Real review photo (itunda Eats redesign, 2026-08-28) -- see backend
    // EatsReview.photoUrl's own doc comment: real end-to-end on the backend since
    // 2026-08-04, Android already has it, this is the first iOS client.
    public let photoUrl: String?
    public let createdAt: String
    // Real Coupang/Naver-style "도움돼요" (helpful) counter -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe/Android with zero iOS client until now -- found via a
    // cross-platform-parity check.
    public let helpfulCount: Int
}
public struct EatsReviewResponse: Decodable { public let success: Bool; public let review: EatsReviewDto }
public struct EatsReviewsResponse: Decodable { public let success: Bool; public let reviews: [EatsReviewDto] }
public struct EatsRatingResponse: Decodable { public let success: Bool; public let average: Double?; public let count: Int }
public struct ReplyToEatsReviewRequest: Encodable { public let reply: String }
public struct ToggleEatsReviewHelpfulResponse: Decodable { public let success: Bool; public let helpful: Bool }
public struct ReportEatsReviewRequest: Encodable {
    public let reason: String
    public init(reason: String) { self.reason = reason }
}

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
    // Real Baemin-style tiered order-amount promotion (2026-08-16) -- itunda-funded,
    // not restaurant-funded. See EatsPromotionCalculator's own doc comment on the
    // backend. Zero for every order below the lowest real tier; the backend always
    // includes this field now, matching every other required Double here.
    public let promotionDiscount: Double
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
    // Real Uber Eats post-delivery tip -- see EatsOrderService.tipRider's own doc
    // comment. Ported from bank-mfe/Android (2026-09-03). Non-null once tipped.
    public let tipAmount: Double?
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
public struct TipEatsOrderRequest: Encodable { public let amount: Double; public init(amount: Double) { self.amount = amount } }
public struct TipEatsOrderResponse: Decodable { public let success: Bool; public let order: EatsOrderDto }

// Real 배달의민족 함께주문 (Baemin "Together Order") -- ported from bank-mfe/Android
// (2026-09-03). A join-code-shared cart in front of the same real checkout/payment
// path finalizeGroupEatsOrder reuses underneath.
public struct GroupEatsOrderDto: Decodable {
    public let id: String; public let hostUserId: String; public let restaurantId: String; public let joinCode: String
    public let deliveryAddress: String; public let deliveryLatitude: Double?; public let deliveryLongitude: Double?
    public let fulfillmentType: String; public let status: String; public let resultingOrderId: String?
    public let createdAt: String; public let finalizedAt: String?
}
public struct GroupEatsOrderItemView: Decodable, Identifiable { public let productId: String; public let productName: String; public let quantity: Int; public let unitPrice: Double; public let lineTotal: Double; public var id: String { productId } }
public struct GroupEatsOrderParticipantView: Decodable, Identifiable { public let userId: String; public let joinedAt: String; public let subtotal: Double; public let items: [GroupEatsOrderItemView]; public var id: String { userId } }
public struct CreateGroupEatsOrderRequest: Encodable { public let restaurantId: String; public let deliveryAddress: String; public init(restaurantId: String, deliveryAddress: String) { self.restaurantId = restaurantId; self.deliveryAddress = deliveryAddress } }
public struct JoinGroupEatsOrderRequest: Encodable { public let joinCode: String; public init(joinCode: String) { self.joinCode = joinCode } }
public struct GroupEatsOrderItemRequest: Encodable { public let menuItemId: String; public let quantity: Int; public init(menuItemId: String, quantity: Int) { self.menuItemId = menuItemId; self.quantity = quantity } }
public struct SetGroupEatsOrderItemsRequest: Encodable { public let items: [GroupEatsOrderItemRequest]; public init(items: [GroupEatsOrderItemRequest]) { self.items = items } }
public struct GroupEatsOrderResponse: Decodable { public let success: Bool; public let groupOrder: GroupEatsOrderDto }
public struct GroupEatsOrderDetailResponse: Decodable { public let success: Bool; public let groupOrder: GroupEatsOrderDto; public let grandTotal: Double; public let participants: [GroupEatsOrderParticipantView] }
public struct FinalizeGroupEatsOrderResponse: Decodable { public let success: Bool; public let order: EatsOrderDto; public let items: [EatsOrderItemDto] }

public struct RiderLocationDto: Decodable { public let latitude: Double; public let longitude: Double; public let updatedAt: String }
public struct EatsRiderLocationResponse: Decodable { public let success: Bool; public let available: Bool; public let location: RiderLocationDto? }

public struct RiderDto: Decodable, Identifiable { public let id: String; public let userId: String; public let accountId: String; public let status: String; public let available: Bool; public let createdAt: String }
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

// Real, minimal product-analytics event (2026-08-10) -- see the backend's own
// AnalyticsEvent.kt doc comment and the "itunda: the wedge, not the mirror" strategy
// memo, recommendation (ii). eventName must be one of AnalyticsController.KNOWN_EVENTS
// on the backend (currently "home_view"/"coop_rail_tap") -- a mismatched name
// real-400s rather than silently recording garbage.
public struct RecordAnalyticsEventRequest: Encodable {
    public let eventName: String
    public let platform: String
    public let metadata: String?
    public init(eventName: String, platform: String = "ios", metadata: String? = nil) {
        self.eventName = eventName
        self.platform = platform
        self.metadata = metadata
    }
}
public struct UploadResponse: Decodable { public let success: Bool; public let url: String }

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
// Real Toss/Naver 해외주식 (overseas stock trading, item 230) -- "RSE" (the original 6
// domestic symbols) vs "NASDAQ" (5 real US-listed symbols, same deterministic
// simulation StockCatalog.kt already establishes for RSE). bank-mfe/Android already
// have this.
public struct StockDto: Decodable, Identifiable {
    public let id: String
    public let symbol: String
    public let name: String
    public let price: Double
    public let change: Double
    public let changePercent: Double
    public let marketCap: String
    public let volume: Int
    public let market: String
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
// Real Investment-account top-up (2026-08-04) -- found via a fresh "defined but
// uncalled" endpoint sweep: StocksService.fundInvestmentAccount (a real MAIN ->
// INVESTMENT internal ledger transfer) had zero client anywhere, so a user with no
// pre-seeded investment balance had no way to ever actually buy a stock. Ports the
// same fix already shipped on Android/bank-mfe.
public struct FundInvestmentRequest: Encodable { public let amount: Double }
public struct FundInvestmentTransactionDto: Decodable { public let id: String; public let amount: Double; public let completedAt: String }
// Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- found via a
// fresh "defined but uncalled" endpoint sweep: the backend (set/clear/getPriceAlert +
// StockPriceAlertScheduler) shipped fully live-verified 2026-08-17 but had zero client
// anywhere, on any platform. This is iOS's first wiring for it. GET returns a real flat
// shape (StocksController.getPriceAlert); POST/DELETE return the real StockWatchlist
// entity nested under "watch" (StocksController.setPriceAlert/clearPriceAlert,
// unchanged from section 113) -- two genuinely different real response shapes.
public struct SetPriceAlertRequest: Encodable { public let targetPrice: Double; public let direction: String }
public struct PriceAlertResponse: Decodable { public let success: Bool; public let targetPrice: Double?; public let targetDirection: String?; public let alertTriggeredAt: String? }
public struct StockWatchAlertDto: Decodable { public let targetPrice: Double?; public let targetDirection: String?; public let alertTriggeredAt: String? }
public struct SetPriceAlertResponse: Decodable { public let success: Bool; public let watch: StockWatchAlertDto }
public struct FundInvestmentResponse: Decodable { public let success: Bool; public let transaction: FundInvestmentTransactionDto }

extension NetworkClient {
    public func startConversation(phoneNumber: String) async throws -> ConversationResponse {
        try await authenticatedPost("api/v1/messages/conversations", body: StartConversationRequest(phoneNumber: phoneNumber, otherUserId: nil))
    }

    public func startConversation(otherUserId: String) async throws -> ConversationResponse {
        try await authenticatedPost("api/v1/messages/conversations", body: StartConversationRequest(phoneNumber: nil, otherUserId: otherUserId))
    }

    public func getConversations(archived: Bool = false) async throws -> ConversationsResponse {
        try await get("api/v1/messages/conversations", query: [URLQueryItem(name: "archived", value: archived ? "true" : "false")])
    }

    public func getTalkContacts() async throws -> TalkContactsResponse { try await get("api/v1/messages/contacts") }

    // Real KakaoTalk-style "오늘의 생일" (Today's Birthday) -- ported from bank-mfe
    // (2026-09-03). Reuses the same TalkContactDto shape as getTalkContacts.
    public func getTodaysBirthdays() async throws -> TalkContactsResponse { try await get("api/v1/messages/contacts/birthdays-today") }

    public func getMessages(conversationId: String) async throws -> MessagesResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/messages")
    }

    // Real Thread support (2026-08-05) -- see MessageDto.replyCount's own doc comment.
    // Root message first, then every direct reply oldest-first.
    public func getThread(conversationId: String, messageId: String) async throws -> MessagesResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/messages/\(messageId)/thread")
    }

    public func searchMessages(conversationId: String, query: String) async throws -> MessagesResponse {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        return try await get("api/v1/messages/conversations/\(conversationId)/messages/search?query=\(encoded)")
    }

    public func sendMessage(conversationId: String, body: String, replyToMessageId: String? = nil, imageUrl: String? = nil) async throws -> MessageResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/messages", body: SendMessageRequest(body: body, replyToMessageId: replyToMessageId, imageUrl: imageUrl))
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

    // Real message forwarding, 1:1 source (item 3 remainder) -- ports Android
    // TalkScreen.kt's ForwardDestinationDialog / bank-mfe's forwardMessage to iOS.
    // See rw.itunda.messaging.web.MessagingController's own forward endpoint.
    public func forwardDirectMessage(messageId: String, destinationType: String, destinationId: String) async throws -> ForwardMessageResponse {
        try await authenticatedPost("api/v1/messages/messages/\(messageId)/forward", body: ForwardMessageRequest(destinationType: destinationType, destinationId: destinationId))
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

    public func getConversationArchived(conversationId: String) async throws -> ConversationArchivedResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/archive")
    }

    public func setConversationArchived(conversationId: String, archived: Bool) async throws -> ConversationArchivedResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/archive", body: SetConversationArchivedRequest(archived: archived))
    }

    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see
    // ConversationSummaryDto.pinnedToTop's own doc comment. Found fully built on the
    // backend (MessagingController POST/GET .../pin-to-top) with zero iOS caller, same
    // defined-but-uncalled shape this session already found and closed for bank-mfe
    // (Section 165) -- Android/iOS were explicitly left for follow-up then.
    public func getConversationPinnedToTop(conversationId: String) async throws -> ConversationPinnedToTopResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/pin-to-top")
    }

    public func setConversationPinnedToTop(conversationId: String, pinned: Bool) async throws -> ConversationPinnedToTopResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/pin-to-top", body: SetConversationPinnedToTopRequest(pinned: pinned))
    }

    // Real KakaoTalk-style "favorite" chat toggle (itunda Talk redesign, 2026-08-28).
    public func getConversationFavorite(conversationId: String) async throws -> ConversationFavoriteResponse {
        try await get("api/v1/messages/conversations/\(conversationId)/favorite")
    }

    public func setConversationFavorite(conversationId: String, favorite: Bool) async throws -> ConversationFavoriteResponse {
        try await authenticatedPost("api/v1/messages/conversations/\(conversationId)/favorite", body: SetConversationFavoriteRequest(favorite: favorite))
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

    // Real KakaoTalk 오픈채팅-style open group (Talk-parity port, §243) -- see
    // GroupMessagingService.createOpenGroup's own doc comment. bank-mfe already
    // has this (2026-08-19); this is the first iOS client. Anyone with the real
    // joinCode can join without being invited by phone number first -- distinct
    // from createGroup above, which requires knowing everyone's real number
    // up front.
    public func createOpenGroup(name: String) async throws -> OpenGroupResponse {
        try await authenticatedPost("api/v1/messages/groups/open", body: CreateOpenGroupRequest(name: name))
    }
    public func joinGroupByCode(joinCode: String) async throws -> JoinGroupResponse {
        try await authenticatedPost("api/v1/messages/groups/join", body: JoinGroupByCodeRequest(joinCode: joinCode))
    }

    public func getGroupMessages(groupId: String) async throws -> GroupMessagesResponse {
        try await get("api/v1/messages/groups/\(groupId)/messages")
    }

    // Real Thread support (2026-08-05) -- see getThread's own doc comment; identical
    // shape for group chat.
    public func getGroupThread(groupId: String, messageId: String) async throws -> GroupMessagesResponse {
        try await get("api/v1/messages/groups/\(groupId)/messages/\(messageId)/thread")
    }

    public func sendGroupMessage(groupId: String, body: String, replyToMessageId: String? = nil, imageUrl: String? = nil) async throws -> GroupMessageResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/messages", body: SendGroupMessageRequest(body: body, replyToMessageId: replyToMessageId, imageUrl: imageUrl))
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

    // Real group Pin -- exact mirror of the already-real 1:1
    // getPinnedConversationMessage/pinConversationMessage/unpinConversationMessage above;
    // group threads never got this client despite the backend endpoint being real since
    // group Pin's own 1:1 counterpart shipped. See GroupMessagingController's own pin
    // endpoints.
    public func getPinnedGroupMessage(groupId: String) async throws -> PinnedGroupMessageResponse {
        try await get("api/v1/messages/groups/\(groupId)/pin")
    }

    public func pinGroupMessage(groupId: String, messageId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/messages/groups/\(groupId)/pin/\(messageId)", body: EmptyRequest())
    }

    public func unpinGroupMessage(groupId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/messages/groups/\(groupId)/pin")
    }

    // Real message forwarding, group source -- see forwardDirectMessage's own doc
    // comment above for the shared contract; this is the group-sourced sibling URL.
    public func forwardGroupMessage(messageId: String, destinationType: String, destinationId: String) async throws -> ForwardMessageResponse {
        try await authenticatedPost("api/v1/messages/groups/messages/\(messageId)/forward", body: ForwardMessageRequest(destinationType: destinationType, destinationId: destinationId))
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

    // Real gap found 2026-09-04: MarketplaceService.createListing has a genuinely
    // rich validation surface (title/description length caps, coordinate bounds,
    // meeting-place/photo-URL length caps, vehicle mileage/claim-count, and a whole
    // all-or-nothing lease-takeover consistency block) all mapped to plain 400s --
    // TalkScreen.errorMessage(400) collapses every one of them into "Please check
    // what you entered and try again.", losing the specific real reason on exactly
    // the kind of multi-field form that benefits most from it.
    public func createListing(title: String, description: String, price: Double, category: String, latitude: Double? = nil, longitude: Double? = nil, meetingPlace: String? = nil, photoUrl: String? = nil, vehicleMileageKm: Int? = nil, vehicleInsuranceClaimCount: Int? = nil, vehicleIsLeaseTakeover: Bool = false, leaseTotalAcquisitionCost: Double? = nil, leaseRemainingMonths: Int? = nil, leaseTotalMonths: Int? = nil, leaseMonthlyPayment: Double? = nil, leaseSubsidyAmount: Double? = nil, leaseReturnFee: Double? = nil) async throws -> ListingResponse {
        try await authenticatedPostWithMessage("api/v1/marketplace/listings", body: CreateListingRequest(title: title, description: description, price: price, category: category, latitude: latitude, longitude: longitude, meetingPlace: meetingPlace, photoUrl: photoUrl, vehicleMileageKm: vehicleMileageKm, vehicleInsuranceClaimCount: vehicleInsuranceClaimCount, vehicleIsLeaseTakeover: vehicleIsLeaseTakeover, leaseTotalAcquisitionCost: leaseTotalAcquisitionCost, leaseRemainingMonths: leaseRemainingMonths, leaseTotalMonths: leaseTotalMonths, leaseMonthlyPayment: leaseMonthlyPayment, leaseSubsidyAmount: leaseSubsidyAmount, leaseReturnFee: leaseReturnFee))
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
        try await postListingAction("api/v1/marketplace/listings/\(listingId)/boost", body: BoostListingRequest(days: days), idempotencyKey: UUID().uuidString)
    }

    // Real 가격 수정 (price edit) + Karrot 가격 하락 알림 -- see backend
    // MarketplaceService.updatePrice's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero iOS client until now -- found via a cross-platform-parity
    // check. Not money-moving itself, so no Idempotency-Key, matching boostListing above.
    public func updateListingPrice(_ listingId: String, price: Double) async throws -> ListingResponse {
        try await authenticatedPatch("api/v1/marketplace/listings/\(listingId)/price", body: UpdateListingPriceRequest(price: price))
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    // 2026-09-04: same real gap as submitJobPostReview/submitPropertyListingReview --
    // "not marked sold yet" vs. "already reviewed" both mapped to 409, both showing
    // the identical generic TalkScreen.errorMessage text.
    public func submitListingReview(_ listingId: String, goodPoints: [String], uncomfortablePoints: [String]) async throws -> HoodReviewResponse {
        try await authenticatedPostWithMessage(
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

    // Real "pay via itunda" Marketplace escrow -- see MarketplaceEscrowDto's own doc
    // comment for the full account (2026-08-15, first iOS client).
    public func payEscrow(_ listingId: String, deliveryAddress: String? = nil) async throws -> MarketplaceEscrowResponse {
        try await authenticatedPost(
            "api/v1/marketplace/listings/\(listingId)/pay-escrow",
            body: PayEscrowRequest(deliveryAddress: deliveryAddress?.isEmpty == true ? nil : deliveryAddress),
            idempotencyKey: UUID().uuidString,
        )
    }

    public func confirmEscrowReceipt(_ listingId: String) async throws -> MarketplaceEscrowResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/confirm-receipt", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func disputeEscrow(_ listingId: String, reason: String) async throws -> MarketplaceEscrowResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/dispute-escrow", body: DisputeEscrowRequest(reason: reason))
    }

    public func getEscrow(_ listingId: String) async throws -> MarketplaceEscrowResponse {
        try await get("api/v1/marketplace/listings/\(listingId)/escrow")
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

    // Real Karrot "이 글 숨기기" (hide this post) -- see backend ListingHideService's own
    // doc comment. Real, shipped on the backend + bank-mfe (2026-08-24) with zero iOS
    // client until now -- found via a cross-platform-parity check. Mirrors
    // addListingFavorite/removeListingFavorite exactly (POST to hide, DELETE to unhide).
    public func hideListing(_ listingId: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/marketplace/listings/\(listingId)/hide", body: EmptyBody())
    }

    public func unhideListing(_ listingId: String) async throws -> SuccessResponse {
        try await authenticatedDelete("api/v1/marketplace/listings/\(listingId)/hide")
    }

    // Real KakaoTalk-style gift send/claim (2026-07-20) -- see GiftService.
    public func sendGiftInConversation(conversationId: String, amount: Double, note: String?, theme: String? = nil) async throws -> GiftResponse {
        try await authenticatedPost(
            "api/v1/gifts/conversations/\(conversationId)",
            body: SendGiftInConversationRequest(amount: amount, note: note, theme: theme),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real standalone send-by-phone-number (found via an uncalled-endpoint sweep
    // 2026-08-16, backend/bank-mfe docs Section 88) -- distinct from the chat-embedded
    // call above, this is the general "gift anyone with an itunda account" entry point.
    public func sendGift(recipientPhoneNumber: String, amount: Double, note: String?, theme: String? = nil) async throws -> GiftResponse {
        try await authenticatedPost(
            "api/v1/gifts",
            body: SendGiftRequest(recipientPhoneNumber: recipientPhoneNumber, amount: amount, note: note, theme: theme),
            idempotencyKey: UUID().uuidString
        )
    }

    public func getGift(giftId: String) async throws -> GiftResponse {
        try await get("api/v1/gifts/\(giftId)")
    }

    public func claimGift(giftId: String) async throws -> GiftResponse {
        try await authenticatedPostWithCode("api/v1/gifts/\(giftId)/claim", body: EmptyBody(), idempotencyKey: UUID().uuidString)
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
        eventDate: String? = nil, capacity: Int? = nil, topic: String? = nil
    ) async throws -> CommunityPostResponse {
        try await authenticatedPost(
            "api/v1/community/posts",
            body: CreateCommunityPostRequest(category: category, title: title, body: body, latitude: latitude, longitude: longitude, eventDate: eventDate, capacity: capacity, topic: topic)
        )
    }

    public func browseCommunityPosts(category: String? = nil, topic: String? = nil) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts", query: [URLQueryItem(name: "category", value: category), URLQueryItem(name: "topic", value: topic)])
    }

    public func getNearbyCommunityPosts(lat: Double, lng: Double, radiusKm: Double = 3) async throws -> CommunityPostsResponse {
        try await get("api/v1/community/posts/nearby", query: [URLQueryItem(name: "latitude", value: String(lat)), URLQueryItem(name: "longitude", value: String(lng)), URLQueryItem(name: "radiusKm", value: String(radiusKm))])
    }

    public func getMyCommunityPosts() async throws -> CommunityPostsResponse { try await get("api/v1/community/my-posts") }

    // Real 당근모임-style "upcoming meetups" browse (backend 2026-07-25,
    // CommunityController.upcomingMeetups) -- excludes meetups whose eventDate has
    // already passed and orders by soonest first, unlike browseCommunityPosts(category:
    // "meetup") above which is plain chronological with no date filter at all. Found
    // missing entirely on iOS (Android/bank-mfe both had the API binding defined but
    // never called -- this platform never even had the binding) while fixing the
    // identical "🎉 Meetups pinned slot shows past events" bug on the other two
    // platforms 2026-09-04.
    public func getUpcomingMeetups() async throws -> CommunityPostsResponse { try await get("api/v1/community/meetups/upcoming") }

    public func getCommentNotificationsEnabled() async throws -> CommentNotificationsEnabledResponse { try await get("api/v1/community/notification-preference") }

    public func setCommentNotificationsEnabled(_ enabled: Bool) async throws -> CommentNotificationsEnabledResponse {
        try await authenticatedPost("api/v1/community/notification-preference", body: SetCommentNotificationsEnabledRequest(enabled: enabled))
    }

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
        try await authenticatedPost(
            "api/v1/community/posts/\(postId)/finalize-group-buy",
            body: FinalizeGroupBuyRequest(totalAmount: totalAmount, description: description),
            idempotencyKey: UUID().uuidString
        )
    }

    // Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
    public func getJobCategories() async throws -> JobCategoriesResponse { try await get("api/v1/jobs/categories") }

    // Real gap found 2026-09-04: JobPostService.create validates title/description
    // length, category whitelist, and coordinate bounds beyond the client's own
    // non-empty/amount>0 guard -- all mapped to plain 400s TalkScreen.errorMessage
    // collapses into one generic string.
    public func createJobPost(category: String, title: String, description: String, payType: String, payAmount: Double, latitude: Double? = nil, longitude: Double? = nil) async throws -> JobPostResponse {
        try await authenticatedPostWithMessage("api/v1/jobs/posts", body: CreateJobPostRequest(category: category, title: title, description: description, payType: payType, payAmount: payAmount, latitude: latitude, longitude: longitude))
    }

    public func browseJobPosts(category: String? = nil) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts", query: [URLQueryItem(name: "category", value: category)])
    }

    // Real relevance-ranked search (2026-08-14, backend JobPostController's own doc
    // comment) -- shipped Android-only, never ported here; same real uncalled-endpoint
    // gap class bank-mfe's own Jobs search fix already closed once for web.
    public func searchJobPosts(_ q: String) async throws -> JobPostsResponse {
        try await get("api/v1/jobs/posts/search", query: [URLQueryItem(name: "q", value: q)])
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
    // Real gap found 2026-09-04: HoodReviewService's "not marked filled yet" and
    // "already reviewed" cases both map to 409, both showing the identical generic
    // TalkScreen.errorMessage text.
    public func submitJobPostReview(_ jobPostId: String, goodPoints: [String], uncomfortablePoints: [String]) async throws -> HoodReviewResponse {
        try await authenticatedPostWithMessage(
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
    // Real gap found 2026-09-04: JobApplicationService.apply's two real 409 causes
    // (job post no longer open vs. already applied) were both showing the identical
    // TalkScreen.errorMessage(409) text ("That's already been done, or is being
    // processed.") -- a real, actionable distinction lost. Also swallowed the
    // required self-introduction length validation, which the client never checks.
    public func applyToJob(_ jobPostId: String, message: String) async throws -> JobApplicationResponse {
        try await authenticatedPostWithMessage("api/v1/jobs/posts/\(jobPostId)/apply", body: ApplyToJobRequest(message: message))
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

    public func getPropertyValuation(latitude: Double, longitude: Double, propertyType: String, listingType: String, sizeSqm: Double, radiusKm: Double = 5) async throws -> PropertyValuationResponse {
        try await get("api/v1/realestate/valuation", query: [
            URLQueryItem(name: "latitude", value: String(latitude)),
            URLQueryItem(name: "longitude", value: String(longitude)),
            URLQueryItem(name: "propertyType", value: propertyType),
            URLQueryItem(name: "listingType", value: listingType),
            URLQueryItem(name: "sizeSqm", value: String(sizeSqm)),
            URLQueryItem(name: "radiusKm", value: String(radiusKm)),
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
    // Real gap found 2026-09-04, same class as submitJobPostReview: HoodReviewService's
    // "not marked taken yet" and "already reviewed" cases both map to 409, both showing
    // the identical generic TalkScreen.errorMessage text.
    public func submitPropertyListingReview(_ propertyListingId: String, goodPoints: [String], uncomfortablePoints: [String]) async throws -> HoodReviewResponse {
        try await authenticatedPostWithMessage(
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

    // Real Karrot(당근마켓)-style price-drop notification -- see backend
    // PropertyListingService.updatePrice's own doc comment. Real, shipped on the
    // backend + bank-mfe with zero iOS client until now -- found via a
    // cross-platform-parity check, the real-estate mirror of the same fix already
    // ported to Marketplace listings (updateListingPrice above).
    // Real gap found 2026-09-04: PropertyListingService.updatePrice's real
    // "Only an available listing's price can be changed" 409 was showing the generic
    // TalkScreen.errorMessage(409) text ("That's already been done, or is being
    // processed."), an unhelpful match for "this listing is no longer available."
    public func updatePropertyListingPrice(_ propertyListingId: String, price: Double) async throws -> PropertyListingResponse {
        try await authenticatedPostWithMessage("api/v1/realestate/listings/\(propertyListingId)/price", body: UpdateListingPriceRequest(price: price))
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
    // businessType added 2026-08-13 (see Merchant.kt's MerchantBusinessType doc comment
    // on the backend for the full account: Eats' own restaurant browse showed
    // Electronics/Fashion merchants, since it shared Shop's exact unfiltered browse).
    // EatsScreen.swift passes "RESTAURANT"; ShopScreen.swift leaves it nil (unfiltered,
    // unchanged), same split as Android/web.
    // sortBy added 2026-08-28 (itunda Eats redesign) -- real RestaurantSortMode values
    // (delivery_time/favorites/rating/distance/discount/min_order), see
    // ShoppingMerchantBrowseService.browse's own doc comment. web/Android already have
    // this; this is the first iOS client.
    public func getShoppingMerchants(category: String? = nil, businessType: String? = nil, q: String? = nil, buyerLat: Double? = nil, buyerLng: Double? = nil, sortBy: String? = nil) async throws -> ShoppingMerchantsResponse {
        try await get("api/v1/shopping/merchants", query: [
            URLQueryItem(name: "category", value: category),
            URLQueryItem(name: "businessType", value: businessType),
            URLQueryItem(name: "q", value: q),
            URLQueryItem(name: "sortBy", value: sortBy),
            URLQueryItem(name: "buyerLat", value: buyerLat.map { String($0) }),
            URLQueryItem(name: "buyerLng", value: buyerLng.map { String($0) }),
        ])
    }

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend. businessType added 2026-08-13, same reasoning as
    // getShoppingMerchants above.
    public func getMerchantCategories(businessType: String? = nil) async throws -> MerchantCategoriesResponse {
        try await get("api/v1/shopping/merchants/categories", query: [URLQueryItem(name: "businessType", value: businessType)])
    }

    // Real "Deals" rail (2026-07-25) -- see backend MerchantProductRepository.findDeals's
    // own doc comment.
    // businessType added 2026-08-25 (direct user directive: "we need everything
    // separated to avoid confusion, that's toss style, clear isolation") -- matches
    // getMerchantCategories above.
    public func getShopDeals(businessType: String? = nil) async throws -> DealsResponse {
        try await get("api/v1/shopping/products/deals", query: [URLQueryItem(name: "businessType", value: businessType)])
    }

    // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc comment.
    public func getActiveTimeDeals() async throws -> TimeDealsResponse { try await get("api/v1/time-deals") }

    // Real Naver Pay 멤버십 데이 (Membership Day) cashback boost -- see
    // rw.itunda.merchant.ShoppingCashbackService's own doc comment. bank-mfe/Android
    // already have this; this is the first iOS client.
    public func getMembershipDayStatus() async throws -> MembershipDayStatusResponse { try await get("api/v1/shopping/membership-day") }

    // Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
    // see ShoppingMissionDto's own doc comment.
    public func getShoppingMissions() async throws -> ShoppingMissionsResponse { try await get("api/v1/shopping/points") }

    public func completeShoppingMission(type: String) async throws -> MissionCompleteResponse {
        try await authenticatedPost("api/v1/shopping/points/missions/\(type)/complete", body: EmptyBody())
    }

    // Real Coupang Eats-style dish grid (itunda Eats redesign, 2026-08-28) -- see
    // EatsDishRecommendationService.getDishes' own doc comment. sortBy="popular" ranks
    // by real order count; otherwise real personal "recommended for you" history.
    // Android/web already have this; this is the first iOS client.
    public func getEatsDishes(category: String? = nil, maxBudget: Double? = nil, sortBy: String? = nil) async throws -> EatsDishesResponse {
        try await get("api/v1/eats/dishes", query: [
            URLQueryItem(name: "category", value: category),
            URLQueryItem(name: "maxBudget", value: maxBudget.map { String($0) }),
            URLQueryItem(name: "sortBy", value: sortBy),
        ])
    }

    // Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28)
    // -- see OrderItemRepository.getFrequentlyOrderedWith's own doc comment.
    public func getFrequentlyOrderedWith(_ productId: String) async throws -> FrequentlyOrderedWithResponse {
        try await get("api/v1/shopping/products/\(productId)/frequently-ordered-with")
    }

    public func subscribeToProduct(merchantId: String, productId: String, quantity: Int, intervalDays: Int, deliveryAddress: String) async throws -> ProductSubscriptionResponse {
        try await authenticatedPost("api/v1/product-subscriptions", body: CreateProductSubscriptionRequest(merchantId: merchantId, productId: productId, quantity: quantity, intervalDays: intervalDays, deliveryAddress: deliveryAddress), idempotencyKey: UUID().uuidString)
    }
    public func getMyProductSubscriptions() async throws -> ProductSubscriptionsResponse { try await get("api/v1/product-subscriptions") }
    public func pauseProductSubscription(_ id: String) async throws -> ProductSubscriptionResponse { try await authenticatedPost("api/v1/product-subscriptions/\(id)/pause", body: EmptyBody()) }
    public func resumeProductSubscription(_ id: String) async throws -> ProductSubscriptionResponse { try await authenticatedPost("api/v1/product-subscriptions/\(id)/resume", body: EmptyBody()) }
    public func cancelProductSubscription(_ id: String) async throws -> ProductSubscriptionResponse { try await authenticatedPost("api/v1/product-subscriptions/\(id)/cancel", body: EmptyBody()) }

    // Real Coupang 정기배송 "건너뛰기" (skip next) -- ported from bank-mfe (2026-09-03).
    public func skipNextProductSubscriptionDelivery(_ id: String) async throws -> ProductSubscriptionResponse { try await authenticatedPost("api/v1/product-subscriptions/\(id)/skip-next", body: EmptyBody()) }

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
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(MoveMapBookmarkResponse.self, from: data)
    }

    // Real Naver Map-style public/private folder + share, and Kakao Map-style "구독"
    // (subscribe) -- ported from Android/bank-mfe (2026-08-18), see
    // SetMapFolderVisibilityRequest's own doc comment above. `folderName` is a real
    // user-chosen string (can contain spaces) -- percent-encoded explicitly before
    // interpolating into the path rather than trusting `appendingPathComponent` on the
    // whole multi-segment string, the same caution `moveMapBookmark`'s own doc comment
    // establishes for this exact class of gotcha.
    public func setMapFolderVisibility(folderName: String, isPublic: Bool) async throws -> SetMapFolderVisibilityResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("api/v1/maps/bookmarks/folder-visibility"))
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(SetMapFolderVisibilityRequest(folderName: folderName, isPublic: isPublic))
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else { throw NetworkError.httpError(statusCode: httpResponse.statusCode) }
        return try decoder.decode(SetMapFolderVisibilityResponse.self, from: data)
    }

    private func sharedMapFolderPath(userId: String, folderName: String, suffix: String = "") -> String {
        let encodedUser = userId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? userId
        let encodedFolder = folderName.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? folderName
        return "api/v1/maps/shared/\(encodedUser)/\(encodedFolder)\(suffix)"
    }

    // Deliberately unauthenticated on the backend (SecurityConfig permitAll) -- whoever
    // opens a real share link doesn't need an itunda session, matching Android's own
    // getSharedMapFolder. `get(_:)` still attaches a token if one exists, harmlessly.
    public func getSharedMapFolder(userId: String, folderName: String) async throws -> SharedMapFolderResponse {
        try await get(sharedMapFolderPath(userId: userId, folderName: folderName))
    }

    // The write half of a shared folder -- real-copies the owner's public places into
    // the caller's own bookmarks. Unlike the GET above, this is authenticated.
    public func subscribeToSharedMapFolder(userId: String, folderName: String) async throws -> SubscribeToSharedMapFolderResponse {
        try await authenticatedPost(sharedMapFolderPath(userId: userId, folderName: folderName, suffix: "/subscribe"), body: EmptyBody())
    }

    // Real Kakao Map-style "친구위치" live location sharing -- see LiveLocationShareDto's
    // own doc comment.
    public func startLocationShare(recipientPhoneNumber: String, durationHours: Int = 1) async throws -> StartLocationShareResponse {
        try await authenticatedPost("api/v1/maps/location-share", body: StartLocationShareRequest(recipientPhoneNumber: recipientPhoneNumber, durationHours: durationHours))
    }

    public func updateMyLocationShare(latitude: Double, longitude: Double) async throws -> UpdateLocationShareResponse {
        try await authenticatedPost("api/v1/maps/location-share/_/update-location", body: UpdateLocationShareRequest(latitude: latitude, longitude: longitude))
    }

    public func extendLocationShare(id: String, additionalHours: Int = 1) async throws -> ExtendLocationShareResponse {
        let encodedId = id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? id
        return try await authenticatedPost("api/v1/maps/location-share/\(encodedId)/extend", body: ExtendLocationShareRequest(additionalHours: additionalHours))
    }

    public func stopLocationShare(id: String) async throws -> StopLocationShareResponse {
        let encodedId = id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? id
        return try await authenticatedPost("api/v1/maps/location-share/\(encodedId)/stop", body: EmptyBody())
    }

    public func getMyLocationShares() async throws -> LocationSharesResponse { try await get("api/v1/maps/location-share/mine") }

    public func getLocationSharesWithMe() async throws -> LocationSharesResponse { try await get("api/v1/maps/location-share/shared-with-me") }

    // Real recipient-side poll -- call this on a real interval (e.g. every 15s) while
    // watching a share to see the sharer's latest pushed position.
    public func getLocationShare(id: String) async throws -> LocationShareResponse {
        let encodedId = id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? id
        return try await get("api/v1/maps/location-share/\(encodedId)")
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
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(SuccessResponse.self, from: data)
    }

    public func getMerchantProducts(merchantId: String) async throws -> MerchantProductsResponse {
        try await get("api/v1/shopping/merchants/\(merchantId)/products")
    }

    // Real seller chat (2026-08-28) -- mirrors contactSeller (Marketplace) above
    // exactly: resolves the real merchant owner's userId server-side, hands off to
    // the same real shared messaging system every other vertical already uses.
    public func contactMerchantSeller(merchantId: String) async throws -> ContactSellerResponse {
        try await authenticatedPost("api/v1/shopping/merchants/\(merchantId)/contact-seller", body: EmptyBody())
    }

    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) --
    // see AffiliateLinkDto's own doc comment. bank-mfe/Android already have this; this
    // is the first iOS client.
    public func createAffiliateLink(productId: String) async throws -> AffiliateLinkResponse {
        try await authenticatedPost("api/v1/affiliate/links", body: CreateAffiliateLinkRequest(productId: productId))
    }
    public func getMyAffiliateLinks() async throws -> AffiliateLinksResponse { try await get("api/v1/affiliate/links/my-links") }
    public func getMyAffiliateCommissions() async throws -> AffiliateCommissionsResponse { try await get("api/v1/affiliate/commissions/my-commissions") }

    // Real local-business appointment booking, customer side -- see
    // MerchantBookingController.kt's own doc comment. merchant-mfe/Android already
    // have this; this is the first iOS client.
    public func getBookingSlots(merchantId: String, serviceId: String, date: String) async throws -> BookingSlotsResponse {
        try await get("api/v1/merchant/\(merchantId)/booking-slots", query: [
            URLQueryItem(name: "serviceId", value: serviceId), URLQueryItem(name: "date", value: date),
        ])
    }
    // Real bug found 2026-08-15 (same Idempotency-Key audit as UpfrontDeposit above):
    // MerchantBookingController's own doc comment explicitly says "Idempotency-Key IS
    // required on POST /bookings -- found live in a 2026-08-02 audit," but this iOS
    // call never actually sent it -- has been silently 400ing since it shipped.
    public func createBooking(_ request: CreateBookingRequest) async throws -> MerchantBookingDetailResponse {
        try await authenticatedPost("api/v1/merchant/bookings", body: request, idempotencyKey: UUID().uuidString)
    }
    public func getMyBookings() async throws -> MerchantBookingsResponse { try await get("api/v1/merchant/bookings/my-bookings") }
    public func cancelBooking(_ bookingId: String) async throws -> MerchantBookingDetailResponse {
        try await authenticatedPost("api/v1/merchant/bookings/\(bookingId)/cancel", body: EmptyBody())
    }

    // Real customer-facing post-appointment review submission (item 143) -- see
    // MerchantBookingReviewController.kt. bank-mfe/Android already have this; this is
    // the first iOS client.
    public func submitBookingReview(_ bookingId: String, rating: Int, comment: String?) async throws -> MerchantBookingReviewResponse {
        try await authenticatedPost("api/v1/merchant/bookings/\(bookingId)/review", body: SubmitBookingReviewRequest(rating: rating, comment: comment))
    }
    public func getMyBookingReviews() async throws -> MerchantBookingReviewsResponse {
        try await get("api/v1/merchant/reviews/my-reviews", query: [URLQueryItem(name: "size", value: "50")])
    }

    // Real pre-booking browsing (item 231) -- found via a defined-but-uncalled-endpoint
    // sweep, both real fully-authorized backend endpoints with zero client callers
    // anywhere until now. bank-mfe/Android shipped this first (2026-08-05); this is the
    // iOS port. Reuses MerchantCouponPreviewDto (already exact-shape-identical, from the
    // payment-preview feature) rather than declaring a duplicate DTO.
    public func getMerchantReviews(merchantId: String) async throws -> MerchantReviewsWithRatingResponse {
        try await get("api/v1/merchant/\(merchantId)/reviews", query: [URLQueryItem(name: "size", value: "20")])
    }
    public func getCouponsForCustomer(merchantId: String) async throws -> MerchantCouponsForCustomerResponse {
        try await get("api/v1/merchant/\(merchantId)/coupons")
    }

    // Real "Coupon box" cross-merchant browse (itunda Pay redesign, 2026-08-28) --
    // itunda's first ever unscoped coupon read, see MerchantCouponService.
    // browseCoupons's own doc comment. Mirrors bank-mfe's lib/coupons.ts exactly.
    public func browseCoupons() async throws -> CouponBrowseResponse {
        try await get("api/v1/merchant/coupons/browse")
    }
    public func getMyCouponRedemptions() async throws -> CouponRedemptionsResponse {
        try await get("api/v1/merchant/coupons/my-redemptions")
    }

    // Real Membership-screen "Store points" row -- see MerchantLoyaltyPointsService.
    // getMyBalances's own doc comment. Mirrors bank-mfe's lib/coupons.ts exactly.
    public func getMyLoyaltyBalances() async throws -> LoyaltyBalancesResponse {
        try await get("api/v1/merchant/loyalty/my-balances")
    }

    // Real cross-merchant product search (item 138) -- see ProductSearchResultDto's
    // own doc comment.
    // businessType added 2026-08-25, same real isolation fix as getShopDeals above.
    public func searchProducts(_ query: String, businessType: String? = nil) async throws -> ProductSearchResponse {
        var path = "api/v1/shopping/products/search?q=\(query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query)"
        if let businessType { path += "&businessType=\(businessType)" }
        return try await get(path)
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
        try await authenticatedPostWithCode("api/v1/gift-vouchers/\(voucherId)/extend", body: EmptyBody())
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

    // Real "pay a merchant", mirrors bank-mfe's lib/shopping.ts collectPayment/
    // payByStaticQr and Android's ApiService.kt exactly. Real camera QR scanning
    // shipped into this app's PayByCodeCard/PayByStaticQrCard §237 (this comment used
    // to say "this app has no scanner" -- gone stale the moment that landed); manual
    // entry is the honest fallback alongside it now, not the only path.
    public func collectPayment(intentId: String, couponId: String? = nil) async throws -> CollectPaymentResultDto {
        try await authenticatedPost("api/v1/merchant/collect/\(intentId)", body: CollectPaymentRequest(couponId: couponId), idempotencyKey: UUID().uuidString)
    }
    // Real customer-presented payment code -- see CustomerPaymentCodeResponse's own
    // doc comment. No Idempotency-Key: this doesn't move money, it just mints a
    // short-lived code (matches bank-mfe's identical generateCustomerPaymentCode).
    public func generateCustomerPaymentCode(accountId: String? = nil) async throws -> CustomerPaymentCodeResponse {
        try await authenticatedPost("api/v1/merchant/pay/customer-code", body: GenerateCustomerPaymentCodeRequest(accountId: accountId))
    }
    // Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
    // PayByCodeCard's own doc comment previously named. bank-mfe/Android already have
    // this; this is the first iOS client.
    public func previewPaymentIntent(intentId: String) async throws -> PaymentIntentPreviewResponse {
        try await get("api/v1/merchant/intent/\(intentId)")
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

    public func getRewardTasks() async throws -> RewardTasksResponse { try await get("api/v1/rewards/tasks") }
    public func claimRewardTask(taskId: String) async throws -> ClaimRewardTaskResponse {
        try await authenticatedPost("api/v1/rewards/claim", body: ClaimRewardTaskRequest(taskId: taskId), idempotencyKey: UUID().uuidString)
    }

    // Real gap found 2026-09-04: OrderService.placeOrder has a rich, real checkout
    // validation surface (min order amount, insufficient stock, sold out, expired
    // surplus deal, invalid delivery address/quantity, merchant not accepting orders)
    // none of it checked client-side -- TalkScreen.errorMessage(422) is hardcoded to
    // "Insufficient funds for this order.", actively WRONG for a min-order-amount
    // failure, and its 409 bucket collapses 3 distinct real causes (out of stock,
    // sold out, deal expired) into one generic "already done" string.
    public func placeOrder(_ request: PlaceOrderRequest) async throws -> OrderDetailResponse {
        try await postP2p("api/v1/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    public func getMyOrders() async throws -> OrdersResponse { try await get("api/v1/orders/my-orders") }

    public func getOrder(_ orderId: String) async throws -> OrderDetailResponse { try await get("api/v1/orders/\(orderId)") }

    /// Real live rider-location tracking for Commerce orders (item 230) -- found via a
    /// defined-but-uncalled-endpoint sweep: mirrors getEatsRiderLocation exactly (same
    /// RiderLocationDto shape), but had zero client callers on any platform until now.
    /// bank-mfe/Android shipped this first (2026-08-05); this is the iOS port. Honest v1
    /// scope-down: OrderDto has no delivery-coordinate fields, so this is a single rider
    /// marker, not a route.
    public func getOrderRiderLocation(_ orderId: String) async throws -> EatsRiderLocationResponse {
        try await get("api/v1/orders/\(orderId)/rider-location")
    }

    /// Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    /// See rw.itunda.commerce.OrderService.cancelOrder's own doc comment.
    public func cancelOrder(_ orderId: String) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders/\(orderId)/cancel", body: EmptyBody())
    }

    /// Real post-delivery product reviews (2026-07-20) -- see OrderController.submitProductReview.
    public func submitProductReview(orderItemId: String, rating: Int, comment: String?) async throws -> ProductReviewResponse {
        try await authenticatedPost("api/v1/orders/items/\(orderItemId)/review", body: SubmitProductReviewRequest(rating: rating, comment: comment))
    }

    // Real gap found 2026-09-04: OrderReturnService throws 5 distinct real reasons
    // (not delivered yet, 7-day return window expired, already requested, invalid
    // reason code) across only 3 HTTP statuses -- TalkScreen.errorMessage's generic
    // per-status bucket collapses "not delivered" and "already requested" into the
    // same 409 text, and its 422 case ("Insufficient funds for this order.") is
    // actively WRONG for a return-window-expired response, which has nothing to do
    // with funds.
    public func requestOrderReturn(orderId: String, type: String, reasonCode: String, reasonNote: String?) async throws -> OrderReturnRequestResponse {
        try await authenticatedPostWithMessage("api/v1/orders/\(orderId)/return", body: RequestOrderReturnRequest(type: type, reasonCode: reasonCode, reasonNote: reasonNote))
    }

    public func getMyReturnRequests() async throws -> OrderReturnRequestsResponse {
        try await get("api/v1/orders/returns/my-requests")
    }

    /// Real merchant-side Commerce order fulfillment queue (item 234) -- found via a
    /// sibling-consistency audit against bank-mfe's own MerchantOrdersView/
    /// MerchantReturnQueueView, which have had this since before this session, and
    /// Android's own port (this same series, 2026-08-05). A real itunda user who also
    /// runs a merchant storefront could manage their store's orders on bank-mfe/Android
    /// but had zero client anywhere on iOS.
    public func getMerchantOrders() async throws -> OrdersResponse { try await get("api/v1/orders/merchant-orders") }

    public func updateOrderStatus(_ orderId: String, status: String) async throws -> OrderDetailResponse {
        try await authenticatedPost("api/v1/orders/\(orderId)/status", body: UpdateOrderStatusRequest(status: status))
    }

    public func getMerchantReturnQueue() async throws -> OrderReturnRequestsResponse {
        try await get("api/v1/orders/returns/merchant-queue")
    }

    public func decideOrderReturn(_ returnRequestId: String, approve: Bool) async throws -> OrderReturnRequestResponse {
        try await authenticatedPost("api/v1/orders/returns/\(returnRequestId)/decide", body: DecideOrderReturnRequest(approve: approve))
    }

    public func getProductRating(_ productId: String) async throws -> ProductRatingResponse {
        try await get("api/v1/orders/products/\(productId)/rating")
    }

    public func getProductReviews(_ productId: String) async throws -> ProductReviewsResponse {
        try await get("api/v1/orders/products/\(productId)/reviews")
    }

    // Real Coupang/Naver-style "helpful" idempotent toggle (2026-08-25) -- see
    // ProductReviewService.toggleHelpful's own doc comment on the backend.
    public func toggleProductReviewHelpful(_ reviewId: String) async throws -> ToggleHelpfulReviewResponse {
        try await authenticatedPost("api/v1/orders/reviews/\(reviewId)/helpful", body: EmptyBody())
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

    // Real "my questions across every product I've ever asked about" -- see
    // OrderController.getMyInquiries's own doc comment on the backend. The two
    // methods above only ever let a buyer ask/view a SINGLE product's own Q&A; this
    // is the first place on iOS a buyer can see every question they've ever asked,
    // across every product, in one list. Real gap found live (uncalled-endpoint
    // sweep, 2026-09-03): this endpoint existed with zero caller on iOS or bank-mfe
    // -- only Android had this wired, since 2026-08-04.
    public func getMyProductInquiries() async throws -> ProductInquiriesResponse {
        try await get("api/v1/orders/inquiries/my-questions")
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
    // rw.itunda.account.SubscriptionDetectionService's own doc comment. bank-mfe/Android
    // already have this; this is the first iOS client.
    public func getDetectedSubscriptions() async throws -> DetectedSubscriptionsResponse { try await get("api/v1/account/subscriptions") }

    // Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- the
    // customer-facing browse half (merchant-mfe owns the paid create/extend side).
    // bank-mfe/Android already have this; this is the first iOS client.
    public func getNearbyMerchantAds(latitude: Double, longitude: Double) async throws -> NearbyMerchantAdsResponse {
        try await get("api/v1/merchant/ads/nearby", query: [URLQueryItem(name: "latitude", value: "\(latitude)"), URLQueryItem(name: "longitude", value: "\(longitude)")])
    }

    public func getNearbyMerchants(latitude: Double, longitude: Double, radiusKm: Double = 5.0) async throws -> NearbyMerchantsResponse {
        try await get("api/v1/merchant/nearby", query: [URLQueryItem(name: "latitude", value: "\(latitude)"), URLQueryItem(name: "longitude", value: "\(longitude)"), URLQueryItem(name: "radiusKm", value: "\(radiusKm)")])
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
        try await postEatsOrder("api/v1/eats/orders", body: request, idempotencyKey: UUID().uuidString)
    }

    public func getMyEatsOrders() async throws -> EatsOrdersResponse { try await get("api/v1/eats/orders/my-orders") }

    // Real 배달의민족 함께주문 (Baemin "Together Order") -- see GroupEatsOrderDto's own
    // doc comment.
    public func createGroupEatsOrder(restaurantId: String, deliveryAddress: String) async throws -> GroupEatsOrderResponse {
        try await authenticatedPost("api/v1/eats/group-orders", body: CreateGroupEatsOrderRequest(restaurantId: restaurantId, deliveryAddress: deliveryAddress))
    }

    public func joinGroupEatsOrder(joinCode: String) async throws -> GroupEatsOrderResponse {
        try await authenticatedPost("api/v1/eats/group-orders/join", body: JoinGroupEatsOrderRequest(joinCode: joinCode))
    }

    public func getGroupEatsOrder(id: String) async throws -> GroupEatsOrderDetailResponse {
        try await get("api/v1/eats/group-orders/\(id)")
    }

    public func setGroupEatsOrderItems(id: String, items: [GroupEatsOrderItemRequest]) async throws -> GroupEatsOrderDetailResponse {
        try await authenticatedPost("api/v1/eats/group-orders/\(id)/items", body: SetGroupEatsOrderItemsRequest(items: items))
    }

    public func finalizeGroupEatsOrder(id: String) async throws -> FinalizeGroupEatsOrderResponse {
        try await authenticatedPost("api/v1/eats/group-orders/\(id)/finalize", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func cancelGroupEatsOrder(id: String) async throws -> GroupEatsOrderResponse {
        try await authenticatedPost("api/v1/eats/group-orders/\(id)/cancel", body: EmptyBody())
    }

    public func placeDineInOrder(_ request: PlaceDineInOrderRequest) async throws -> DineInOrderDetailResponse {
        try await postEatsOrder("api/v1/eats/dine-in/orders", body: request, idempotencyKey: UUID().uuidString)
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
    public func submitEatsReview(orderId: String, restaurantRating: Int, restaurantComment: String?, riderRating: Int, riderComment: String?, photoUrl: String? = nil, goodPoints: [String] = []) async throws -> EatsReviewResponse {
        try await authenticatedPost(
            "api/v1/eats/orders/\(orderId)/review",
            body: SubmitEatsReviewRequest(restaurantRating: restaurantRating, restaurantComment: restaurantComment, riderRating: riderRating, riderComment: riderComment, photoUrl: photoUrl, goodPoints: goodPoints)
        )
    }

    // Real Uber Eats post-delivery tip -- see EatsOrderDto.tipAmount's own doc comment.
    // Real Idempotency-Key required, matching tipRideDriver's own identical fix (2026-09-03).
    public func tipEatsOrderRider(orderId: String, amount: Double) async throws -> TipEatsOrderResponse {
        try await authenticatedPost("api/v1/eats/orders/\(orderId)/tip", body: TipEatsOrderRequest(amount: amount), idempotencyKey: UUID().uuidString)
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

    // Real Coupang/Naver-style "도움돼요" (helpful) idempotent toggle -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe/Android with zero iOS client until now -- found via a
    // cross-platform-parity check.
    public func toggleEatsReviewHelpful(_ reviewId: String) async throws -> ToggleEatsReviewHelpfulResponse {
        try await authenticatedPost("api/v1/eats/reviews/\(reviewId)/helpful", body: EmptyBody())
    }

    // Real 배달의민족 리뷰 신고하기 (report a review) -- see backend
    // EatsReviewService.reportReview's own doc comment. Genuinely NOT covered by the
    // generic HoodReportButton mechanism (no REVIEW target exists there), same
    // reasoning bank-mfe's own lib/eats.ts doc comment already established.
    public func reportEatsReview(_ reviewId: String, reason: String) async throws -> SuccessResponse {
        try await authenticatedPost("api/v1/eats/reviews/\(reviewId)/report", body: ReportEatsReviewRequest(reason: reason))
    }

    public func searchDeliveryAddress(_ query: String) async throws -> AddressSearchResponse {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/v1/eats/geocode/search"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "q", value: query)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(AddressSearchResponse.self, from: data)
    }

    /// Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    /// only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    // Real gap found 2026-09-04: EatsOrderService.cancelOrder's real message includes
    // the order's actual current status ("Only a PLACED order can be cancelled --
    // this order is already DELIVERED"), a dynamic detail TalkScreen.errorMessage(409)
    // can never reproduce with its generic "That's already been done" text.
    public func cancelEatsOrder(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPostWithMessage("api/v1/eats/orders/\(orderId)/cancel", body: EmptyBody())
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

    // Real gap found 2026-09-04: claimDelivery's 3 real 409 causes (rider not
    // available, delivery no longer available, rider already on another delivery)
    // all showed the identical generic TalkScreen.errorMessage(409) text.
    public func claimDelivery(_ orderId: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPostWithMessage("api/v1/eats/orders/\(orderId)/claim", body: EmptyBody())
    }

    public func updateRiderOrderStatus(_ orderId: String, status: String) async throws -> EatsOrderDetailResponse {
        try await authenticatedPostWithMessage("api/v1/eats/orders/\(orderId)/rider-status", body: UpdateEatsOrderStatusRequest(status: status))
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

    // Real gap found 2026-09-04: both membership subscribe endpoints' real
    // "Choose a real membership duration -- X, Y, Z days" message (dynamically listing
    // the actual valid tiers, EatsMembershipService/PlatformMembershipService's own
    // MEMBERSHIP_TIERS keys) would fall back to a generic 400 if a client duration
    // picker ever drifted out of sync with the backend's tier list.
    public func subscribeEatsMembership(days: Int) async throws -> EatsMembershipResponse {
        try await postP2p("api/v1/eats/membership/subscribe", body: SubscribeEatsMembershipRequest(days: days), idempotencyKey: UUID().uuidString)
    }

    public func getMyPlatformMembership() async throws -> PlatformMembershipResponse { try await get("api/v1/eats/platform-membership/me") }

    public func subscribePlatformMembership(days: Int) async throws -> PlatformMembershipResponse {
        try await postP2p("api/v1/eats/platform-membership/subscribe", body: SubscribePlatformMembershipRequest(days: days), idempotencyKey: UUID().uuidString)
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

    public func fundInvestmentAccount(amount: Double) async throws -> FundInvestmentResponse {
        try await authenticatedPost("api/v1/stocks/fund", body: FundInvestmentRequest(amount: amount), idempotencyKey: UUID().uuidString)
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

    // Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- see
    // SetPriceAlertRequest's own doc comment.
    public func getPriceAlert(stockId: String) async throws -> PriceAlertResponse {
        try await get("api/v1/stocks/\(stockId)/price-alert")
    }

    public func setPriceAlert(stockId: String, targetPrice: Double, direction: String) async throws -> SetPriceAlertResponse {
        try await authenticatedPost("api/v1/stocks/\(stockId)/price-alert", body: SetPriceAlertRequest(targetPrice: targetPrice, direction: direction))
    }

    public func clearPriceAlert(stockId: String) async throws -> SetPriceAlertResponse {
        try await authenticatedDelete("api/v1/stocks/\(stockId)/price-alert")
    }

    /// Real DELETE support -- every other authenticated call so far was GET/POST only,
    /// see `authenticatedPost`'s own doc comment for why the Idempotency-Key handling
    /// lives there; DELETE never needs one (removing an already-removed listing is
    /// naturally idempotent at the database level, unlike a real money-moving POST).
    func authenticatedDelete<Response: Decodable>(_ path: String) async throws -> Response {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "DELETE"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await dataWithRefresh(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { throw NetworkError.invalidResponse }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.httpError(statusCode: httpResponse.statusCode)
        }
        return try decoder.decode(Response.self, from: data)
    }

    /// Real query-param DELETE (section 168) -- `authenticatedDelete(_:)` above uses
    /// `appendingPathComponent`, which percent-encodes `?`/`=`/`&` and breaks a query
    /// string, same gotcha the query-param `get(_:query:)` overload above already
    /// worked around. clearRateAlert needs this because the backend takes the pair as
    /// query params, not a path segment.
    func authenticatedDelete<Response: Decodable>(_ path: String, query: [URLQueryItem]) async throws -> Response {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.filter { $0.value != nil && !($0.value!.isEmpty) }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "DELETE"
        if let token = KeychainTokenStore.shared.getAccessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await dataWithRefresh(for: request)
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
// Real "My assets" tab-by-tab redesign (2026-08-27, direct user reference: 3 real
// Toss "총자산" screenshots). See OverviewService.kt's own doc comments for exactly
// how each is sourced -- Car is raw count+purchase-price only (the live valuation
// math stays private to :vehicle's VehicleValuationService), Tax/Points are always
// real even at zero.
public struct OverviewCardsSummaryDto: Decodable { public let hasCard: Bool; public let last4: String?; public let design: String?; public let frozen: Bool? }
public struct OverviewVehicleSummaryDto: Decodable { public let vehicleCount: Int; public let totalPurchasePrice: Double }
public struct OverviewTaxSummaryDto: Decodable { public let totalPaid: Double; public let paymentCount: Int }
public struct OverviewPointsSummaryDto: Decodable { public let rewardsTotal: Double; public let payMoneyBalance: Double }
public struct OverviewResponse: Decodable {
    public let success: Bool
    public let netWorth: Double
    public let accounts: [AccountSummaryDto]
    public let savings: OverviewSavingsSummaryDto
    public let loans: OverviewLoansSummaryDto
    public let investments: OverviewInvestmentsSummaryDto
    public let insurance: OverviewInsuranceSummaryDto
    public let linkedAccounts: [LinkedAccountSummaryDto]
    public let cards: OverviewCardsSummaryDto
    public let vehicles: OverviewVehicleSummaryDto
    public let tax: OverviewTaxSummaryDto
    public let points: OverviewPointsSummaryDto
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
// (AUTO_TOPUP_SETTING_NOT_FOUND) if this account has no setting configured yet.
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
    public let accountId: String
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
public struct LoanAccountDto: Decodable, Identifiable { public let id: String; public let userId: String; public let accountId: String; public let offerId: String; public let principal: Double; public let outstanding: Double; public let interestRate: Double; public let status: String; public let disbursedAt: String }
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
    public let accountId: String
    public let creditLimit: Double
    public let drawnBalance: Double
    public let interestRate: Double
    public let status: String

    // Explicit memberwise init -- Swift doesn't synthesize a public one across module
    // boundaries, needed so App-side code can construct an updated copy after a
    // draw/repay response.
    public init(id: String, userId: String, accountId: String, creditLimit: Double, drawnBalance: Double, interestRate: Double, status: String) {
        self.id = id
        self.userId = userId
        self.accountId = accountId
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
    public let accountId: String
    public let creditLimit: Double
    public let currentBalance: Double
    public let status: String
    public let cycleDueAt: String?
    public let lastLateFeeAccrualAt: String?
    public let createdAt: String
    public let updatedAt: String

    // Explicit memberwise init -- same real cross-module-construction gotcha
    // OverdraftAccountDto's own doc comment already names.
    public init(id: String, userId: String, accountId: String, creditLimit: Double, currentBalance: Double, status: String, cycleDueAt: String?, lastLateFeeAccrualAt: String?, createdAt: String, updatedAt: String) {
        self.id = id
        self.userId = userId
        self.accountId = accountId
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
    // Real "분실신고"/"카드 해지하기"/"카드 재발급"/"카드 비밀번호 변경" fields
    // (2026-09-01, direct user-supplied Toss Bank card-management screenshots) --
    // see the backend's DebitCard.kt doc comment for why lost/closedAt are
    // deliberately separate, one-way states from `frozen`.
    public let lost: Bool
    public let closedAt: String?
    public let pinSet: Bool
    public let reissuedAt: String?
    public let issuedAt: String
    public let design: String
    public let spentToday: Double
    public let spentThisMonth: Double
    public let remainingToday: Double
    public let remainingThisMonth: Double

    // Explicit memberwise init -- Swift doesn't synthesize a public one across module
    // boundaries, same real gotcha OverdraftAccountDto's own doc comment already
    // documents.
    public init(id: String, last4: String, dailyLimit: Double, monthlyLimit: Double, frozen: Bool, lost: Bool, closedAt: String?, pinSet: Bool, reissuedAt: String?, issuedAt: String, design: String, spentToday: Double, spentThisMonth: Double, remainingToday: Double, remainingThisMonth: Double) {
        self.id = id
        self.last4 = last4
        self.dailyLimit = dailyLimit
        self.monthlyLimit = monthlyLimit
        self.frozen = frozen
        self.lost = lost
        self.closedAt = closedAt
        self.pinSet = pinSet
        self.reissuedAt = reissuedAt
        self.issuedAt = issuedAt
        self.design = design
        self.spentToday = spentToday
        self.spentThisMonth = spentThisMonth
        self.remainingToday = remainingToday
        self.remainingThisMonth = remainingThisMonth
    }
}
public struct CardResponse: Decodable { public let success: Bool; public let card: CardDto }
public struct SetCardPinRequest: Encodable { public let newPin: String; public let currentCredential: String }
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

// Real card-design picker (2026-08-27, direct user instruction: "update itunda bank
// with all those cards designs allowing users to choose from those designs... that's
// how toss does it too") -- `design` must be one of the 5 real ids CardDesigns.all
// (CardDesignPicker.swift has the full colorway list); the backend's own
// DebitCardDesign whitelist real-400s anything else.
public struct IssueCardRequest: Encodable { public let design: String }

// Real Kigali public-transit stored-value balance (2026-08-27) -- see the backend's
// TransitBalance.kt doc comment for the full sourced account of Kigali's real Tap&Go
// fare system (AC Group Ltd, Kigali Bus Services, Royal Express) and the honest
// boundary this simulates: itunda has no real partnership with any of them, so this is
// never named "Tap&Go" anywhere in this client.
public struct TransitBalanceDto: Decodable {
    public let balance: Double
    public let createdAt: String
}
public struct TransitBalanceResponse: Decodable { public let success: Bool; public let balance: TransitBalanceDto }
public struct TransitTripDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let operatorName: String
    public let fare: Double
    public let ledgerTransactionId: String
    public let createdAt: String

    private enum CodingKeys: String, CodingKey {
        case id, userId, fare, ledgerTransactionId, createdAt
        case operatorName = "operator"
    }
}
public struct TransitTripsResponse: Decodable { public let success: Bool; public let trips: [TransitTripDto]; public let totalElements: Int; public let totalPages: Int }
public struct TopUpTransitRequest: Encodable { public let amount: Double }
public struct TapFareRequest: Encodable { public let operatorName: String; public let fare: Double
    private enum CodingKeys: String, CodingKey { case operatorName = "operator"; case fare }
}
public struct TapFareResponse: Decodable { public let success: Bool; public let trip: TransitTripDto; public let balance: TransitBalanceDto }

// Real "agent collects a fare from a rider's own presented code" flow (2026-08-27,
// direct user follow-up: "for simplification we need nfc"). `code` is the same
// CustomerPaymentCode value generateCustomerPaymentCode already produces and
// MyPaymentCodeCard already shows as a QR -- reached here via a real
// NFCTagReaderSession read (Android riders only, see TransitCollectScreenView.swift's
// own doc comment) or a camera QR scan (any rider).
public struct TapFareByCodeRequest: Encodable {
    public let code: String
    public let operatorName: String
    public let fare: Double
    private enum CodingKeys: String, CodingKey { case code, fare; case operatorName = "operator" }
}
public struct TransitCollectResultDto: Decodable {
    public let operatorName: String
    public let fare: Double
    public let collectedAt: String
    private enum CodingKeys: String, CodingKey { case fare, collectedAt; case operatorName = "operator" }
}
public struct TapFareByCodeResponse: Decodable { public let success: Bool; public let collected: TransitCollectResultDto }

// Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
// can make pay for tax and moto as well"). `code` is the same CustomerPaymentCode
// value generateCustomerPaymentCode already produces -- see the backend's
// MotoFareService.kt doc comment for the full sourced account.
public struct CollectMotoFareRequest: Encodable { public let code: String; public let fare: Double }
public struct MotoFareCollectResultDto: Decodable { public let fare: Double; public let collectedAt: String }
public struct CollectMotoFareResponse: Decodable { public let success: Bool; public let collected: MotoFareCollectResultDto }
// Real gap found live (uncalled-endpoint sweep, 2026-08-29): the collect flow above
// existed with zero way for a driver to ever see what they'd collected -- the
// backend's own MotoFareController.getMyTripsAsDriver ("/earnings") had zero caller
// anywhere on any platform since the feature shipped 2026-08-27.
public struct MotoFareTripDto: Decodable, Identifiable { public let id: String; public let riderUserId: String; public let driverUserId: String; public let fare: Double; public let createdAt: String }
public struct MotoFareEarningsResponse: Decodable { public let success: Bool; public let trips: [MotoFareTripDto]; public let totalElements: Int; public let totalPages: Int }

public struct CreditScoreFactorDto: Decodable, Identifiable { public let name: String; public let points: Int; public let description: String; public var id: String { name } }
public struct CreditScoreResponse: Decodable { public let success: Bool; public let score: Int; public let factors: [CreditScoreFactorDto]; public let computedAt: String }

// Real actionable next-steps -- CreditScoreService.getImprovementSuggestions existed on
// the backend since 2026-07-26 with a real Android client (CreditScoreScreen.kt) but
// zero iOS UI until now (2026-08-16, found while checking whether this session's new
// backend-only "Card usage" factor would surface correctly on Android/iOS -- it does
// for the factor breakdown via the same generic ForEach(result.factors) pattern
// CreditScoreScreenView.swift already has, but iOS never got the suggestions half at
// all). Distinct from the factor breakdown above (what makes up your score today):
// this is what to do NEXT to raise it (action + real point gain + why), mirroring
// Android's exact DTO shape.
public struct CreditScoreSuggestionDto: Decodable, Identifiable { public let action: String; public let pointsGain: Int; public let description: String; public var id: String { action } }
public struct CreditScoreSuggestionsResponse: Decodable { public let success: Bool; public let suggestions: [CreditScoreSuggestionDto] }

// Real Karrot-Score-style numeric trust/reputation badge (item 152) -- distinct from
// the per-listing trustScores batch map used for seller/poster/lister badges on Hood
// cards. GET /api/v1/trust-score returns a user's own full factor breakdown,
// mirroring CreditScoreResponse's shape exactly. Found 2026-07-31 real on bank-mfe
// only, zero UI on Android/iOS despite that.
public struct TrustScoreFactorDto: Decodable, Identifiable { public let name: String; public let points: Int; public let description: String; public var id: String { name } }
public struct TrustScoreResponse: Decodable { public let success: Bool; public let score: Int; public let factors: [TrustScoreFactorDto]; public let computedAt: String }

// Real Itunda cash-agent operator console -- see AgentOperatorController.kt's own doc
// comment: "Store-facing API: the operator's JWT determines the agent; callers never
// supply an agent id." Distinct from the customer-facing withdrawal-code creation --
// this is the STAFF side, real till balance + real cash-in/cash-out + real till
// reconciliation. bank-mfe already has this (AgentOperatorView); this is the first
// native client (Android/iOS).
public struct AgentTillReconciliationDto: Decodable {
    public let id: String; public let agentId: String; public let businessDate: String
    public let expectedCash: Double; public let countedCash: Double; public let variance: Double
    public let submittedByUserId: String; public let status: String
    public let reviewedByUserId: String?; public let reviewNote: String?; public let reviewedAt: String?
    public let createdAt: String
}
public struct AgentTillSnapshotDto: Decodable {
    public let agentId: String; public let agentName: String; public let expectedCash: Double
    public let todayCashIn: Double; public let todayCashOut: Double
    public let reconciliation: AgentTillReconciliationDto?
}
public struct AgentActivityItemDto: Decodable, Identifiable {
    public let id: String; public let type: String; public let amount: Double
    public let receiptNumber: String; public let ledgerTransactionId: String; public let createdAt: String
}
public struct AgentTillResponse: Decodable { public let success: Bool; public let till: AgentTillSnapshotDto }
public struct AgentActivityResponse: Decodable { public let success: Bool; public let activity: [AgentActivityItemDto] }
public struct AgentCashInRequest: Encodable { public let accountNumber: String; public let amount: Double; public let receiptNumber: String
    public init(accountNumber: String, amount: Double, receiptNumber: String) { self.accountNumber = accountNumber; self.amount = amount; self.receiptNumber = receiptNumber }
}
public struct AgentCashOutRequest: Encodable { public let accountNumber: String; public let amount: Double; public let receiptNumber: String; public let authorizationCode: String
    public init(accountNumber: String, amount: Double, receiptNumber: String, authorizationCode: String) { self.accountNumber = accountNumber; self.amount = amount; self.receiptNumber = receiptNumber; self.authorizationCode = authorizationCode }
}
public struct AgentCashResultResponse: Decodable { public let success: Bool; public let newBalance: Double; public let operatorCommission: Double }
public struct SubmitAgentTillCountRequest: Encodable { public let countedCash: Double
    public init(countedCash: Double) { self.countedCash = countedCash }
}
public struct AgentTillReconciliationResponse: Decodable { public let success: Bool; public let reconciliation: AgentTillReconciliationDto }

// Real peer-to-peer agent float rebalancing marketplace -- see the backend's
// FloatMarketplaceService.kt doc comment for the full sourced account (a real
// documented top-2 operational challenge for mobile money agents, distinct from
// AgentOperatorController's admin-to-agent till funding). bank-mfe/Android already
// have this; this is the first iOS client.
public struct FloatListingDto: Decodable, Identifiable {
    public let id: String; public let agentId: String; public let amount: Double
    public let claimedAmount: Double; public let status: String; public let createdAt: String
}
public struct NearbyFloatListingDto: Decodable, Identifiable {
    public let listing: FloatListingDto; public let agentDisplayName: String; public let distanceKm: Double; public let remainingAmount: Double
    public var id: String { listing.id }
}
public struct FloatTransferRequestDto: Decodable, Identifiable {
    public let id: String; public let listingId: String; public let requestingAgentId: String; public let amount: Double
    public let status: String; public let transactionId: String?; public let createdAt: String
}
public struct PostFloatListingRequest: Encodable { public let amount: Double; public init(amount: Double) { self.amount = amount } }
public struct RequestFloatRequest: Encodable { public let amount: Double; public init(amount: Double) { self.amount = amount } }
public struct FloatListingResponse: Decodable { public let success: Bool; public let listing: FloatListingDto }
public struct FloatListingsResponse: Decodable { public let success: Bool; public let listings: [FloatListingDto] }
public struct NearbyFloatListingsResponse: Decodable { public let success: Bool; public let listings: [NearbyFloatListingDto] }
public struct FloatTransferRequestResponse: Decodable { public let success: Bool; public let request: FloatTransferRequestDto }
public struct FloatTransferRequestsResponse: Decodable { public let success: Bool; public let requests: [FloatTransferRequestDto] }

public struct CertificateDto: Decodable {
    public let id: String; public let userId: String; public let serialNumber: String; public let publicKeyBase64: String
    public let algorithm: String; public let status: String; public let issuedAt: String; public let expiresAt: String; public let revokedAt: String?
}
public struct IssueCertificateResponse: Decodable { public let success: Bool; public let certificate: CertificateDto; public let privateKey: String }
public struct MyCertificateResponse: Decodable { public let success: Bool; public let certificate: CertificateDto? }
public struct RevokeCertificateResponse: Decodable { public let success: Bool; public let certificate: CertificateDto }
// Real public certificate status/verify (2026-08-04) -- found via a fresh "defined
// but uncalled" endpoint sweep: real, working, deliberately unauthenticated endpoints
// (see CertificateController's own doc comment on why /status and /verify are
// permitAll, unlike /issue/-me/-revoke above) with zero client anywhere, including
// this app which already wires the other three. Ports the same fix already shipped
// on Android/bank-mfe.
public struct CertificateStatusResponse: Decodable { public let success: Bool; public let certificate: CertificateDto }
public struct VerifyCertificateSignatureRequest: Encodable { public let serialNumber: String; public let payload: String; public let signature: String }
public struct VerifyCertificateSignatureResponse: Decodable {
    public let success: Bool; public let signatureValid: Bool; public let certificateStatus: String; public let userId: String; public let serialNumber: String
}

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
    public let frozeAccountId: String?; public let dueBy: String; public let reviewedBy: String?; public let createdAt: String; public let resolvedAt: String?
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

// Real 1:1-chat split-bill request (2026-08-09) -- no participantUserIds field, unlike
// CreateSplitBillRequest above: the other person is fixed by the otherUserId path
// segment, since this endpoint is for exactly two people, not an existing group. See
// backend SplitBillController's CreateDirectSplitBillRequest's own doc comment.
public struct CreateDirectSplitBillRequest: Encodable {
    public let totalAmount: Double; public let description: String
    public let mode: String; public let ladderVarianceLevel: Int?
    public init(totalAmount: Double, description: String, mode: String = "EVEN", ladderVarianceLevel: Int? = nil) {
        self.totalAmount = totalAmount; self.description = description
        self.mode = mode; self.ladderVarianceLevel = ladderVarianceLevel
    }
}

public struct AddContactRequest: Encodable { public let name: String; public let bank: String?; public let phoneNumber: String }
public struct ContactDto: Decodable, Identifiable { public let id: String; public let userId: String; public let name: String; public let bank: String; public let acc: String; public let phoneNumber: String; public let color: String; public let letter: String }
public struct ContactsResponse: Decodable { public let success: Bool; public let contacts: [ContactDto] }
public struct AddContactResponse: Decodable { public let success: Bool; public let contact: ContactDto }

public struct AddGroupMemberRequest: Encodable { public let userId: String }

