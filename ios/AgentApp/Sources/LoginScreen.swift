import SwiftUI
import CoreDesignSystem

/// Real agent-operator login, mirroring Android agentapp's own LoginScreen.kt exactly:
/// validates the role before leaving the sign-in screen (a normal consumer login must
/// not look like a usable cashier session) by requiring a real GET /api/v1/agent/me
/// to succeed, not just a successful password check.
struct LoginScreen: View {
    let onLoggedIn: () -> Void

    @State private var phoneNumber = ""
    @State private var password = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            Text("itunda").font(.largeTitle).bold()
            Text("Agent").font(.title2).foregroundColor(.secondary)
            Text("Use the itunda account assigned to this store.")
                .font(.subheadline).foregroundColor(.secondary)

            IdsTextField("Phone number", text: $phoneNumber, keyboardType: .phonePad)
            IdsTextField("Password", text: $password, isSecure: true)

            if let error {
                Text(error).foregroundColor(.red).font(.footnote)
            }

            Button(action: { Task { await login() } }) {
                Text(busy ? "Signing in…" : "Sign in")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            .disabled(busy)

            Spacer()
        }
        .padding(24)
    }

    private func login() async {
        guard !phoneNumber.isEmpty, !password.isEmpty else {
            error = "Enter your phone number and password."
            return
        }
        busy = true
        error = nil
        defer { busy = false }
        do {
            let res = try await AgentNetworkClient.shared.login(LoginRequest(phoneNumber: phoneNumber, password: password))
            AgentKeychainTokenStore.shared.save(accessToken: res.accessToken)
            // Validate the role before leaving the sign-in screen -- see this
            // struct's own doc comment.
            _ = try await AgentNetworkClient.shared.me()
            // Real push device-token registration -- best-effort, fire-and-forget: a
            // registration failure must never block an otherwise successful login.
            // See NetworkClient.swift's own doc comment.
            Task {
                _ = try? await AgentNetworkClient.shared.registerDeviceToken(
                    RegisterDeviceTokenRequest(platform: "IOS", token: AgentDeviceStore.shared.getOrCreateDeviceId())
                )
            }
            onLoggedIn()
        } catch {
            AgentKeychainTokenStore.shared.clear()
            self.error = "This account is not an active agent operator, or the service could not be reached."
        }
    }
}
