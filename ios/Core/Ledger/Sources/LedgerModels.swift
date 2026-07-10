import Foundation

public enum AccountType: String, Codable {
    case asset      // User's deposited money
    case liability  // Money the platform owes (e.g., to merchants)
    case equity     // Platform equity
    case revenue    // Fees collected
    case expense    // Platform expenses
}

/// Represents a single ledger account in the double-entry system.
public struct LedgerAccount: Codable, Identifiable {
    public let id: String
    public let customerId: String
    public let type: AccountType
    public let currency: String // e.g., "RWF"
    
    public let postedBalance: Decimal // Settled and cleared balance
    public let pendingBalance: Decimal // Outstanding holds or processing debits
    public let isActive: Bool
    
    public var availableBalance: Decimal {
        return postedBalance - pendingBalance
    }
    
    public init(id: String, customerId: String, type: AccountType, currency: String, postedBalance: Decimal, pendingBalance: Decimal, isActive: Bool) {
        self.id = id
        self.customerId = customerId
        self.type = type
        self.currency = currency
        self.postedBalance = postedBalance
        self.pendingBalance = pendingBalance
        self.isActive = isActive
    }
}

public enum PostingType: String, Codable {
    case debit
    case credit
}

public struct Posting: Codable, Identifiable {
    public let id: String
    public let accountId: String
    public let amount: Decimal
    public let currency: String
    public let type: PostingType
    
    public init(id: String, accountId: String, amount: Decimal, currency: String, type: PostingType) {
        self.id = id
        self.accountId = accountId
        self.amount = amount
        self.currency = currency
        self.type = type
    }
}

public enum TransactionStatus: String, Codable {
    case pending
    case completed
    case failed
    case reversed
}

public enum LedgerError: Error {
    case doubleEntryViolation(String)
}

/// An immutable record of a money movement in the double-entry ledger.
public struct LedgerTransaction: Codable, Identifiable {
    public let id: String
    public let idempotencyKey: String
    public let description: String
    public let timestamp: TimeInterval
    public let status: TransactionStatus
    public let postings: [Posting]
    
    public init(id: String, idempotencyKey: String, description: String, timestamp: TimeInterval, status: TransactionStatus, postings: [Posting]) throws {
        self.id = id
        self.idempotencyKey = idempotencyKey
        self.description = description
        self.timestamp = timestamp
        self.status = status
        self.postings = postings
        
        // Enforce the double-entry principle locally: Debits must equal Credits
        let totalDebits = postings.filter { $0.type == .debit }.reduce(0) { $0 + $1.amount }
        let totalCredits = postings.filter { $0.type == .credit }.reduce(0) { $0 + $1.amount }
        
        guard totalDebits == totalCredits else {
            throw LedgerError.doubleEntryViolation("Double-entry violation: Debits (\(totalDebits)) do not equal Credits (\(totalCredits)) for transaction \(id)")
        }
    }
}
