import Foundation
import CoreNetwork

enum MoneyActionResult {
    case success(String)
    // Queued (2026-07-13) is distinct from success: the action wasn't executed yet,
    // only durably saved locally for replay once connectivity returns -- see
    // OfflineActionQueue.swift. Mirrors Android's MoneyActionResult.Queued exactly.
    case queued(String)
    case failure(String)
    // Real device binding (2026-07-21 port) -- a real 403 DEVICE_NOT_VERIFIED (this
    // device hasn't been step-up-verified yet) gets its own case, not a generic
    // failure, since the caller has a real, actionable next step (re-enter password,
    // then retry). Mirrors Android's MoneyActionResult.DeviceNotVerified exactly.
    case deviceNotVerified
}

/// Real direct P2P push-transfer + savings deposit/claim -- mirrors Android's
/// MainViewModel.sendTransfer/depositToSavingsGoal/claimInterest exactly. Lives in the
/// App target for the same reason BankViewModel does (Features/Payments can't depend
/// back on App's NetworkClient -- see BankView.swift's note).
///
/// `sendTransfer` switched 2026-07-20 from the previous quote-then-confirm
/// `quoteTransfer`/`confirmTransfer` pair (built 2026-07-12) to the new `sendDirect`
/// endpoint: those older endpoints always route through a simulated external rail and
/// never actually credit another itunda user's account, even when the recipient is a real
/// itunda account (confirmed via a direct MySQL check while building the real fix on the
/// backend one day earlier -- see SendDirectP2pRequest's own doc comment). No quote step
/// needed here, since there's no external rail decision to quote.
@MainActor
final class TransferViewModel: ObservableObject {
    // fromAccountId added 2026-08-31 (direct user reference of their own Toss app's
    // "which account should the money come from" picker) -- nil default keeps every
    // pre-existing caller sending from the sender's MAIN account, unchanged. See
    // NetworkClient.sendDirect's own doc comment.
    func sendTransfer(recipientAccountNumber: String, amountRwf: Int, memo: String = "", fromAccountId: String? = nil) async -> MoneyActionResult {
        do {
            let response = try await NetworkClient.shared.sendDirect(
                recipient: Self.normalizeRecipientIdentifier(recipientAccountNumber),
                amount: Double(amountRwf),
                memo: memo,
                fromAccountId: fromAccountId
            )
            return .success(response.message)
        } catch NetworkError.deviceNotVerified {
            return .deviceNotVerified
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            return .failure(message ?? Self.errorMessage(statusCode))
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    /// Real standalone "send as a gift" (found via an uncalled-endpoint sweep
    /// 2026-08-16, backend/bank-mfe docs Section 88) -- GiftController's own
    /// POST /api/v1/gifts, money moves into escrow immediately and only reaches the
    /// recipient once they claim it, unlike sendTransfer's instant push. Only resolves
    /// recipients by phone number (unlike sendDirect's phone-or-account-number lookup),
    /// so the identifier is normalized the same way sendTransfer's own account-number
    /// field would be if it happens to already be a phone number, then passed through.
    func sendGift(recipientPhoneNumber: String, amountRwf: Int, note: String?, theme: String?) async -> MoneyActionResult {
        do {
            _ = try await NetworkClient.shared.sendGift(
                recipientPhoneNumber: Self.normalizeRecipientIdentifier(recipientPhoneNumber),
                amount: Double(amountRwf),
                note: note,
                theme: theme
            )
            return .success("Gift sent! Held until they claim it -- auto-refunded after 7 days if unclaimed.")
        } catch NetworkError.deviceNotVerified {
            return .deviceNotVerified
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            return .failure(message ?? Self.errorMessage(statusCode))
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    /// Real phone-vs-account-number disambiguation for the numeric-keypad recipient
    /// screen (2026-07-20) -- mirrors Android's MainViewModel.
    /// normalizeRecipientIdentifier exactly. RecipientEntryScreen's real
    /// Toss-reference-matching design is a pure digit keypad with no "+" key, so a real
    /// Rwandan mobile number typed there arrives as raw digits ("0788000001" or
    /// "250788000001"), not the "+250XXXXXXXXX" form `User.phoneNumber` is actually
    /// stored in. Same real, sourced national-number shape `RailCatalog.
    /// resolveByPhoneNumber` already recognizes on the backend (Rwanda's RURA numbering
    /// plan) -- converged here to the canonical +250 form `sendDirect`'s exact-match
    /// phone lookup needs. A real itunda account number (always 10 digits starting
    /// 2024/2025) never matches either digit shape, so it passes through unchanged and
    /// still resolves via `sendDirect`'s own account-number fallback.
    private static func normalizeRecipientIdentifier(_ raw: String) -> String {
        let digits = raw.filter(\.isNumber)
        if digits.hasPrefix("0"), digits.count == 10 {
            return "+250" + digits.dropFirst()
        }
        if digits.hasPrefix("250"), digits.count == 12 {
            return "+" + digits
        }
        return raw
    }

    /// Real offline queueing (2026-07-13): on a genuine connectivity failure
    /// (URLError -- an HTTP error is a real backend response and is never queued,
    /// only ever surfaced as a real failure) the deposit intent is durably saved
    /// locally via OfflineActionQueue rather than dropped, and replayed
    /// automatically once BankViewModel's ConnectivityObserver reports a real
    /// network again. Mirrors Android's MainViewModel.depositToSavingsGoal exactly,
    /// including the same scope decision: sendTransfer above is intentionally never
    /// queued -- the backend's ActionsBatchController doesn't support a transfer
    /// action type at all, and a retried offline send should surface its own real
    /// error/idempotent-replay rather than being silently re-attempted later against
    /// whatever the sender's balance happens to be by then.
    func depositToSavingsGoal(goalId: String, amountRwf: Int) async -> MoneyActionResult {
        do {
            let response = try await NetworkClient.shared.depositToGoal(goalId: goalId, amount: Double(amountRwf))
            return .success(response.message)
        } catch NetworkError.deviceNotVerified {
            return .deviceNotVerified
        } catch let NetworkError.httpError(statusCode) {
            return .failure(Self.errorMessage(statusCode))
        } catch is URLError {
            OfflineActionQueue.shared.enqueueSavingsDeposit(goalId: goalId, amount: Double(amountRwf))
            return .queued("Saved offline -- this deposit will go through automatically once you're back online.")
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    // Real gap found live (2026-08-31, direct user re-reference of the real Toss
    // "얼마나 꺼낼까요?" (withdraw) screenshot) -- see backend SavingsService
    // .withdrawFromGoal's own doc comment for the full account. Not offline-queued,
    // unlike depositToSavingsGoal above -- the backend batch endpoint
    // (ActionsBatchController) has no SAVINGS_WITHDRAW action type yet.
    func withdrawFromSavingsGoal(goalId: String, amountRwf: Int) async -> MoneyActionResult {
        do {
            let response = try await NetworkClient.shared.withdrawFromGoal(goalId: goalId, amount: Double(amountRwf))
            return .success(response.message)
        } catch NetworkError.deviceNotVerified {
            return .deviceNotVerified
        } catch let NetworkError.httpError(statusCode) {
            return .failure(Self.errorMessage(statusCode))
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    func claimInterest() async -> MoneyActionResult {
        do {
            let response = try await NetworkClient.shared.claimInterest()
            return .success(response.message)
        } catch NetworkError.deviceNotVerified {
            return .deviceNotVerified
        } catch let NetworkError.httpError(statusCode) {
            return .failure(Self.errorMessage(statusCode))
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    // Real step-up re-verification (2026-07-21 port) -- re-proves password ownership
    // on THIS device (resolved server-side from the caller's own JWT deviceId claim)
    // and marks it trusted, matching bank-mfe's verifyDevice()/Android's
    // MainViewModel.verifyDevice exactly. The caller is expected to retry whatever
    // money-moving action returned .deviceNotVerified once this returns true.
    func verifyDevice(password: String) async -> MoneyActionResult {
        do {
            _ = try await NetworkClient.shared.verifyDevice(password: password)
            return .success("Device verified")
        } catch let NetworkError.httpError(statusCode) {
            let message = statusCode == 400 ? "Incorrect password." : "Something went wrong. Please try again."
            return .failure(message)
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    private static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 422: return "Insufficient funds for this amount."
        case 404: return "That account or goal couldn't be found."
        case 409: return "This request is already being processed."
        case 502: return "The payment provider declined this transaction."
        default: return "Something went wrong. Please try again."
        }
    }
}
