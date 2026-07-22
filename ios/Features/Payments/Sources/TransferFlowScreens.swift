import SwiftUI
import CoreDesignSystem

/// Real, from-scratch transfer flow matching Android's TransferFlow.kt exactly
/// (2026-07-12) -- both against the same real Toss reference screenshots
/// (user-provided). Two screens:
///  - RecipientEntryScreen: "어떤 계좌로 보낼까요?" -- account number input, bank
///    label (no real detection -- see this file's own note below), numeric keypad.
///  - TransferAmountScreen: "얼마나 보낼까요?" -- from/to summary with a connector
///    line, quick-amount chips, a Next bar, and a keypad.
///
/// Dumb views taking plain primitives + callbacks, not NetworkClient/Wallet types
/// directly -- this Feature module can't depend back on App (see BankView.swift's
/// own note on the same constraint). TransferViewModel (App/Sources) owns the real
/// quoteTransfer/confirmTransfer calls and is the only caller.

// Real saved-contacts list (2026-07-22) -- see rw.itunda.contacts.ContactsController's
// own doc comment on the backend. This tiny UI-facing shape (not NetworkClient's own
// ContactDto) keeps this Feature module's existing independence from App's
// NetworkClient -- the actual fetch/add calls happen in TransferFlowContainer.swift
// (App/Sources), which already has API access, and are passed down here as plain
// data + callbacks.
public struct ContactUi: Identifiable {
    public let id: String
    public let name: String
    public let phoneNumber: String
    public let bank: String
    public init(id: String, name: String, phoneNumber: String, bank: String) {
        self.id = id; self.name = name; self.phoneNumber = phoneNumber; self.bank = bank
    }
}

public struct RecipientEntryScreen: View {
    @State private var accountNumber = ""
    @State private var showAddContact = false
    @State private var newContactName = ""
    @State private var newContactPhone = ""
    let onBack: () -> Void
    let contacts: [ContactUi]
    let onAddContact: (String, String) -> Void
    let onNext: (String) -> Void

    public init(
        onBack: @escaping () -> Void,
        contacts: [ContactUi] = [], onAddContact: @escaping (String, String) -> Void = { _, _ in },
        onNext: @escaping (String) -> Void
    ) {
        self.onBack = onBack
        self.contacts = contacts
        self.onAddContact = onAddContact
        self.onNext = onNext
    }

    public var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 0) {
                Text("Which account should\nwe send to?")
                    .font(IDS.scaledFont(size: 26, weight: .bold, relativeTo: .title1))
                    .foregroundColor(IDS.Colors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: 28)

                // Copy widened 2026-07-20: this same digit keypad now also accepts a
                // real phone number (a local "07XXXXXXXX" or international
                // "2507XXXXXXXX" shape is recognized and normalized client-side -- see
                // TransferViewModel.normalizeRecipientIdentifier), not just an account
                // number, now that TransferViewModel.sendTransfer calls the real
                // rw.itunda.p2p.sendDirect.
                Text("Enter phone or account number")
                    .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .footnote))
                    .foregroundColor(IDS.Colors.brand)
                Spacer().frame(height: 6)

                TextField("", text: $accountNumber)
                    .font(IDS.scaledFont(size: 22, weight: .semibold, relativeTo: .title3))
                    .foregroundColor(IDS.Colors.textPrimary)
                    .keyboardType(.numberPad)
                    .accessibilityLabel("Phone or account number, up to 16 digits")
                    .onChange(of: accountNumber) { newValue in
                        accountNumber = String(newValue.filter(\.isNumber).prefix(16))
                    }
                Spacer().frame(height: 8)
                Rectangle().fill(IDS.Colors.brand).frame(height: 2)

                Spacer().frame(height: 28)

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Select bank")
                            .font(.system(size: 17))
                            .foregroundColor(IDS.Colors.textTertiary)
                        // Real Toss auto-detects the bank from a real BIN registry;
                        // itunda has none to check against, so this doesn't claim to
                        // (2026-07-12, matching Android's RecipientEntryScreen fix).
                        if accountNumber.isEmpty {
                            Text("Optional, for your own reference")
                                .font(.system(size: 13))
                                .foregroundColor(IDS.Colors.textTertiary)
                        }
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundColor(IDS.Colors.textTertiary)
                }
                .padding(.vertical, 14)

                // Real saved contacts (2026-07-22), replacing the single hardcoded demo
                // row this section used to show -- see ContactUi's own doc comment
                // above; GET /api/v1/contacts had zero client UI anywhere until now.
                if accountNumber.isEmpty {
                    Spacer().frame(height: 28)
                    HStack {
                        Text("Contacts")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(IDS.Colors.textSecondary)
                        Spacer()
                        Button(showAddContact ? "Cancel" : "+ Add") { showAddContact.toggle() }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(IDS.Colors.brand)
                    }
                    Spacer().frame(height: 12)
                    if showAddContact {
                        VStack(alignment: .leading, spacing: 8) {
                            TextField("Name", text: $newContactName)
                                .padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            TextField("Phone number", text: $newContactPhone)
                                .keyboardType(.phonePad)
                                .padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            Button("Save contact") {
                                onAddContact(newContactName, newContactPhone)
                                newContactName = ""; newContactPhone = ""; showAddContact = false
                            }
                            .disabled(newContactName.isEmpty || newContactPhone.isEmpty)
                            .font(.system(size: 14, weight: .semibold))
                        }
                        Spacer().frame(height: 12)
                    }
                    if contacts.isEmpty && !showAddContact {
                        Text("No saved contacts yet.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    ForEach(contacts) { contact in
                        RecentRecipientRow(name: contact.name, bankAndAccount: "\(contact.bank) - \(contact.phoneNumber)") {
                            accountNumber = String(contact.phoneNumber.filter(\.isNumber).prefix(16))
                        }
                    }
                }
            }
            .padding(.horizontal, 24)

            Spacer()

            if accountNumber.count >= 4 {
                FlowNextBar(enabled: true, label: "Next") { onNext(accountNumber) }
            }
            NumericKeypad(
                onDigit: { d in if accountNumber.count < 16 { accountNumber += d } },
                onDelete: { if !accountNumber.isEmpty { accountNumber.removeLast() } }
            )
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

public struct TransferAmountScreen: View {
    @State private var digits = ""
    let recipientAccountNumber: String
    let availableBalance: Double
    let isSubmitting: Bool
    let onBack: () -> Void
    let onConfirm: (Int) -> Void

    public init(
        recipientAccountNumber: String,
        availableBalance: Double = 0,
        isSubmitting: Bool = false,
        onBack: @escaping () -> Void,
        onConfirm: @escaping (Int) -> Void
    ) {
        self.recipientAccountNumber = recipientAccountNumber
        self.availableBalance = availableBalance
        self.isSubmitting = isSubmitting
        self.onBack = onBack
        self.onConfirm = onConfirm
    }

    private var amount: Int { Int(digits) ?? 0 }

    public var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 6) {
                TransferPartyRow(label: "From Itunda Wallet", sublabel: "Available RWF \(formatAmount(Int(availableBalance)))", symbol: "creditcard")
                Rectangle().fill(IDS.Colors.divider).frame(width: 2, height: 20).padding(.leading, 21)
                TransferPartyRow(label: "To account \(recipientAccountNumber)", sublabel: "New recipient", symbol: "leaf")
            }
            .padding(.horizontal, 24)

            Spacer().frame(height: 40)

            VStack(spacing: 16) {
                Text("How much to send?")
                    .font(.system(size: 16))
                    .foregroundColor(IDS.Colors.textSecondary)
                Text(digits.isEmpty ? "0 RWF" : "\(formatAmount(amount)) RWF")
                    .font(.system(size: digits.isEmpty ? 32 : 42, weight: .bold))
                    .foregroundColor(digits.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.textPrimary)
            }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity)

            HStack(spacing: 10) {
                QuickAmountChip(label: "+10,000") { digits = String((Int(digits) ?? 0) + 10_000) }
                QuickAmountChip(label: "+100,000") { digits = String((Int(digits) ?? 0) + 100_000) }
                QuickAmountChip(label: "Max") { digits = String(Int(availableBalance)) }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 12)

            if isSubmitting {
                ProgressView()
                    .tint(IDS.Colors.brand)
                    .padding(.vertical, 24)
            } else {
                FlowNextBar(enabled: !digits.isEmpty && amount > 0, label: "Send") { onConfirm(amount) }
                NumericKeypad(
                    onDigit: { d in if digits.count < 9 { digits += d } },
                    onDelete: { if !digits.isEmpty { digits.removeLast() } }
                )
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

public enum SavingsAmountMode {
    case deposit
    case claimInterest
}

/// Real savings deposit/claim amount screens, matching the two real Toss reference
/// screenshots (user-provided, 2026-07-12): "얼마나 채울까요?" (deposit) and "얼마나
/// 꺼낼까요?" (withdraw). Same visual shape as TransferAmountScreen above, reusing
/// its private FlowTopBar/TransferPartyRow-equivalent pieces directly since they
/// live in this same file. itunda has no real withdraw-from-goal endpoint (only
/// deposit + interest-jar claim, see SavingsController.kt) -- a withdraw mode is
/// deliberately not offered here rather than faked against an endpoint that
/// doesn't exist, same reasoning as Android's SavingsAmountScreen.kt.
public struct SavingsAmountScreen: View {
    @State private var digits = ""
    let goalName: String
    let mode: SavingsAmountMode
    let availableBalance: Double
    let isSubmitting: Bool
    let onBack: () -> Void
    let onConfirm: (Int) -> Void

    public init(
        goalName: String,
        mode: SavingsAmountMode,
        availableBalance: Double,
        isSubmitting: Bool,
        onBack: @escaping () -> Void,
        onConfirm: @escaping (Int) -> Void
    ) {
        self.goalName = goalName
        self.mode = mode
        self.availableBalance = availableBalance
        self.isSubmitting = isSubmitting
        self.onBack = onBack
        self.onConfirm = onConfirm
    }

    private var amount: Int { Int(digits) ?? 0 }

    public var body: some View {
        VStack(spacing: 0) {
            FlowTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 6) {
                TransferPartyRow(label: "From Itunda Wallet", sublabel: "Available RWF \(formatAmount(Int(availableBalance)))", symbol: "creditcard")
                Rectangle().fill(IDS.Colors.divider).frame(width: 2, height: 20).padding(.leading, 21)
                TransferPartyRow(label: "To \(goalName)", sublabel: mode == .deposit ? "Savings goal" : "Interest jar", symbol: "leaf")
            }
            .padding(.horizontal, 24)

            Spacer().frame(height: 40)

            VStack(spacing: 16) {
                Text(mode == .deposit ? "How much to save?" : "Claim your interest")
                    .font(.system(size: 16))
                    .foregroundColor(IDS.Colors.textSecondary)
                Text(digits.isEmpty ? "0 RWF" : "\(formatAmount(amount)) RWF")
                    .font(.system(size: digits.isEmpty ? 32 : 42, weight: .bold))
                    .foregroundColor(digits.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.textPrimary)
            }
            .frame(maxWidth: .infinity)
            .frame(maxHeight: .infinity)

            if mode == .deposit {
                HStack(spacing: 10) {
                    QuickAmountChip(label: "+10,000") { digits = String((Int(digits) ?? 0) + 10_000) }
                    QuickAmountChip(label: "+100,000") { digits = String((Int(digits) ?? 0) + 100_000) }
                    QuickAmountChip(label: "Max") { digits = String(Int(availableBalance)) }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
            }

            if isSubmitting {
                ProgressView()
                    .tint(IDS.Colors.brand)
                    .padding(.vertical, 24)
            } else if mode == .claimInterest {
                FlowNextBar(enabled: true, label: "Claim") { onConfirm(0) }
            } else {
                FlowNextBar(enabled: !digits.isEmpty && amount > 0, label: "Deposit") { onConfirm(amount) }
                NumericKeypad(
                    onDigit: { d in if digits.count < 9 { digits += d } },
                    onDelete: { if !digits.isEmpty { digits.removeLast() } }
                )
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

private struct FlowTopBar: View {
    let onBack: () -> Void
    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(IDS.Colors.textPrimary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Back")
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }
}

private struct RecentRecipientRow: View {
    let name: String
    let bankAndAccount: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Circle()
                    .fill(IDS.Colors.chipBackground)
                    .frame(width: 44, height: 44)
                    .overlay(Text(String(name.prefix(1))).font(.system(size: 17, weight: .bold)).foregroundColor(IDS.Colors.textPrimary))
                VStack(alignment: .leading, spacing: 2) {
                    Text(name).font(.system(size: 16, weight: .semibold)).foregroundColor(IDS.Colors.textPrimary)
                    Text(bankAndAccount).font(.system(size: 13)).foregroundColor(IDS.Colors.textTertiary)
                }
                Spacer()
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

private struct TransferPartyRow: View {
    let label: String
    let sublabel: String
    let symbol: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.system(size: 17, weight: .semibold)).foregroundColor(IDS.Colors.textPrimary)
                Text(sublabel).font(.system(size: 13)).foregroundColor(IDS.Colors.textTertiary)
            }
            Spacer()
            RoundedRectangle(cornerRadius: 14)
                .fill(IDS.Colors.chipBackground)
                .frame(width: 42, height: 42)
                .overlay(Image(systemName: symbol).font(.system(size: 18)).foregroundColor(IDS.Colors.textPrimary))
        }
        .padding(.vertical, 6)
    }
}

private struct QuickAmountChip: View {
    let label: String
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(IDS.Colors.textPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(IDS.Colors.chipBackground)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct FlowNextBar: View {
    let enabled: Bool
    let label: String
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(enabled ? .white : IDS.Colors.textTertiary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(enabled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(!enabled)
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

private struct NumericKeypad: View {
    let onDigit: (String) -> Void
    let onDelete: () -> Void
    private let rows = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["00", "0", "DEL"]]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: 0) {
                    ForEach(row, id: \.self) { key in
                        Button(action: { key == "DEL" ? onDelete() : onDigit(key) }) {
                            Group {
                                if key == "DEL" {
                                    Image(systemName: "delete.left")
                                } else {
                                    Text(key).font(.system(size: 24, weight: .medium))
                                }
                            }
                            .foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 60)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(key == "DEL" ? "Delete" : key)
                    }
                }
            }
        }
        .padding(.bottom, 8)
    }
}
