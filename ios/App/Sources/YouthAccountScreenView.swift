import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real KakaoBank mini-style capped starter account (rw.itunda.account.
// YouthAccountService, 2026-07-28) -- last remaining client platform for this feature
// (item 101; bank-mfe/Android already have it). Same no-ViewModel,
// "call NetworkClient.shared directly from Task {} blocks" convention as
// WeeklySavingsScreenView.swift.
private enum YouthAccountMode {
    case loading, needsBirthDate, notOpen, open
}

struct YouthAccountScreenView: View {
    var onBack: () -> Void = {}
    @State private var mode: YouthAccountMode = .loading
    @State private var account: Account?
    @State private var birthDate = ""
    @State private var amount = ""
    @State private var busy = false
    @State private var error: String?
    // Real per-bucket detail screen (2026-08-31) -- see BucketDetailScreen.swift's
    // own doc comment. Youth Account never supports a withdraw (confirmed: no such
    // endpoint exists anywhere in this backend), so every one of its transactions is
    // a real deposit/credit -- isCredit is unconditionally true, no fromAccountId
    // comparison needed the way the primary account's own ledger requires.
    @State private var showHistory = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Youth account").font(.headline).foregroundColor(IDS.Colors.textPrimary)
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
                        YouthAccountActionButton(title: busy ? "Opening…" : "Open a Youth account", disabled: busy, action: openAccount)
                    case .needsBirthDate:
                        Text("Enter your birth date to check eligibility.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        IdsTextField("Birth date (YYYY-MM-DD)", text: $birthDate)
                        // Real CTA-label-clarity fix (item 244, docs/DESIGN_REFERENCES.md §11),
                        // matching the identical same-day fix on Android/web: "Continue" doesn't
                        // say what happens -- the text above already names the real outcome.
                        YouthAccountActionButton(title: busy ? "Checking…" : "Check eligibility", disabled: busy, action: submitBirthDateAndOpen)
                    case .open:
                        Button(action: { showHistory = true }) {
                            VStack(alignment: .leading, spacing: 4) {
                                CountUpText("\(formatMoney(account?.balance ?? 0)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                                Text(account?.accountNumber ?? "").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                        }
                        IdsTextField("Amount (RWF)", text: $amount, keyboardType: .numberPad)
                        YouthAccountActionButton(title: busy ? "Adding…" : "Add money", disabled: busy, action: deposit)
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { load() }
        .fullScreenCover(isPresented: $showHistory) {
            if let account {
                BucketDetailScreen(
                    title: "Youth Account",
                    subtitle: account.accountNumber,
                    balanceText: "\(formatMoney(account.balance)) RWF",
                    fetchTransactions: {
                        let txs = try await NetworkClient.shared.getAccountTransactionHistory(accountId: account.id).transactions
                            .sorted(by: { $0.createdAt > $1.createdAt })
                        var runningBalance = account.balance
                        return txs.map { tx in
                            let balanceAfter = runningBalance
                            runningBalance -= tx.amount
                            return BucketTransactionDto(id: tx.id, description: tx.description, amount: tx.amount, isCredit: true, balanceAfter: balanceAfter, createdAt: tx.createdAt)
                        }
                    },
                    onBack: { showHistory = false }
                )
            }
        }
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
                let res = try await NetworkClient.shared.openYouthAccount()
                account = res.account
                mode = .open
            } catch NetworkError.youthAccountBirthDateRequired {
                mode = .needsBirthDate
            } catch NetworkError.youthAccountAgeIneligible {
                error = "Youth accounts are only available for ages 7-18."
            } catch {
                self.error = "Could not open a Youth account."
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
                _ = try await NetworkClient.shared.depositYouthAccount(amount: parsedAmount)
                amount = ""
                load()
            } catch {
                self.error = "Could not add money to your Youth account."
            }
            busy = false
        }
    }
}

private struct YouthAccountActionButton: View {
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

