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
                        // Real a11y fix (item 242, docs/ACCESSIBILITY.md §2's iOS follow-up,
                        // same fix as AgreementWidget.swift's checkbox icon): both this
                        // method icon and the trailing selection indicator below are hidden
                        // from accessibility -- SwiftUI's Button merges all subviews into one
                        // spoken label by default, so their raw SF Symbol names ("mobile
                        // phone fill" / "circle" / "checkmark circle fill") would otherwise
                        // get read out alongside method.rawValue as redundant noise. Selected
                        // state is now conveyed the standard way, via .isSelected below,
                        // instead of relying on which icon shape is showing.
                        Image(systemName: iconName(for: method))
                            .foregroundColor(selectedMethod == method ? .blue : .gray)
                            .frame(width: 32, height: 32)
                            .accessibilityHidden(true)

                        Text(method.rawValue)
                            .foregroundColor(.primary)

                        Spacer()

                        if selectedMethod == method {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.blue)
                                .accessibilityHidden(true)
                        } else {
                            Image(systemName: "circle")
                                .foregroundColor(.gray)
                                .accessibilityHidden(true)
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
                .accessibilityAddTraits(selectedMethod == method ? [.isSelected] : [])
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
