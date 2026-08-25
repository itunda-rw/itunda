import Foundation

// Real fix (2026-08-26): split out of NetworkClient.swift once that file grew past
// its file-size-lint baseline. Real Toss/KakaoBank scheduled-savings products --
// WeeklySavings (26주적금, scheduler-driven weekly installments) and Grow31Savings
// (키워봐요 31일적금, explicit daily deposit action) -- see each type's own doc
// comment below for the full sourced mechanics. Same module (CoreNetwork), so no
// import changes needed anywhere.


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
    public let accountId: String
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
    public let accountBalance: Double
    public let installments: [WeeklySavingsInstallmentDto]
}

public struct CreateWeeklySavingsPlanRequest: Encodable {
    public let name: String
    public let baseWeeklyAmount: Double
    public let escalationRate: Double
}

// Real bug found 2026-08-15 (same pass as the Idempotency-Key fix below): the real
// backend response for POST /weekly-savings/plans is just {success, plan} --
// WeeklySavingsController.create's own body literal is `mapOf("success" to true,
// "plan" to plan)`, no message/accountBalance/installments (those only exist on
// cancel/withdraw's response, see WeeklySavingsActionResponse below). createWeeklySavingsPlan
// used to declare WeeklySavingsActionResponse as its return type, whose `message`/
// `accountBalance`/`installments` are all non-optional -- meaning every successful
// creation would still throw a real DecodingError client-side (missing required
// keys) even after the Idempotency-Key header is sent correctly, silently reporting
// "couldn't reach itunda" for a plan that actually WAS created server-side. Matches
// Android's own correct split (CreateWeeklySavingsPlanResponse vs
// WeeklySavingsActionResponse in ApiService.kt) -- iOS had wrongly reused one type
// for both shapes.
public struct CreateWeeklySavingsPlanResponse: Decodable {
    public let success: Bool
    public let plan: WeeklySavingsPlanDto
}

public struct WeeklySavingsActionResponse: Decodable {
    public let success: Bool
    public let message: String
    public let plan: WeeklySavingsPlanDto
    public let accountBalance: Double
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

    // Real bug found 2026-08-15 (while porting Grow31 savings to iOS, checking this
    // file's own established idempotencyKey convention against every other
    // authenticatedPost call): these 3 calls never passed idempotencyKey, so the
    // param's `= nil` default meant the real, backend-required "Idempotency-Key"
    // header (WeeklySavingsController.create/cancel/withdraw all declare
    // `@RequestHeader("Idempotency-Key") idempotencyKey: String`, non-nullable) was
    // simply never sent -- every create/cancel/withdraw call on this screen has been
    // silently 400ing with IDEMPOTENCY_KEY_REQUIRED since it shipped. Fixed to match
    // every other real money-moving call in this file (e.g. depositYouthAccount,
    // contributeToIkimina).
    public func createWeeklySavingsPlan(name: String, baseWeeklyAmount: Double, escalationRate: Double) async throws -> CreateWeeklySavingsPlanResponse {
        try await authenticatedPost(
            "api/v1/weekly-savings/plans",
            body: CreateWeeklySavingsPlanRequest(name: name, baseWeeklyAmount: baseWeeklyAmount, escalationRate: escalationRate),
            idempotencyKey: UUID().uuidString
        )
    }

    public func cancelWeeklySavingsPlan(id: String) async throws -> WeeklySavingsActionResponse {
        try await authenticatedPost("api/v1/weekly-savings/plans/\(id)/cancel", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func withdrawWeeklySavingsPlan(id: String) async throws -> WeeklySavingsActionResponse {
        try await authenticatedPost("api/v1/weekly-savings/plans/\(id)/withdraw", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }
}

// Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent -- see
// Grow31SavingsService.kt's own doc comment for the full sourced mechanics. Distinct
// from WeeklySavings above: a deposit is an explicit daily user action ("Save
// today"), not a scheduler-driven installment. First iOS client (2026-08-15) -- had
// on Android since 2026-08-12 and bank-mfe since earlier the same session as this.
// Field shapes mirror Android's ApiService.kt Grow31SavingsPlanDto/etc exactly.
public struct Grow31SavingsPlanDto: Decodable, Identifiable {
    public let id: String
    public let userId: String
    public let accountId: String
    public let name: String
    public let dailyAmount: Double
    public let startDate: String
    public let daysElapsed: Int
    public let currentStreak: Int
    public let longestStreak: Int
    public let lastDepositDate: String?
    public let totalSaved: Double
    public let baseRate: Double
    public let status: String
    public let createdAt: String
    public let maturedAt: String?
    public let cancelledAt: String?
    public let withdrawnAt: String?
    public let totalInterestPaid: Double?
}

public struct Grow31SavingsDepositDto: Decodable, Identifiable {
    public let id: String
    public let planId: String
    public let dayNumber: Int
    public let depositDate: String
    public let amount: Double
    public let streakAtDeposit: Int
    public let depositedAt: String
}

public struct Grow31SavingsPlansResponse: Decodable { public let success: Bool; public let plans: [Grow31SavingsPlanDto] }

public struct Grow31SavingsPlanDetailResponse: Decodable {
    public let success: Bool
    public let plan: Grow31SavingsPlanDto
    public let accountBalance: Double
    public let deposits: [Grow31SavingsDepositDto]
}

public struct CreateGrow31SavingsPlanRequest: Encodable {
    public let name: String
    public let dailyAmount: Double
}

// Matches the real backend's create response shape exactly ({success, plan} only --
// see Grow31SavingsController.create's own body literal), same real lesson the
// WeeklySavingsActionResponse decode bug above just taught: don't reuse the fuller
// action-response type for create.
public struct CreateGrow31SavingsPlanResponse: Decodable {
    public let success: Bool
    public let plan: Grow31SavingsPlanDto
}

public struct Grow31SavingsActionResponse: Decodable {
    public let success: Bool
    public let message: String?
    public let plan: Grow31SavingsPlanDto
    public let accountBalance: Double
    public let deposits: [Grow31SavingsDepositDto]
}

// Real display-only constant (mirrors Grow31SavingsService's own TERM_DAYS) -- purely
// for display; the backend is the actual source of truth for every real number.
public enum Grow31SavingsConstants {
    public static let termDays = 31

    // Real, sourced tier table mirrored from Grow31SavingsService.bonusRateForStreak
    // on the backend (display-only copy, matching Android/bank-mfe's identical logic).
    public static func bonusRate(forStreak streak: Int) -> Double {
        if streak >= 31 { return 10 }
        if streak >= 21 { return 8 }
        if streak >= 14 { return 6 }
        if streak >= 7 { return 4 }
        if streak >= 3 { return 3 }
        return 0
    }
}

extension NetworkClient {
    public func getGrow31SavingsPlans() async throws -> Grow31SavingsPlansResponse { try await get("api/v1/grow31-savings/plans") }

    public func getGrow31SavingsPlan(id: String) async throws -> Grow31SavingsPlanDetailResponse {
        try await get("api/v1/grow31-savings/plans/\(id)")
    }

    public func createGrow31SavingsPlan(name: String, dailyAmount: Double) async throws -> CreateGrow31SavingsPlanResponse {
        try await authenticatedPost(
            "api/v1/grow31-savings/plans",
            body: CreateGrow31SavingsPlanRequest(name: name, dailyAmount: dailyAmount),
            idempotencyKey: UUID().uuidString
        )
    }

    public func depositGrow31SavingsToday(id: String) async throws -> Grow31SavingsActionResponse {
        try await authenticatedPost("api/v1/grow31-savings/plans/\(id)/deposit-today", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func cancelGrow31SavingsPlan(id: String) async throws -> Grow31SavingsActionResponse {
        try await authenticatedPost("api/v1/grow31-savings/plans/\(id)/cancel", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }

    public func withdrawGrow31SavingsPlan(id: String) async throws -> Grow31SavingsActionResponse {
        try await authenticatedPost("api/v1/grow31-savings/plans/\(id)/withdraw", body: EmptyBody(), idempotencyKey: UUID().uuidString)
    }
}
