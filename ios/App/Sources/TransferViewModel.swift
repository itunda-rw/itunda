import Foundation

enum MoneyActionResult {
    case success(String)
    // Queued (2026-07-13) is distinct from success: the action wasn't executed yet,
    // only durably saved locally for replay once connectivity returns -- see
    // OfflineActionQueue.swift. Mirrors Android's MoneyActionResult.Queued exactly.
    case queued(String)
    case failure(String)
}

/// Real quote-then-confirm transfer + savings deposit/claim (2026-07-12) -- mirrors
/// Android's MainViewModel.sendTransfer/depositToSavingsGoal/claimInterest exactly.
/// Lives in the App target for the same reason BankViewModel does (Features/Payments
/// can't depend back on App's NetworkClient -- see BankView.swift's note).
@MainActor
final class TransferViewModel: ObservableObject {
    func sendTransfer(recipientAccountNumber: String, amountRwf: Int) async -> MoneyActionResult {
        do {
            let quote = try await NetworkClient.shared.quoteTransfer(amount: Double(amountRwf), recipient: recipientAccountNumber)
            let confirm = try await NetworkClient.shared.confirmTransfer(quoteId: quote.quote.id)
            return .success(confirm.message)
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
    /// queued, for the same 60-second-quote-expiry reason the backend's
    /// ActionsBatchController doesn't support a transfer action type at all.
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
