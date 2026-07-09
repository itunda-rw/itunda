import Foundation

public enum IntentStatus: String, Codable {
    case draft
    case quoted
    case processing
    case completed
    case failed
}

/// An intent to transfer money. Toss principle: "Quote before confirmation".
public struct TransferIntent: Codable, Identifiable {
    public let id: String
    public let sourceAccountId: String
    public let recipient: String
    public let amount: Decimal
    public let currency: String
    public let fee: Decimal?
    public let status: IntentStatus
    
    public init(id: String, sourceAccountId: String, recipient: String, amount: Decimal, currency: String, fee: Decimal? = nil, status: IntentStatus) {
        self.id = id
        self.sourceAccountId = sourceAccountId
        self.recipient = recipient
        self.amount = amount
        self.currency = currency
        self.fee = fee
        self.status = status
    }
}

public protocol TransferService {
    /// Step 1: Create the intent and get a quote (verifying against ledger).
    func createQuote(sourceAccountId: String, recipient: String, amount: Decimal, currency: String) async throws -> TransferIntent
    
    /// Step 2: Confirm the transfer and execute it.
    func confirmTransfer(intentId: String) async throws -> String // Returns ledger transaction ID
}
