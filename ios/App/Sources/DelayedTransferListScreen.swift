import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Korean 지연이체서비스 (Delayed Transfer Service) -- see DelayedTransferDto's
// own doc comment in NetworkClient.swift for the full sourced account. Mirrors
// bank-mfe's real DelayedTransfersCard (BankDashboard.tsx) and this file's own
// sibling AutoTransferListScreen (TransferHubScreen.swift) structurally -- a list
// screen with its own "+ New" push to a create form -- rather than web's single
// inline-toggle card, matching this codebase's own established list/create-screen
// split for every other Transfer-hub row.
struct DelayedTransferListScreen: View {
    var onBack: () -> Void = {}

    @State private var transfers: [DelayedTransferDto]?
    @State private var loadError: String?
    @State private var showNewForm = false
    @State private var busyId: String?

    private var pending: [DelayedTransferDto] { (transfers ?? []).filter { $0.status == .PENDING } }
    private var past: [DelayedTransferDto] { (transfers ?? []).filter { $0.status != .PENDING } }

    var body: some View {
        if showNewForm {
            NewDelayedTransferScreen(
                onBack: { showNewForm = false },
                onCreated: {
                    showNewForm = false
                    Task { await load() }
                }
            )
        } else {
            VStack(spacing: 0) {
                HStack {
                    Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                    Spacer()
                    Text("Delayed transfers").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                .padding()

                Text("Send safely: the money is held for a few hours so you can still cancel a transfer sent under pressure or to the wrong person.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)

                IdsButton(text: "+ Send safely", action: { showNewForm = true })
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .padding(.top, 12)

                if let loadError {
                    Text(loadError)
                        .font(.caption).foregroundColor(IDS.Colors.danger)
                        .padding(.horizontal, IDS.Layout.screenHorizontal)
                        .padding(.top, 8)
                }

                if let transfers {
                    if transfers.isEmpty {
                        EmptyStateView("No delayed transfers yet.")
                            .font(.subheadline)
                            .foregroundColor(IDS.Colors.textSecondary)
                            .padding(24)
                        Spacer()
                    } else {
                        ScrollView {
                            VStack(spacing: 10) {
                                ForEach(pending + past.prefix(3)) { transfer in
                                    DelayedTransferRow(
                                        transfer: transfer,
                                        busy: busyId == transfer.id,
                                        onCancel: { cancel(transfer) }
                                    )
                                }
                            }
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                            .padding(.top, 12)
                        }
                    }
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(40)
                    Spacer()
                }
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .task { await load() }
        }
    }

    private func load() async {
        do {
            transfers = try await NetworkClient.shared.getMyDelayedTransfers().transfers
            loadError = nil
        } catch {
            loadError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func cancel(_ transfer: DelayedTransferDto) {
        busyId = transfer.id
        Task {
            do {
                _ = try await NetworkClient.shared.cancelDelayedTransfer(transfer.id)
                await load()
            } catch {
                // Best-effort, same tolerance AutoTransferListScreen's own cancel already established.
            }
            busyId = nil
        }
    }
}

private struct DelayedTransferRow: View {
    let transfer: DelayedTransferDto
    let busy: Bool
    let onCancel: () -> Void

    private var releaseDate: Date? {
        ISO8601DateFormatter.itundaFractional.date(from: transfer.releaseAt) ?? ISO8601DateFormatter().date(from: transfer.releaseAt)
    }

    private var statusLabel: String {
        switch transfer.status {
        case .PENDING: return "Pending"
        case .COMPLETED: return "Completed"
        case .CANCELLED: return "Cancelled"
        }
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(formatDelayedAmount(transfer.amount)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                if transfer.status == .PENDING, let releaseDate {
                    TimelineView(.periodic(from: .now, by: 30)) { context in
                        Text("Releases in \(formatReleaseCountdown(releaseDate, now: context.date))")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                } else {
                    Text(statusLabel).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            Spacer()
            if transfer.status == .PENDING {
                Button(busy ? "…" : "Cancel", action: onCancel)
                    .font(.caption).bold().foregroundColor(IDS.Colors.danger)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.chipBackground).cornerRadius(10)
                    .disabled(busy)
            }
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// Same real hold-window countdown as bank-mfe's own formatReleaseCountdown
// (BankDashboard.tsx) -- "Xh Ym" while over an hour out, "Ym" once under.
private func formatReleaseCountdown(_ releaseAt: Date, now: Date) -> String {
    let seconds = releaseAt.timeIntervalSince(now)
    if seconds <= 0 { return "0m" }
    let hours = Int(seconds) / 3600
    let minutes = (Int(seconds) % 3600) / 60
    return hours > 0 ? "\(hours)h \(minutes)m" : "\(minutes)m"
}

private func formatDelayedAmount(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    if rounded == rounded.rounded(.towardZero) { return String(Int(rounded)) }
    return String(format: "%.2f", rounded)
}

private extension ISO8601DateFormatter {
    static let itundaFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}

// MARK: - New delayed transfer form

private struct NewDelayedTransferScreen: View {
    var onBack: () -> Void = {}
    var onCreated: () -> Void = {}

    @State private var recipient = ""
    @State private var amount = ""
    @State private var description = ""
    @State private var error: String?
    @State private var submitting = false
    @State private var needsDeviceVerification = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Send safely").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            FixedBottomCTA {
                VStack(alignment: .leading, spacing: 12) {
                    Text("This transfer will be held for a few hours before it reaches the recipient, so you can still cancel it.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)

                    TextField("Phone number or account number", text: $recipient)
                        .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)

                    TextField("Amount (RWF)", text: $amount)
                        .keyboardType(.decimalPad)
                        .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)
                        .onChange(of: amount) { newValue in
                            amount = newValue.filter { $0.isNumber || $0 == "." }
                        }

                    TextField("What's this for? (optional)", text: $description)
                        .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)

                    if let error {
                        Text(error).font(.caption).foregroundColor(IDS.Colors.danger)
                    }

                    DeviceStepUpHost(
                        visible: needsDeviceVerification,
                        onDismiss: { needsDeviceVerification = false },
                        onVerified: { needsDeviceVerification = false; await submit() }
                    )
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 4)
            } cta: {
                IdsButton(text: submitting ? "Sending…" : "Send safely", isEnabled: !submitting, action: { Task { await submit() } })
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func submit() async {
        guard !recipient.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a phone number or account number."
            return
        }
        guard let amountValue = Double(amount), amountValue > 0 else {
            error = "Enter a valid amount."
            return
        }
        submitting = true
        error = nil
        do {
            _ = try await NetworkClient.shared.sendDelayed(
                recipient: recipient.trimmingCharacters(in: .whitespaces),
                amount: amountValue,
                description: description
            )
            onCreated()
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = message ?? Self.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        submitting = false
    }

    // Same statusCode-to-message convention as NewAutoTransferScreen's own
    // errorMessage (TransferHubScreen.swift) -- mirrors P2pController's real
    // @ExceptionHandler mapping for send-delayed (it shares P2pTransferLimitService
    // and recipient-resolution with the instant sendDirect path).
    private static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 404: return "Couldn't find that recipient or account."
        case 422: return "Insufficient funds for this transfer."
        case 429: return "Too many requests -- try again in a bit."
        case 400: return "That recipient or amount isn't valid."
        default: return "Couldn't send this transfer. Please try again."
        }
    }
}
