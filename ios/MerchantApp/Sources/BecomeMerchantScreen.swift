import SwiftUI
import CoreDesignSystem

/// Shown once for any logged-in itunda user who hasn't registered a business yet.
///
/// Real gap found 2026-08-15 (backend AlreadyX audit, matches Android's identical
/// BecomeMerchantScreen.kt fix): a fresh install/reinstall has no local memory of a
/// prior registration, so this screen is reachable by an account that's already a
/// real registered merchant. That real, specific MERCHANT_ALREADY_REGISTERED backend
/// 409 used to be caught by a plain `catch` and replaced with a hardcoded "couldn't
/// register, try again" message -- a dead loop on every retry. Toss-style
/// resolve-forward: load the existing merchant profile and proceed, same as Android.
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

            IdsTextField("Business name", text: $businessName)

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
        } catch NetworkError.httpError(let statusCode) where statusCode == 409 {
            // MERCHANT_ALREADY_REGISTERED in practice -- this screen only ever
            // renders for a logged-in user with no LOCAL registration record, so the
            // only real 409 this endpoint can produce here is the account already
            // being registered server-side. Real Toss-style resolution: load the
            // existing profile and move forward instead of a dead-end error.
            do {
                let merchant = try await MerchantNetworkClient.shared.getMyMerchant().merchant
                onRegistered(merchant)
            } catch {
                self.error = "You're already registered, but we couldn't load your business right now. Try again."
            }
        } catch {
            self.error = "Couldn't register your business right now. Try again."
        }
    }
}
