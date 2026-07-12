import Foundation

enum MoneyActionResult {
    case success(String)
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

    func depositToSavingsGoal(goalId: String, amountRwf: Int) async -> MoneyActionResult {
        do {
            let response = try await NetworkClient.shared.depositToGoal(goalId: goalId, amount: Double(amountRwf))
            return .success(response.message)
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
