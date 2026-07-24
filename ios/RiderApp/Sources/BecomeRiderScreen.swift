import SwiftUI
import CoreDesignSystem

/// Shown once for any logged-in itunda user who hasn't registered as a rider yet.
struct BecomeRiderScreen: View {
    let onRegistered: () -> Void
    let onLogout: () -> Void

    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            Text("Become an itunda Rider").font(.title2).bold()
            Text("Deliver real Eats orders and get paid straight to your itunda wallet after every delivery.")
                .font(.subheadline).foregroundColor(.secondary)

            if let error {
                Text(error).foregroundColor(.red).font(.footnote)
            }

            Button(action: { Task { await register() } }) {
                Text(busy ? "Registering…" : "Become a rider")
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
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await RiderNetworkClient.shared.registerRider()
            onRegistered()
        } catch {
            self.error = "Couldn't register as a rider right now. Try again."
        }
    }
}
