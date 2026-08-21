import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real KakaoBank mini-style capped starter account (rw.itunda.account.
// MiniAccountService, 2026-07-28) -- last remaining client platform for this feature
// (item 101; bank-mfe/Android already have it). Same no-ViewModel,
// "call NetworkClient.shared directly from Task {} blocks" convention as
// WeeklySavingsScreenView.swift.
private enum MiniAccountMode {
    case loading, needsBirthDate, notOpen, open
}

struct MiniAccountScreenView: View {
    var onBack: () -> Void = {}
    @State private var mode: MiniAccountMode = .loading
    @State private var account: Account?
    @State private var birthDate = ""
    @State private var amount = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Mini account").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    switch mode {
                    case .loading:
                        ProgressView().frame(maxWidth: .infinity).padding(40)
                    case .notOpen:
                        Text("A capped starter account for ages 7-18 -- a 500,000 RWF balance cap, 300,000 RWF daily and 2,000,000 RWF monthly deposit limits.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        MiniAccountActionButton(title: busy ? "Opening…" : "Open a Mini account", disabled: busy, action: openAccount)
                    case .needsBirthDate:
                        Text("Enter your birth date to check eligibility.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        IdsTextField("Birth date (YYYY-MM-DD)", text: $birthDate)
                        // Real CTA-label-clarity fix (item 244, docs/DESIGN_REFERENCES.md §11),
                        // matching the identical same-day fix on Android/web: "Continue" doesn't
                        // say what happens -- the text above already names the real outcome.
                        MiniAccountActionButton(title: busy ? "Checking…" : "Check eligibility", disabled: busy, action: submitBirthDateAndOpen)
                    case .open:
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(formatMoney(account?.balance ?? 0)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text(account?.accountNumber ?? "").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        IdsTextField("Amount (RWF)", text: $amount, keyboardType: .numberPad)
                        MiniAccountActionButton(title: busy ? "Adding…" : "Add money", disabled: busy, action: deposit)
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { load() }
    }

    private func load() {
        Task {
            do {
                let res = try await NetworkClient.shared.getAccounts()
                if let existing = res.accounts.first(where: { $0.type == "MINI" }) {
                    account = existing
                    mode = .open
                } else {
                    mode = .notOpen
                }
            } catch {
                mode = .notOpen
            }
        }
    }

    private func openAccount() {
        busy = true
        error = nil
        Task {
            do {
                let res = try await NetworkClient.shared.openMiniAccount()
                account = res.account
                mode = .open
            } catch NetworkError.miniAccountBirthDateRequired {
                mode = .needsBirthDate
            } catch NetworkError.miniAccountAgeIneligible {
                error = "Mini accounts are only available for ages 7-18."
            } catch {
                self.error = "Could not open a Mini account."
            }
            busy = false
        }
    }

    private func submitBirthDateAndOpen() {
        guard !birthDate.isEmpty else { return }
        busy = true
        error = nil
        Task {
            do {
                _ = try await NetworkClient.shared.setBirthDate(birthDate)
                busy = false
                openAccount()
            } catch {
                self.error = "Could not save your birth date."
                busy = false
            }
        }
    }

    private func deposit() {
        guard let parsedAmount = Double(amount), parsedAmount > 0 else {
            error = "Enter a real amount."
            return
        }
        busy = true
        error = nil
        Task {
            do {
                _ = try await NetworkClient.shared.depositMiniAccount(amount: parsedAmount)
                amount = ""
                load()
            } catch {
                self.error = "Could not add money to your Mini account."
            }
            busy = false
        }
    }
}

private struct MiniAccountActionButton: View {
    let title: String
    let disabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).bold().foregroundColor(.white)
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .background(IDS.Colors.brand).cornerRadius(12)
        }
        .disabled(disabled)
    }
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
