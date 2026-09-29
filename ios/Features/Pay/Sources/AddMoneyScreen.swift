import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Toss "충전하기" (top up) reference (2026-09-12, direct user-supplied Toss Pay
/// screenshots) -- closes the exact gap MyPaymentCodeCard.swift's own onAddMoney call
/// site previously left dead (`onAddMoney: { showAccountDetail = false }`, matching
/// web's/Android's identical disclosed gap). Picks a source from the caller's OTHER
/// real accounts (never a fabricated external MyData-style balance -- itunda has no
/// such integration) and a real amount, then posts the same internal-transfer
/// endpoint the reverse "옮기기" direction would also use. Own file, matching this
/// screen family's established "genuinely distinct flow, own file" convention.
private let quickAmounts = [1000, 5000, 10000]

public struct AddMoneyScreen: View {
    let destinationAccountId: String
    let sourceOptions: [Account]
    let onBack: () -> Void
    let onDone: () -> Void

    @State private var sourceId: String?
    @State private var amountText = ""
    @State private var error: String?
    @State private var busy = false

    public init(destinationAccountId: String, sourceOptions: [Account], onBack: @escaping () -> Void, onDone: @escaping () -> Void) {
        self.destinationAccountId = destinationAccountId
        self.sourceOptions = sourceOptions
        self.onBack = onBack
        self.onDone = onDone
        _sourceId = State(initialValue: sourceOptions.first?.id)
    }

    private var amount: Double? {
        let parsed = Double(amountText)
        return (parsed ?? 0) > 0 ? parsed : nil
    }

    private func submit() {
        guard let sourceId, let amount else { return }
        busy = true
        error = nil
        Task {
            do {
                _ = try await NetworkClient.shared.internalTransfer(fromAccountId: sourceId, toAccountId: destinationAccountId, amount: amount)
                busy = false
                onDone()
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                busy = false
                error = message ?? "Could not complete this top-up."
            } catch {
                busy = false
                self.error = "Could not complete this top-up."
            }
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .body).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Add money")
                        .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2))
                        .foregroundColor(IDS.Colors.textPrimary)

                    if sourceOptions.isEmpty {
                        Text("You don't have another itunda account to top up from yet.")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("From")
                                .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                                .foregroundColor(IDS.Colors.textSecondary)
                            ForEach(sourceOptions, id: \.id) { account in
                                Button(action: { sourceId = account.id }) {
                                    HStack {
                                        Image(systemName: sourceId == account.id ? "largecircle.fill.circle" : "circle")
                                            .foregroundColor(sourceId == account.id ? IDS.Colors.brand : IDS.Colors.textTertiary)
                                        Text(account.nickname ?? account.accountName)
                                            .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .callout))
                                            .foregroundColor(IDS.Colors.textPrimary)
                                        Spacer()
                                        Text("\(formatAmount(Int(account.balance))) RWF")
                                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                                            .foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    .padding(.vertical, 8)
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Amount")
                                .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                                .foregroundColor(IDS.Colors.textSecondary)
                            IdsTextField("Amount (RWF)", text: $amountText, keyboardType: .numberPad)
                            HStack(spacing: 8) {
                                ForEach(quickAmounts, id: \.self) { quick in
                                    Button(action: { amountText = String((Int(amountText) ?? 0) + quick) }) {
                                        Text("+\(formatAmount(quick))")
                                            .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .footnote))
                                            .foregroundColor(IDS.Colors.textPrimary)
                                            .padding(.horizontal, 12).padding(.vertical, 6)
                                            .background(IDS.Colors.chipBackground)
                                            .cornerRadius(8)
                                    }
                                }
                            }
                        }

                        if let error {
                            Text(error)
                                .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                                .foregroundColor(.red)
                        }

                        IdsButton(text: busy ? "Adding money…" : "Add money", action: submit)
                            .frame(height: 48)
                            .disabled(busy || sourceId == nil || amount == nil)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 4)
                .padding(.bottom, 24)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}
