//
//  PaymentWidget.swift
//  Ported from mobile_clients/ios/ItundaPaySDK (2026-07-10) into the canonical
//  SDK/Pay Tuist target -- see docs/ARCHITECTURE.md §3. In-app checkout widgets,
//  complementary to this file's existing ItundaPayments (the URL-scheme entry point
//  for third-party apps launching Itunda Payments).
//
//  NOT build-verified -- see ios/Core/Risk/Sources/ZeroTrust.swift for why.
//

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

    /// Not a real payment call -- always succeeds after a fixed delay, with no
    /// actual network request or provider integration. Ported as-is from the
    /// original mock rather than silently dressed up as real; do not wire this
    /// to a real checkout flow without replacing the body below. Repeating the
    /// always-succeeds pattern this repo's SECURITY.md and KeypadBottomSheet
    /// fixes were specifically about would be the same mistake twice.
    public func requestPayment(
        info: PaymentInfo,
        paymentMethod: PaymentMethodType
    ) async throws -> PaymentResult {
        try await Task.sleep(nanoseconds: 1_500_000_000)
        return PaymentResult.success(orderId: info.orderId, paymentKey: UUID().uuidString, amount: info.amount)
    }
}

public enum PaymentResult {
    case success(orderId: String, paymentKey: String, amount: Double)
}

public enum PaymentError: Error {
    case paymentFailed(reason: String)
}
