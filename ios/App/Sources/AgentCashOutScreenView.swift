import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real "cash out at an agent" screen -- see NetworkClient's
/// AgentWithdrawalAuthorizationDto doc comment for the full sourced account.
/// Distinct from AgentOperatorScreenView.swift's own STAFF console -- this is the
/// CUSTOMER side. Mirrors Android's AgentCashScreen.kt exactly (the only platform
/// that already had this feature): amount entry -> create a one-time, 10-minute-
/// expiry code -> show it as a QR/text to the agent -> cancel if unused. Reuses
/// `generateQrImage` (CoreDesignSystem, already used by FeaturePay's own
/// MyPaymentCodeCard) rather than writing a second QR-generation implementation.
struct AgentCashOutScreenView: View {
    var onBack: () -> Void = {}
    var onFindNearbyAgent: () -> Void = {}

    @State private var authorizations: [AgentWithdrawalAuthorizationDto]?
    @State private var amountText = ""
    @State private var creating = false
    @State private var error: String?

    private func load() async {
        do {
            authorizations = try await NetworkClient.shared.getAgentWithdrawalAuthorizations().authorizations
        } catch {
            authorizations = []
        }
    }

    private var activeCount: Int {
        (authorizations ?? []).filter { $0.status == "ACTIVE" }.count
    }

    private func handleCreate() async {
        guard let amount = Double(amountText), amount > 0 else { return }
        creating = true
        error = nil
        do {
            _ = try await NetworkClient.shared.createAgentWithdrawalAuthorization(amount: amount)
            amountText = ""
            await load()
        } catch {
            self.error = "Could not create a withdrawal code. Please try again."
        }
        creating = false
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Cash out at an agent").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: IDS.Layout.cardGap) {
                    Text("Create a one-time code, then show it to the agent only when they are ready to hand over cash. Codes expire in 10 minutes.")
                        .font(.subheadline).foregroundColor(IDS.Colors.textSecondary)

                    Button(action: onFindNearbyAgent) {
                        Text("Find a nearby itunda agent").bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(IDS.Colors.chipBackground).cornerRadius(10)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        TextField("Amount (RWF)", text: $amountText)
                            .keyboardType(.numberPad)
                            .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)

                        HStack(spacing: 8) {
                            ForEach([10000, 100000], id: \.self) { quick in
                                Button(action: { amountText = String(quick) }) {
                                    Text("\(formatAmount(quick)) RWF").bold().foregroundColor(IDS.Colors.textPrimary)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(IDS.Colors.chipBackground).cornerRadius(10)
                                }
                            }
                        }

                        if let error {
                            Text(error).foregroundColor(.red).font(.caption)
                        }

                        IdsButton(text: creating ? "Creating…" : "Create withdrawal code", isEnabled: !creating && (Double(amountText).map { $0 > 0 } ?? false)) {
                            Task { await handleCreate() }
                        }
                    }
                    .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)

                    Text("Your codes (\(activeCount) active)").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)

                    if authorizations == nil {
                        SkeletonBlock(height: 120)
                    } else if authorizations!.isEmpty {
                        Text("No withdrawal codes yet.").foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(authorizations!) { authorization in
                            AgentWithdrawalAuthorizationCard(authorization: authorization, onCancelled: { Task { await load() } })
                        }
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, IDS.Layout.screenTop)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }
}

private struct AgentWithdrawalAuthorizationCard: View {
    let authorization: AgentWithdrawalAuthorizationDto
    let onCancelled: () -> Void

    @State private var cancelling = false
    @State private var error: String?

    private var active: Bool { authorization.status == "ACTIVE" }

    private func handleCancel() async {
        cancelling = true
        error = nil
        do {
            _ = try await NetworkClient.shared.cancelAgentWithdrawalAuthorization(code: authorization.code)
            onCancelled()
        } catch {
            self.error = "Could not cancel this code."
        }
        cancelling = false
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(formatAmount(Int(authorization.amount))) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if active {
                        Text("Withdrawal code").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Text(authorization.code).font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
                    } else {
                        Text(authorization.status).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Text("Expires: \(authorization.expiresAt)").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                if active, let qr = generateQrImage(from: authorization.code, size: 90) {
                    Image(uiImage: qr).resizable().frame(width: 90, height: 90)
                }
            }
            if active {
                Text("For your security, give this code only to an itunda agent at the counter. It can be used once for the exact amount shown.")
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error {
                Text(error).foregroundColor(.red).font(.caption)
            }
            if active {
                Button(action: { Task { await handleCancel() } }) {
                    Text(cancelling ? "Cancelling…" : "Cancel code").bold().foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(10)
                }
                .disabled(cancelling)
            }
        }
        .padding(16)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
    }
}
