//
//  PaymentMethodWidget.swift
//  Ported from mobile_clients/ios/ItundaPaySDK (2026-07-10) -- see PaymentWidget.swift.
//

import SwiftUI

public struct PaymentMethodWidget: View {
    @ObservedObject var widget: PaymentWidget
    @Binding var selectedMethod: PaymentMethodType?

    public init(widget: PaymentWidget, selectedMethod: Binding<PaymentMethodType?>) {
        self.widget = widget
        self._selectedMethod = selectedMethod
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Select Payment Method")
                .font(.headline)
                .padding(.bottom, 8)

            ForEach(PaymentMethodType.allCases, id: \.self) { method in
                Button(action: {
                    selectedMethod = method
                }) {
                    HStack {
                        Image(systemName: iconName(for: method))
                            .foregroundColor(selectedMethod == method ? .blue : .gray)
                            .frame(width: 32, height: 32)

                        Text(method.rawValue)
                            .foregroundColor(.primary)

                        Spacer()

                        if selectedMethod == method {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.blue)
                        } else {
                            Image(systemName: "circle")
                                .foregroundColor(.gray)
                        }
                    }
                    .padding()
                    .background(Color(UIColor.secondarySystemBackground))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(selectedMethod == method ? Color.blue : Color.clear, lineWidth: 2)
                    )
                }
            }
        }
        .padding()
    }

    private func iconName(for method: PaymentMethodType) -> String {
        switch method {
        case .mtnMobileMoney:
            return "iphone.gen1.radiowaves.left.and.right"
        case .airtelMoney:
            return "wave.3.forward"
        case .card:
            return "creditcard"
        }
    }
}
