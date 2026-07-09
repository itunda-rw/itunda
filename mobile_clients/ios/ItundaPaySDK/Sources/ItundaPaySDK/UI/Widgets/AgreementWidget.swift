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
                    Image(systemName: isAgreed ? "checkmark.square.fill" : "square")
                        .foregroundColor(isAgreed ? .blue : .gray)
                        .font(.title3)
                    
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
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(12)
        }
        .padding()
    }
}
