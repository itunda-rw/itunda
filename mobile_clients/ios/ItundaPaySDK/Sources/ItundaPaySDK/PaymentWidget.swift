import Foundation
import SwiftUI

public enum PaymentMethodType: String, CaseIterable {
    case mtnMobileMoney = "MTN Mobile Money"
    case airtelMoney = "Airtel Money"
    case card = "Credit/Debit Card"
}

public struct PaymentInfo {
    public let orderId: String
    public let orderName: String
    public let amount: Double
    public let currency: String
    public let customerName: String
    
    public init(orderId: String, orderName: String, amount: Double, currency: String = "RWF", customerName: String) {
        self.orderId = orderId
        self.orderName = orderName
        self.amount = amount
        self.currency = currency
        self.customerName = customerName
    }
}

public class PaymentWidget: ObservableObject {
    public let clientKey: String
    public let customerKey: String
    
    @Published public var amount: Double
    @Published public var currency: String = "RWF"
    
    public init(clientKey: String, customerKey: String, amount: Double = 0.0) {
        self.clientKey = clientKey
        self.customerKey = customerKey
        self.amount = amount
    }
    
    public func updateAmount(_ amount: Double) {
        self.amount = amount
    }
    
    public func requestPayment(
        info: PaymentInfo,
        paymentMethod: PaymentMethodType
    ) async throws -> PaymentResult {
        // Simulate network request for Rwanda local payment
        try await Task.sleep(nanoseconds: 1_500_000_000)
        
        let success = true // Mock payment processing
        
        if success {
            return PaymentResult.success(orderId: info.orderId, paymentKey: UUID().uuidString, amount: info.amount)
        } else {
            throw PaymentError.paymentFailed(reason: "Insufficient funds or cancelled by user.")
        }
    }
}

public enum PaymentResult {
    case success(orderId: String, paymentKey: String, amount: Double)
}

public enum PaymentError: Error {
    case paymentFailed(reason: String)
}
