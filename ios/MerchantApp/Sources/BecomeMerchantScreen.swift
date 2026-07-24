import SwiftUI
import CoreDesignSystem

/// Shown once for any logged-in itunda user who hasn't registered a business yet.
struct BecomeMerchantScreen: View {
    let onRegistered: (MerchantDto) -> Void
    let onLogout: () -> Void

    @State private var businessName = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            Text("Register your business").font(.title2).bold()
            Text("Accept real payments, manage your menu, and handle incoming Eats orders from one app.")
                .font(.subheadline).foregroundColor(.secondary)

            TextField("Business name", text: $businessName)
                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(12)

            if let error {
                Text(error).foregroundColor(.red).font(.footnote)
            }

            Button(action: { Task { await register() } }) {
                Text(busy ? "Registering…" : "Register my business")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            .disabled(busy)

            Text("Log out")
                .foregroundColor(.blue)
                .frame(maxWidth: .infinity, alignment: .center)
                .onTapGesture(perform: onLogout)

            Spacer()
        }
        .padding(24)
    }

    private func register() async {
        guard !businessName.isEmpty else {
            error = "Enter your business name."
            return
        }
        busy = true
        error = nil
        defer { busy = false }
        do {
            let merchant = try await MerchantNetworkClient.shared.registerMerchant(businessName: businessName).merchant
            onRegistered(merchant)
        } catch {
            self.error = "Couldn't register your business right now. Try again."
        }
    }
}
