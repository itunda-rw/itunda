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

    @objc func openURL(_ url: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        guard let parsed = URL(string: url) else {
            reject("SARONITE_OPEN_URL_FAILED", "Invalid URL", nil)
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
