import SwiftUI

struct LoginScreen: View {
    let onLoggedIn: () -> Void

    @State private var phoneNumber = ""
    @State private var password = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            Text("Itunda Merchant").font(.largeTitle).bold()
            Text("Log in with your existing itunda account to run your shop.")
                .font(.subheadline).foregroundColor(.secondary)

            TextField("Phone number", text: $phoneNumber)
                .keyboardType(.phonePad)
                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(12)
            SecureField("Password", text: $password)
                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(12)

            if let error {
                Text(error).foregroundColor(.red).font(.footnote)
            }

            Button(action: { Task { await login() } }) {
                Text(busy ? "Logging in…" : "Log in")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Color.blue).cornerRadius(12)
            }
            .disabled(busy)

            Text("Don't have an itunda account yet? Register in the main itunda app first, then come back here to run your shop.")
                .font(.caption).foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)

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
            let res = try await MerchantNetworkClient.shared.login(LoginRequest(phoneNumber: phoneNumber, password: password))
            MerchantKeychainTokenStore.shared.saveSession(userId: res.user.id, accessToken: res.accessToken, refreshToken: res.refreshToken)
            onLoggedIn()
        } catch let NetworkError.httpError(statusCode) {
            error = statusCode == 401 ? "Incorrect phone number or password." : "Couldn't reach itunda. Try again."
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
