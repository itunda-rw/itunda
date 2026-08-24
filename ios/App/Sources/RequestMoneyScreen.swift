import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real fixed-amount person-to-person payment request (item 171) -- see
// NetworkClient.swift's own doc comment. bank-mfe (item 167) and Android (item 170)
// already have this; this is the iOS port, mirroring ForeignCurrencyScreenView/
// UpfrontDepositScreenView's own shape.
struct RequestMoneyScreenView: View {
    var onBack: () -> Void = {}

    @State private var requests: [P2pPaymentRequestDto]?
    @State private var error: String?
    @State private var created: P2pPaymentRequestDto?

    private func load() async {
        do {
            requests = try await NetworkClient.shared.getMyP2pRequests().requests
            error = nil
        } catch {
            self.error = "Could not load your requests."
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Request money").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper
                // around each of these sibling sections -- real Divider()s mark the
                // real boundaries instead (docs/UI_UX_GUIDELINES.md §10).
                VStack(alignment: .leading, spacing: 12) {
                    CreateRequestCard(onCreated: { req in created = req; Task { await load() } })

                    if let created {
                        Divider().overlay(IDS.Colors.divider)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Share this code -- expires in 15 minutes").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text(created.id).font(.subheadline).bold()
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    Divider().overlay(IDS.Colors.divider)
                    PayRequestCard(onPaid: { Task { await load() } })

                    if let error { Text(error).font(.caption).foregroundColor(.red) }

                    Divider().overlay(IDS.Colors.divider)
                    Text("My requests").font(.headline)
                    if let requests {
                        if requests.isEmpty {
                            // Real copy-voice fix (item 244, round 9): matches Android's
                            // own already-correct RequestMoneyScreen.kt wording exactly
                            // -- this screen does NOT self-hide when empty, correcting a
                            // stale claim in docs/COPY_VOICE.md's rule-2 mistake writeup.
                            EmptyStateView("No requests yet — ask someone to pay you above.")
                        } else {
                            // Real fix (2026-08-24, flat-design sweep): dropped the per-row
                            // Card -- history log of payment requests, kept the per-row
                            // Divider convention (docs/DESIGN_REFERENCES.md §274).
                            ForEach(requests) { req in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(formatMoneyReq(req.amount)) RWF").bold()
                                        if !req.description.isEmpty { Text(req.description).font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                    }
                                    Spacer()
                                    Text(statusLabel(req.status))
                                        .font(.caption).bold()
                                        .foregroundColor(req.status == "COMPLETED" ? .green : req.status == "EXPIRED" ? IDS.Colors.textSecondary : IDS.Colors.brand)
                                }
                                .padding(.vertical, 10)
                                Divider().overlay(IDS.Colors.divider)
                            }
                        }
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func statusLabel(_ status: String) -> String {
        switch status {
        case "COMPLETED": return "Paid"
        case "EXPIRED": return "Expired"
        default: return "Pending"
        }
    }
}

private struct CreateRequestCard: View {
    let onCreated: (P2pPaymentRequestDto) -> Void

    @State private var amountText = ""
    @State private var description = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("New request").bold()
            IdsTextField("Amount (RWF)", text: $amountText, keyboardType: .decimalPad)
            IdsTextField("What's it for? (optional)", text: $description)
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            Button(action: { Task { await submit() } }) {
                Text(submitting ? "Creating…" : "Create request").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
    }

    private func submit() async {
        guard let amount = Double(amountText.trimmingCharacters(in: .whitespaces)), amount > 0 else {
            error = "Enter a real amount."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let res = try await NetworkClient.shared.generateP2pRequest(amount: amount, description: description.trimmingCharacters(in: .whitespaces))
            onCreated(res.request)
            amountText = ""
            description = ""
        } catch {
            self.error = "Could not create this request."
        }
    }
}

private struct PayRequestCard: View {
    let onPaid: () -> Void

    @State private var code = ""
    @State private var paying = false
    @State private var error: String?
    // Real biometric device step-up (item 171's own named follow-up, closed 2026-07-29,
    // item 181) -- DeviceStepUpHost/DeviceStepUpView (DeviceStepUpView.swift) live in
    // this same App target, so used directly here rather than the honest-text-message
    // fallback this screen shipped with originally.
    @State private var needsDeviceVerification = false

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Pay a request").bold()
                IdsTextField("Request code", text: $code)
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                Button(action: { Task { await pay() } }) {
                    Text(paying ? "Paying…" : "Pay").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(paying || code.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            DeviceStepUpHost(
                visible: needsDeviceVerification,
                onDismiss: { needsDeviceVerification = false },
                onVerified: { needsDeviceVerification = false; await pay() }
            )
        }
    }

    private func pay() async {
        paying = true
        error = nil
        defer { paying = false }
        do {
            _ = try await NetworkClient.shared.payP2pRequest(requestId: code.trimmingCharacters(in: .whitespaces))
            code = ""
            onPaid()
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not pay this request."
        } catch {
            self.error = "Could not pay this request."
        }
    }
}

private func formatMoneyReq(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int(rounded)) : String(format: "%.2f", rounded)
}
