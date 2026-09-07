import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real USSD basic-banking access (item 231, rw.itunda.ussd) -- the real menu, PIN
// check, and money transfer are fully built and working on the backend; only the real
// MNO/telco short-code partnership needed to dial *XXX# is missing, the same honest
// limitation ID verification has. This is the smartphone-side companion screen: a
// real, separate 4-6 digit PIN (not the account password, M-Pesa-style convention,
// see UssdPin.kt's own doc comment) a user sets here so they can later use any basic
// phone. First iOS client for this -- bank-mfe's UssdSettingsView.tsx shipped first;
// content/copy mirrored from it 1:1. Lives alongside SupportScreenView in this same
// module since both are account/help-adjacent settings screens.
public struct UssdSettingsScreenView: View {
    public var onBack: () -> Void
    public init(onBack: @escaping () -> Void = {}) { self.onBack = onBack }

    @State private var pin = ""
    @State private var confirmPin = ""
    @State private var submitting = false
    @State private var error: String?
    @State private var success = false

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("USSD access").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Roughly two-thirds of people in Rwanda have a feature phone, not a smartphone. Set a real 4-6 digit USSD PIN so you can check your balance and send money from any phone, no app or internet needed.")
                        .font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                    Text("Honestly scoped: the real menu, PIN check, and money transfer are fully built and working today. Dialing a short code like *123# to reach them needs a real partnership with a mobile network operator this project doesn't have yet -- the same honest limitation as our ID-verification integration.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    IdsTextField("New USSD PIN (4-6 digits)", text: $pin, isSecure: true, keyboardType: .numberPad)
                        .onChange(of: pin) { pin = String(pin.filter(\.isNumber).prefix(6)) }
                    IdsTextField("Confirm PIN", text: $confirmPin, isSecure: true, keyboardType: .numberPad)
                        .onChange(of: confirmPin) { confirmPin = String(confirmPin.filter(\.isNumber).prefix(6)) }
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if success { Text("Your USSD PIN has been set.").font(.caption).foregroundColor(IDS.Colors.success) }
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "Saving…" : "Set USSD PIN").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(submitting)
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func submit() async {
        guard pin.range(of: "^\\d{4,6}$", options: .regularExpression) != nil else {
            error = "PIN must be 4-6 digits."
            return
        }
        guard pin == confirmPin else {
            error = "PINs did not match."
            return
        }
        submitting = true
        error = nil
        success = false
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.setUssdPin(pin)
            pin = ""
            confirmPin = ""
            success = true
        } catch {
            self.error = "Could not save this PIN."
        }
    }
}
