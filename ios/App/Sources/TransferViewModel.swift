import Foundation

enum MoneyActionResult {
    case success(String)
    // Queued (2026-07-13) is distinct from success: the action wasn't executed yet,
    // only durably saved locally for replay once connectivity returns -- see
    // OfflineActionQueue.swift. Mirrors Android's MoneyActionResult.Queued exactly.
    case queued(String)
    case failure(String)
}

/// Real direct P2P push-transfer + savings deposit/claim -- mirrors Android's
/// MainViewModel.sendTransfer/depositToSavingsGoal/claimInterest exactly. Lives in the
/// App target for the same reason BankViewModel does (Features/Payments can't depend
/// back on App's NetworkClient -- see BankView.swift's note).
///
/// `sendTransfer` switched 2026-07-20 from the previous quote-then-confirm
/// `quoteTransfer`/`confirmTransfer` pair (built 2026-07-12) to the new `sendDirect`
/// endpoint: those older endpoints always route through a simulated external rail and
/// never actually credit another itunda user's wallet, even when the recipient is a real
/// itunda account (confirmed via a direct MySQL check while building the real fix on the
/// backend one day earlier -- see SendDirectP2pRequest's own doc comment). No quote step
/// needed here, since there's no external rail decision to quote.
@MainActor
final class TransferViewModel: ObservableObject {
    func sendTransfer(recipientAccountNumber: String, amountRwf: Int) async -> MoneyActionResult {
        do {
            let response = try await NetworkClient.shared.sendDirect(recipient: recipientAccountNumber, amount: Double(amountRwf))
            return .success(response.message)
        } catch let NetworkError.httpError(statusCode) {
            return .failure(Self.errorMessage(statusCode))
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
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
        } catch let NetworkError.httpError(statusCode) {
            return .failure(Self.errorMessage(statusCode))
        } catch is URLError {
            OfflineActionQueue.shared.enqueueSavingsDeposit(goalId: goalId, amount: Double(amountRwf))
            return .queued("Saved offline -- this deposit will go through automatically once you're back online.")
        } catch {
            return .failure("Couldn't reach itunda. Check your connection and try again.")
        }
    }

    func claimInterest() async -> MoneyActionResult {
        do {
            let response = try await NetworkClient.shared.claimInterest()
            return .success(response.message)
        } catch let NetworkError.httpError(statusCode) {
            return .failure(Self.errorMessage(statusCode))
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
