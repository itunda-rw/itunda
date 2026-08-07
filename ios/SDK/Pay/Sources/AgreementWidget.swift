//
//  AgreementWidget.swift
//  Ported from mobile_clients/ios/ItundaPaySDK (2026-07-10) -- see PaymentWidget.swift.
//

import SwiftUI

public struct AgreementWidget: View {
    @ObservedObject var widget: PaymentWidget
    @Binding var isAgreed: Bool

    public init(widget: PaymentWidget, isAgreed: Binding<Bool>) {
        self.widget = widget
        self._isAgreed = isAgreed
    }

    public var body: some View {
        VStack(alignment: .leading) {
            Text("Terms and Conditions")
                .font(.headline)
                .padding(.bottom, 8)

            Button(action: {
                isAgreed.toggle()
            }) {
                HStack(alignment: .top) {
                    // Real a11y fix (item 242, docs/ACCESSIBILITY.md §2's iOS follow-up,
                    // long left open for lack of a way to check): SwiftUI's Button already
                    // merges its subviews into one accessibility element by default, so
                    // this checkbox icon's own SF Symbol name ("checkmark square fill" /
                    // "square") would otherwise get spoken as part of the combined label --
                    // redundant noise alongside the text below. Hidden from accessibility;
                    // checked state is now conveyed the standard way, via .isSelected below.
                    Image(systemName: isAgreed ? "checkmark.square.fill" : "square")
                        .foregroundColor(isAgreed ? .blue : .gray)
                        .font(.title3)
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("I agree to all Terms and Conditions")
                            .font(.subheadline)
                            .foregroundColor(.primary)
                        Text("This includes the Privacy Policy and Itunda Pay Electronic Payment Service Terms.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer()
                }
            }
            .accessibilityAddTraits(isAgreed ? [.isSelected] : [])
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(12)
        }
        .padding()
    }
}
