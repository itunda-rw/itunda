import SwiftUI
import FeaturePaymentsInterface
import CoreDesignSystem

/// Toss-style Transfer Quote Screen
public struct TransferQuoteScreen: View {
    let recipientName: String
    let amount: String
    let fee: String
    let onConfirm: () -> Void
    let onCancel: () -> Void
    
    public init(recipientName: String, amount: String, fee: String, onConfirm: @escaping () -> Void, onCancel: @escaping () -> Void) {
        self.recipientName = recipientName
        self.amount = amount
        self.fee = fee
        self.onConfirm = onConfirm
        self.onCancel = onCancel
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Transfer to \(recipientName)")
                .font(TdsTypography.title1)
                .foregroundColor(TdsColors.gray900)
                .padding(.horizontal, 24)
                .padding(.top, 40)

            Spacer().frame(height: 32)

            TdsListRow(title: "Transfer Amount", rightText: "\(amount) RWF", action: {})

            TdsListRow(title: "Fee", rightText: fee == "0" ? "Free" : "\(fee) RWF", action: {})
            
            Spacer()
            
            HStack(spacing: 16) {
                TdsButton(text: "Cancel", action: onCancel)
                TdsButton(text: "Confirm & Send", action: onConfirm)
            }
            .padding(24)
        }
    }
}
