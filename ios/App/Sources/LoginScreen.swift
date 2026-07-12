import SwiftUI
import CoreDesignSystem

/// The login/register screen this app never had (see SessionManager.swift) --
/// gates ContentView in ItundaApp.swift behind a real authenticated session
/// instead of rendering the whole app unconditionally, mirroring Android's
/// LoginScreen.kt exactly against the same real
/// services/backend /api/v1/auth/register and /api/v1/auth/login endpoints.
struct LoginScreen: View {
    @ObservedObject var sessionManager: SessionManager

    @State private var isRegisterMode = false
    @State private var phoneNumber = ""
    @State private var password = ""
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            // Split across two Group blocks (2026-07-11, same fix as
            // BenefitsShopAllScreens.swift's EntireMenuScreen -- see that file's own
            // comment): this VStack has 12 direct children, and the Swift 5.8.1
            // toolchain this project builds against only supports ViewBuilder blocks
            // up to 10 children (the parameter-pack-based unlimited-children
            // ViewBuilder arrived in Swift 5.9). Group is purely a ViewBuilder
            // child-count workaround here -- it doesn't change layout.
            VStack(alignment: .leading, spacing: 12) {
                Group {
                    Spacer(minLength: 80)

                    Text("itunda")
                        .font(IDS.Typography.header)
                        .foregroundColor(IDS.Colors.textPrimary)
                    Text(isRegisterMode ? "Create your account" : "Log in to continue")
                        .font(IDS.Typography.bodyMedium)
                        .foregroundColor(IDS.Colors.textSecondary)

                    Spacer(minLength: 24)

                    if isRegisterMode {
                        TextField("First name", text: $firstName)
                            .textFieldStyle()
                        TextField("Last name", text: $lastName)
                            .textFieldStyle()
                    }

                    TextField("Phone number", text: $phoneNumber)
                        .keyboardType(.phonePad)
                        .textFieldStyle()

                    SecureField("Password", text: $password)
                        .textFieldStyle()

                    if let errorMessage {
                        Text(errorMessage)
                            .font(IDS.Typography.caption)
                            .foregroundColor(.red)
                    }

                    Spacer(minLength: 16)
                }

                Group {
                    if isSubmitting {
                        HStack {
                            Spacer()
                            ProgressView().tint(IDS.Colors.brand)
                            Spacer()
                        }
                        .padding(.vertical, 16)
                    } else {
                        TdsButton(
                            text: isRegisterMode ? "Create account" : "Log in",
                            isEnabled: canSubmit,
                            action: { submit() }
                        )
                    }

                    Button(action: { isRegisterMode.toggle(); errorMessage = nil }) {
                        Text(isRegisterMode ? "Already have an account? Log in" : "New to itunda? Create an account")
                            .font(IDS.Typography.bodyMedium)
                            .foregroundColor(IDS.Colors.textBrand)
                    }
                    .padding(.top, 8)

                    Spacer(minLength: 80)
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private var canSubmit: Bool {
        let baseFieldsFilled = !phoneNumber.isEmpty && !password.isEmpty
        return isRegisterMode ? baseFieldsFilled && !firstName.isEmpty && !lastName.isEmpty : baseFieldsFilled
    }

    private func submit() {
        errorMessage = nil
        isSubmitting = true
        Task {
            let result = isRegisterMode
                ? await sessionManager.register(phoneNumber: phoneNumber, password: password, firstName: firstName, lastName: lastName)
                : await sessionManager.login(phoneNumber: phoneNumber, password: password)
            isSubmitting = false
            if case let .failure(message) = result {
                errorMessage = message
            }
        }
    }
}

private extension View {
    func textFieldStyle() -> some View {
        self
            .font(IDS.Typography.bodyMedium)
            .padding(14)
            .background(IDS.Colors.backgroundSecondary)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(IDS.Colors.divider, lineWidth: 1))
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
    }
}
