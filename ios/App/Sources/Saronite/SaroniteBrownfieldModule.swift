import Foundation
import UIKit
import React
import CoreNetwork

/// Direct iOS port of Android's `SaroniteBrownfieldModule`/`ItundaSaroniteHostBridge`
/// (`android/app/.../miniapps/SaroniteBridge.kt`) -- itunda's own real, legacy
/// (`NativeModules`, not granite's TurboModule) bridge that mini-app JS actually calls for
/// data (`@itunda/saronite-react-native`'s `getPendingBills`/`payBill`/etc.), completely
/// separate from `GraniteBrownfieldModuleImpl` above (granite's own generic close/scheme
/// module). Same split exists on Android -- confirmed by reading `pay-bills/pages/index.tsx`'s
/// own header comment directly, not assumed.
///
/// Legacy `RCTBridgeModule`, not a TurboModule -- matches how the hand-written JS spec
/// (`packages/saronite/packages/brownfield-module/src/spec/SaroniteBrownfieldModule.ts`)
/// itself resolves it, via a plain `NativeModules.SaroniteBrownfieldModule` lookup, no
/// codegen. RN auto-discovers any class with a matching `RCT_EXTERN_MODULE` declaration
/// (the companion `.m` file) via the ObjC runtime purely by class name/selector matching --
/// unlike Android's explicit `ReactPackage` list requirement. Deliberately does NOT declare
/// `RCTBridgeModule` conformance in Swift: that protocol is real (confirmed present in the
/// installed `React-Core-prebuilt` xcframework header) but not resolvable as a Swift type in
/// this build (a real, unexplained Swift/ClangImporter gap distinct from the `RCTHost`
/// C++-visibility issue `SaroniteBrickBridge` works around) -- RN's own documented
/// `RCT_EXTERN_MODULE` pattern for Swift native modules never required this conformance
/// anyway, since the bridge discovers modules via ObjC runtime introspection on the class's
/// `@objc` selectors, not Swift protocol witness tables.
@objc(SaroniteBrownfieldModule)
final class SaroniteBrownfieldModule: NSObject {
    static func moduleName() -> String! { "SaroniteBrownfieldModule" }
    static func requiresMainQueueSetup() -> Bool { false }

    private let session = URLSession(configuration: .default)

    @objc func constantsToExport() -> [AnyHashable: Any]! {
        // The generic base scheme, matching Android's `ItundaSaroniteHostBridge.getSchemeUri()`
        // exactly -- distinct from `GraniteBrownfieldModuleImpl`'s per-mini-app scheme above.
        ["schemeUri": "itunda://saronite"]
    }

    @objc func closeView(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        DispatchQueue.main.async {
            SaroniteMiniAppViewController.current?.dismiss(animated: true)
            resolve(nil)
        }
    }

    // Real gap found 2026-08-08 (super-app mini-program research pass, comparing this
    // bridge against WeChat's own documented Mini Program model, which restricts
    // outbound navigation to a pre-declared domain allowlist): this took any URL string
    // and opened it with zero scheme/domain restriction -- an https link to anywhere,
    // or another installed app's custom scheme. Mirrors the identical fix on Android's
    // `SaroniteBridge.kt`'s own `openURL` -- same allowlist, same rationale (today's
    // real callers are itunda's own first-party mini-apps, but the bridge itself had no
    // structural defense if a lower-trust mini-app is ever loaded through Saronite
    // later). Note, honestly scoped smaller than the Android fix: Android's bridge also
    // gates every method behind a `requireScope`/`MiniAppSecurityContext` partner-scope
    // check that this iOS bridge has no equivalent of at all yet, on any method -- that
    // whole partner-scoping system is a real, separate, larger gap, not something to
    // build inside this one fix.
    @objc func openURL(_ url: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        guard let parsed = URL(string: url) else {
            reject("SARONITE_OPEN_URL_FAILED", "Invalid URL", nil)
            return
        }
        let host = parsed.host?.lowercased()
        let allowed: Bool
        switch parsed.scheme?.lowercased() {
        case "https": allowed = host != nil && (host == "itunda.rw" || host!.hasSuffix(".itunda.rw"))
        case "tel", "mailto": allowed = true
        default: allowed = false
        }
        guard allowed else {
            reject("SARONITE_OPEN_URL_DENIED", "This mini-app tried to open a URL outside itunda's allowed domains: \(url)", nil)
            return
        }
        DispatchQueue.main.async {
            UIApplication.shared.open(parsed, options: [:]) { success in
                success ? resolve(nil) : reject("SARONITE_OPEN_URL_FAILED", "Could not open URL", nil)
            }
        }
    }

    @objc func getWalletBalance(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/wallet", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            var totalBalance = 0.0
            var currency = "RWF"
            let wallets = ((root["wallets"] as? [[String: Any]]) ?? []).map { w -> [String: Any] in
                let balance = (w["balance"] as? NSNumber)?.doubleValue ?? 0
                let walletCurrency = w["currency"] as? String ?? currency
                totalBalance += balance
                currency = walletCurrency
                return [
                    "id": w["id"] as? String ?? "",
                    "type": w["type"] as? String ?? "",
                    "name": w["accountName"] as? String ?? "",
                    "number": w["accountNumber"] as? String ?? "",
                    "balance": balance,
                    "currency": walletCurrency,
                    "icon": "",
                    "connected": true,
                ]
            }
            return ["totalBalance": totalBalance, "currency": currency, "wallets": wallets]
        }
    }

    @objc func getPendingBills(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/bills/pending", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let bills = ((root["bills"] as? [[String: Any]]) ?? []).map { b -> [String: Any] in
                [
                    "id": b["id"] as? String ?? "",
                    "provider": b["provider"] as? String ?? "",
                    "amount": (b["amount"] as? NSNumber)?.doubleValue ?? 0,
                    "dueDate": b["dueDate"] as? String ?? "",
                    "status": b["status"] as? String ?? "",
                    "accountNumber": b["accountNumber"] as? String ?? "",
                ]
            }
            return ["bills": bills]
        }
    }

    @objc func payBill(
        _ billId: String,
        amount: NSNumber,
        accountNumber: String,
        provider: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let body: [String: Any] = ["billId": billId, "amount": amount, "accountNumber": accountNumber, "provider": provider]
        authorizedCall(path: "api/v1/bills/pay", method: "POST", body: body, resolve: resolve, reject: reject) { root in
            let transaction = root["transaction"] as? [String: Any]
            return [
                "message": root["message"] as? String ?? "Bill payment successful",
                "transactionId": transaction?["id"] as? String ?? "",
                "referenceNumber": transaction?["referenceNumber"] as? String ?? "",
                "status": transaction?["status"] as? String ?? "",
            ]
        }
    }

    @objc func getRewardTasks(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/rewards/tasks", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let tasks = ((root["tasks"] as? [[String: Any]]) ?? []).map { t -> [String: Any] in
                var task: [String: Any] = [
                    "id": t["id"] as? String ?? "",
                    "title": t["title"] as? String ?? "",
                    "subtitle": t["subtitle"] as? String ?? "",
                    "rewardAmount": (t["rewardAmount"] as? NSNumber)?.doubleValue ?? 0,
                    "claimed": t["claimed"] as? Bool ?? false,
                ]
                if let claimedAt = t["claimedAt"] as? String { task["claimedAt"] = claimedAt }
                return task
            }
            return ["tasks": tasks, "rewardsTotal": (root["rewardsTotal"] as? NSNumber)?.doubleValue ?? 0]
        }
    }

    @objc func claimRewardTask(_ taskId: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/rewards/claim", method: "POST", body: ["taskId": taskId], resolve: resolve, reject: reject) { root in
            [
                "message": root["message"] as? String ?? "Reward claimed",
                "rewardAmount": (root["rewardAmount"] as? NSNumber)?.doubleValue ?? 0,
                "newBalance": (root["newBalance"] as? NSNumber)?.doubleValue ?? 0,
            ]
        }
    }

    // Real Toss 만보기 (walking rewards) -- see Android's own SaroniteBridge.kt
    // reportSteps/getTodaySteps for the same doc comment on why `steps` is honestly a
    // manually-entered count, not a real CMPedometer reading (no sensor integration
    // exists on either native host app yet).
    // Real lottery-style bonus (item 248, docs/DESIGN_REFERENCES.md Section 15) --
    // lotteryBonusWonTiers/-WonAmount/-Total and tiers (the real, stated per-tier odds)
    // extended here so the mini-app can show the same disclosed-odds transparency
    // Android's own identical addition already has -- this bridge previously silently
    // dropped these fields since it only extracts what it explicitly names.
    private func stepRewardTiers(_ root: [String: Any]) -> [[String: Any]] {
        ((root["tiers"] as? [[String: Any]]) ?? []).map { tier in
            [
                "stepsRequired": (tier["stepsRequired"] as? NSNumber)?.intValue ?? 0,
                "rewardAmount": (tier["rewardAmount"] as? NSNumber)?.doubleValue ?? 0,
                "lotteryOdds": (tier["lotteryOdds"] as? NSNumber)?.doubleValue ?? 0,
                "lotteryBonusAmount": (tier["lotteryBonusAmount"] as? NSNumber)?.doubleValue ?? 0,
            ]
        }
    }

    @objc func reportSteps(_ steps: NSNumber, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/rewards/steps", method: "POST", body: ["steps": steps.intValue], resolve: resolve, reject: reject) { root in
            [
                "steps": (root["steps"] as? NSNumber)?.intValue ?? 0,
                "newlyEarnedTiers": (root["newlyEarnedTiers"] as? [NSNumber])?.map { $0.intValue } ?? [],
                "newlyEarnedAmount": (root["newlyEarnedAmount"] as? NSNumber)?.doubleValue ?? 0,
                "totalEarnedToday": (root["totalEarnedToday"] as? NSNumber)?.doubleValue ?? 0,
                "lotteryBonusWonTiers": (root["lotteryBonusWonTiers"] as? [NSNumber])?.map { $0.intValue } ?? [],
                "lotteryBonusWonAmount": (root["lotteryBonusWonAmount"] as? NSNumber)?.doubleValue ?? 0,
                "lotteryBonusTotal": (root["lotteryBonusTotal"] as? NSNumber)?.doubleValue ?? 0,
                "tiers": self.stepRewardTiers(root),
            ]
        }
    }

    @objc func getTodaySteps(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/rewards/steps/today", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            [
                "steps": (root["steps"] as? NSNumber)?.intValue ?? 0,
                "tiers": self.stepRewardTiers(root),
            ]
        }
    }

    @objc func getInsurancePlans(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/plans", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let plans = ((root["plans"] as? [[String: Any]]) ?? []).map { p -> [String: Any] in
                [
                    "id": p["id"] as? String ?? "",
                    "name": p["name"] as? String ?? "",
                    "category": p["category"] as? String ?? "",
                    "provider": p["provider"] as? String ?? "",
                    "monthlyPremium": (p["monthlyPremium"] as? NSNumber)?.doubleValue ?? 0,
                    "coverageAmount": (p["coverageAmount"] as? NSNumber)?.doubleValue ?? 0,
                    "description": p["description"] as? String ?? "",
                    "features": p["features"] as? [String] ?? [],
                    "rating": (p["rating"] as? NSNumber)?.doubleValue ?? 0,
                    "enrolledCount": (p["enrolledCount"] as? NSNumber)?.doubleValue ?? 0,
                    "color": p["color"] as? String ?? "",
                ]
            }
            return ["plans": plans]
        }
    }

    @objc func getMyPolicies(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/my-policies", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let policies = ((root["policies"] as? [[String: Any]]) ?? []).map(Self.mapPolicy)
            return ["policies": policies]
        }
    }

    @objc func enrollInsurance(_ planId: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/enroll", method: "POST", body: ["planId": planId], resolve: resolve, reject: reject) { root in
            var result: [String: Any] = ["message": root["message"] as? String ?? "Enrolled"]
            if let policy = root["policy"] as? [String: Any] { result["policy"] = Self.mapPolicy(policy) }
            return result
        }
    }

    // Real Ejo Heza ya Moto-style premium savings fund -- see Android's own SaroniteBridge.kt
    // createPremiumFund for the same doc comment. Row creation only, no Idempotency-Key
    // required by the backend, though authorizedCall attaches one anyway (harmless: the
    // backend's InsuranceController.createPremiumFund never reads that header).
    @objc func createPremiumFund(
        _ policyId: String,
        dailyContribution: NSNumber,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        authorizedCall(path: "api/v1/insurance/policies/\(policyId)/premium-fund", method: "POST", body: ["dailyContribution": dailyContribution], resolve: resolve, reject: reject, parse: Self.mapPremiumFundResult)
    }

    // Money movement -- backend requires a real Idempotency-Key header
    // (InsuranceController.contributeToFund's @RequestHeader), which authorizedCall
    // already attaches on every call with a body (same as enrollInsurance above).
    @objc func contributeToFund(
        _ fundId: String,
        amount: NSNumber,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        authorizedCall(path: "api/v1/insurance/premium-funds/\(fundId)/contribute", method: "POST", body: ["amount": amount], resolve: resolve, reject: reject, parse: Self.mapPremiumFundResult)
    }

    // Also money movement (refunds currentAmount back to the MAIN wallet) -- same real
    // Idempotency-Key requirement as contributeToFund. body: [:] (not nil), same reasoning
    // as requestEmailVerification above -- this is a real POST even with nothing to send.
    @objc func cancelFund(_ fundId: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/premium-funds/\(fundId)/cancel", method: "POST", body: [:], resolve: resolve, reject: reject, parse: Self.mapPremiumFundResult)
    }

    @objc func getMyPremiumFunds(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/premium-funds", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let funds = ((root["funds"] as? [[String: Any]]) ?? []).map(Self.mapPremiumFund)
            return ["success": root["success"] as? Bool ?? true, "funds": funds]
        }
    }

    // Real claims filing -- mirrors Android's SaroniteBridge.kt submitClaim/getMyClaims.
    // InsuranceController.submitClaim/getMyClaims existed on the backend with zero mobile
    // client on either platform: the insurance mini-app only ever surfaced plans/policies/
    // premium-funds. Not money-moving (only the ADMIN decide step pays out), so
    // authorizedCall's always-attached Idempotency-Key header is harmless but unused,
    // same as createPremiumFund above.
    @objc func submitClaim(
        _ policyId: String,
        description: String,
        amount: NSNumber,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        authorizedCall(path: "api/v1/insurance/claims", method: "POST", body: ["policyId": policyId, "description": description, "amount": amount], resolve: resolve, reject: reject) { root in
            var result: [String: Any] = ["success": root["success"] as? Bool ?? true]
            if let claim = root["claim"] as? [String: Any] { result["claim"] = Self.mapClaim(claim) }
            return result
        }
    }

    @objc func getMyClaims(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/claims", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let claims = ((root["claims"] as? [[String: Any]]) ?? []).map(Self.mapClaim)
            return ["success": root["success"] as? Bool ?? true, "claims": claims]
        }
    }

    // Real Rwanda NAIS-style parametric/weather-index crop insurance
    // (WeatherIndexInsuranceController) -- mirrors Android's SaroniteBridge.kt additions.
    // Structurally distinct from claims-based InsuranceController above: no individual
    // claim is ever filed, a published district+season rainfall index auto-triggers
    // payout for every enrolled policy at once. Had zero mobile client anywhere until now.
    @objc func getCropIndexCatalog(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/crop-index/catalog", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let catalog = ((root["catalog"] as? [[String: Any]]) ?? []).map { c -> [String: Any] in
                [
                    "cropType": c["cropType"] as? String ?? "",
                    "name": c["name"] as? String ?? "",
                    "premiumRatePercent": (c["premiumRatePercent"] as? NSNumber)?.doubleValue ?? 0,
                    "description": c["description"] as? String ?? "",
                ]
            }
            return ["success": root["success"] as? Bool ?? true, "catalog": catalog]
        }
    }

    @objc func getMyCropIndexPolicies(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/crop-index/policies", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            let policies = ((root["policies"] as? [[String: Any]]) ?? []).map(Self.mapCropIndexPolicy)
            return ["success": root["success"] as? Bool ?? true, "policies": policies]
        }
    }

    @objc func enrollCropIndexPolicy(
        _ cropType: String,
        district: String,
        season: String,
        insuredAmount: NSNumber,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        authorizedCall(
            path: "api/v1/insurance/crop-index/policies",
            method: "POST",
            body: ["cropType": cropType, "district": district, "season": season, "insuredAmount": insuredAmount],
            resolve: resolve,
            reject: reject
        ) { root in
            var result: [String: Any] = ["success": root["success"] as? Bool ?? true]
            if let policy = root["policy"] as? [String: Any] { result["policy"] = Self.mapCropIndexPolicy(policy) }
            return result
        }
    }

    // Money movement in reverse only if the policy hasn't already paid out --
    // WeatherIndexInsuranceService.cancel enforces that server-side; a real
    // Idempotency-Key is attached by authorizedCall (same as the fund endpoints above).
    @objc func cancelCropIndexPolicy(_ policyId: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/insurance/crop-index/policies/\(policyId)/cancel", method: "POST", body: [:], resolve: resolve, reject: reject) { root in
            var result: [String: Any] = ["success": root["success"] as? Bool ?? true]
            if let policy = root["policy"] as? [String: Any] { result["policy"] = Self.mapCropIndexPolicy(policy) }
            return result
        }
    }

    @objc func getCropIndexSeasonIndex(
        _ district: String,
        season: String,
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let allowed = CharacterSet.urlPathAllowed
        let encodedDistrict = district.addingPercentEncoding(withAllowedCharacters: allowed) ?? district
        let encodedSeason = season.addingPercentEncoding(withAllowedCharacters: allowed) ?? season
        authorizedCall(path: "api/v1/insurance/crop-index/districts/\(encodedDistrict)/seasons/\(encodedSeason)/index", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            var result: [String: Any] = ["success": root["success"] as? Bool ?? true]
            if let index = root["index"] as? [String: Any] {
                result["index"] = [
                    "district": index["district"] as? String ?? "",
                    "season": index["season"] as? String ?? "",
                    "rainfallIndexPercent": (index["rainfallIndexPercent"] as? NSNumber)?.doubleValue ?? 0,
                    "droughtThresholdPercent": (index["droughtThresholdPercent"] as? NSNumber)?.doubleValue ?? 0,
                    "publishedAt": index["publishedAt"] as? String ?? "",
                ]
            }
            return result
        }
    }

    @objc func getReferralInfo(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/rewards/referral", method: "GET", body: nil, resolve: resolve, reject: reject) { root in
            [
                // Explicit NSNull, not an omitted key -- ReferralInfo.referralCode is
                // typed `string | null` on the JS side, not optional/undefined.
                "referralCode": (root["referralCode"] as? String) ?? NSNull(),
                "referredCount": (root["referredCount"] as? NSNumber)?.intValue ?? 0,
                "completedReferralCount": (root["completedReferralCount"] as? NSNumber)?.intValue ?? 0,
            ]
        }
    }

    @objc func updateProfilePhoto(_ profilePhotoUrl: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/auth/profile/photo", method: "PUT", body: ["profilePhotoUrl": profilePhotoUrl], resolve: resolve, reject: reject, parse: Self.mapProfileResult)
    }

    // body: [:] (not nil) -- authorizedCall gates the real Content-Type/Idempotency-Key
    // headers on body being non-nil, and this is a real POST even with nothing to send.
    @objc func requestEmailVerification(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/auth/profile/verify-email", method: "POST", body: [:], resolve: resolve, reject: reject) { _ in [:] }
    }

    @objc func confirmEmailVerification(_ token: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        authorizedCall(path: "api/v1/auth/profile/verify-email/confirm", method: "POST", body: ["token": token], resolve: resolve, reject: reject, parse: Self.mapProfileResult)
    }

    @objc func addListener(_ eventName: String) {}
    @objc func removeListeners(_ count: NSNumber) {}

    // Real backend shape: services/backend/auth's PublicUser, nested under "user" in
    // both PUT /auth/profile/photo and POST /auth/profile/verify-email/confirm's real
    // response body -- only the two fields task_profile eligibility actually needs.
    private static func mapProfileResult(_ root: [String: Any]) -> [String: Any] {
        let user = (root["user"] as? [String: Any]) ?? root
        return [
            "profilePhotoUrl": (user["profilePhotoUrl"] as? String) ?? NSNull(),
            "emailVerified": user["emailVerified"] as? Bool ?? false,
        ]
    }

    private static func mapPolicy(_ p: [String: Any]) -> [String: Any] {
        [
            "id": p["id"] as? String ?? "",
            "planId": p["planId"] as? String ?? "",
            "planName": p["planName"] as? String ?? "",
            "category": p["category"] as? String ?? "",
            "status": p["status"] as? String ?? "",
            "startDate": p["startDate"] as? String ?? "",
            "endDate": p["endDate"] as? String ?? "",
            "monthlyPremium": (p["monthlyPremium"] as? NSNumber)?.doubleValue ?? 0,
            "nextPaymentDate": p["nextPaymentDate"] as? String ?? "",
            "policyNumber": p["policyNumber"] as? String ?? "",
        ]
    }

    // Real backend shape: InsuranceController.submitClaim/getMyClaims return the raw
    // InsuranceClaim entity (no remapping, unlike fundMap/policyMap above) -- field names
    // read directly from core/domain/InsuranceClaim.kt. status is one of
    // SUBMITTED/APPROVED/REJECTED; decisionReason is only set once an admin has decided it.
    private static func mapClaim(_ c: [String: Any]) -> [String: Any] {
        [
            "id": c["id"] as? String ?? "",
            "policyId": c["policyId"] as? String ?? "",
            "description": c["description"] as? String ?? "",
            "amount": (c["amount"] as? NSNumber)?.doubleValue ?? 0,
            "status": c["status"] as? String ?? "SUBMITTED",
            "submittedAt": c["submittedAt"] as? String ?? "",
            "decisionReason": (c["decisionReason"] as? String) ?? NSNull(),
        ]
    }

    // Real backend shape: WeatherIndexInsuranceController.policyMap -- read directly from
    // that controller, not guessed. status is one of ENROLLED/PAYOUT_TRIGGERED/
    // SEASON_ENDED_NO_PAYOUT/CANCELLED; payoutAt is null until a payout actually fires.
    private static func mapCropIndexPolicy(_ p: [String: Any]) -> [String: Any] {
        [
            "id": p["id"] as? String ?? "",
            "cropType": p["cropType"] as? String ?? "",
            "district": p["district"] as? String ?? "",
            "season": p["season"] as? String ?? "",
            "insuredAmount": (p["insuredAmount"] as? NSNumber)?.doubleValue ?? 0,
            "premiumAmount": (p["premiumAmount"] as? NSNumber)?.doubleValue ?? 0,
            "status": p["status"] as? String ?? "ENROLLED",
            "createdAt": p["createdAt"] as? String ?? "",
            "payoutAt": (p["payoutAt"] as? String) ?? NSNull(),
        ]
    }

    // Real backend shape: services/backend/insurance's InsuranceController.fundMap
    // (POST .../premium-fund, POST .../contribute, POST .../cancel, GET premium-funds)
    // -- read directly from InsuranceController.kt, not guessed.
    private static func mapPremiumFund(_ f: [String: Any]) -> [String: Any] {
        [
            "id": f["id"] as? String ?? "",
            "policyId": f["policyId"] as? String ?? "",
            "targetAmount": (f["targetAmount"] as? NSNumber)?.doubleValue ?? 0,
            "currentAmount": (f["currentAmount"] as? NSNumber)?.doubleValue ?? 0,
            "dailyContribution": (f["dailyContribution"] as? NSNumber)?.doubleValue ?? 0,
            "status": f["status"] as? String ?? "",
            "createdAt": f["createdAt"] as? String ?? "",
        ]
    }

    private static func mapPremiumFundResult(_ root: [String: Any]) -> [String: Any] {
        var result: [String: Any] = ["success": root["success"] as? Bool ?? true]
        if let fund = root["fund"] as? [String: Any] { result["fund"] = mapPremiumFund(fund) }
        return result
    }

    private func authorizedCall(
        path: String,
        method: String,
        body: [String: Any]?,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock,
        parse: @escaping ([String: Any]) -> [String: Any]
    ) {
        guard let token = KeychainTokenStore.shared.getAccessToken() else {
            reject("SARONITE_NOT_AUTHENTICATED", "No active itunda session", nil)
            return
        }
        guard let url = URL(string: NetworkClient.baseURLString + path) else {
            reject("SARONITE_INVALID_URL", "Invalid URL", nil)
            return
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let body = body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(UUID().uuidString, forHTTPHeaderField: "Idempotency-Key")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }

        session.dataTask(with: request) { data, response, error in
            if let error = error {
                reject("SARONITE_NETWORK_ERROR", error.localizedDescription, error)
                return
            }
            guard let http = response as? HTTPURLResponse, let data = data else {
                reject("SARONITE_NETWORK_ERROR", "No response", nil)
                return
            }
            guard (200..<300).contains(http.statusCode) else {
                let bodyString = String(data: data, encoding: .utf8) ?? ""
                reject("SARONITE_HTTP_ERROR", "itunda API returned \(http.statusCode): \(bodyString)", nil)
                return
            }
            guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
                reject("SARONITE_PARSE_ERROR", "Invalid JSON", nil)
                return
            }
            resolve(parse(root))
        }.resume()
    }
}
