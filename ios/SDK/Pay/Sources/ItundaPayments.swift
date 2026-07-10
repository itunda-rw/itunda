import Foundation
import UIKit

public class ItundaPayments {
    public static let shared = ItundaPayments()

    private let urlScheme = "itundapayments://process"

    private init() {}

    /// Call this method to open the Itunda app to process a payment.
    /// This is intended to be used by 3rd party apps integrating Itunda Payments.
    public func processPayment(amount: Double, merchantId: String, orderId: String) {
        guard let url = URL(string: "\(urlScheme)?amount=\(amount)&merchantId=\(merchantId)&orderId=\(orderId)") else { return }
        
        if UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        } else {
            // In a real SDK, you would redirect to the App Store
            print("Itunda app is not installed.")
        }
    }
}
