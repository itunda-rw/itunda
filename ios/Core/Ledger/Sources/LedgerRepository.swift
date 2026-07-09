import Foundation

/// Repository interface for interacting with the core ledger system.
public protocol LedgerRepository {
    
    /// Gets a real-time stream (using AsyncStream) of the user's primary accounts.
    func getAccounts(customerId: String) -> AsyncStream<[LedgerAccount]>
    
    /// Gets the transaction history for a specific account.
    func getTransactionHistory(accountId: String, limit: Int, offset: Int) async throws -> [LedgerTransaction]
    
    /// Verifies if a transfer intent is possible based on available balances and risk limits.
    /// In Toss, this is done before showing the final confirmation screen.
    func verifyTransferIntent(sourceAccountId: String, amount: Decimal, currency: String) async throws -> Bool
}
