import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Bank 송금 (Transfer) full page (2026-07-24 port) -- mirrors Android's real
// TransferHubScreen.kt exactly (see that file's own doc comment for the full account):
// Home's own "Send" button (TransferFlowContainer, unchanged) previously jumped straight
// into the 2-step Recipient/Amount flow with no page in between, unlike real Toss where
// Home shows only a thin curated teaser and every money-feature area has its own full
// dedicated page grouping every related action (confirmed via real Toss screenshots: the
// 송금 page groups 송금하기/자동이체/해외송금/더치페이/etc). This groups itunda's own
// equivalents -- Send money now, real Auto-transfer (see AutoTransferDto's own doc
// comment in NetworkClient.swift), and Transfer history -- scoped to what itunda
// actually has rather than inventing Toss features itunda has no backend for.
//
// Reached from EntireMenuScreen's own "Financial services" section (the "Transfer" row),
// not from Home -- matching real Toss where Home's Send button stays a quick
// recipient-picker, unchanged.
struct TransferHubContainer: View {
    var onBack: () -> Void = {}
    var onSendMoney: () -> Void = {}
    var onSplitBill: () -> Void = {}
    var onOpenHistory: () -> Void = {}

    @State private var autoTransferCount = 0
    @State private var showAutoTransfers = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Transfer").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(spacing: 8) {
                    TransferHubRow(
                        symbol: "paperplane.fill",
                        title: "Send money",
                        subtitle: "Account number · contact",
                        action: onSendMoney
                    )
                    TransferHubRow(
                        symbol: "arrow.triangle.2.circlepath",
                        title: "Auto-transfer",
                        subtitle: autoTransferCount > 0 ? "\(autoTransferCount) active" : "Set up a recurring transfer",
                        action: { showAutoTransfers = true }
                    )
                    // Real 더치페이 (Split bill) row (2026-07-24) -- completes real
                    // Toss's own 송금 page grouping, deliberately deferred when this
                    // screen was first ported. Split Bill itself already lives inside
                    // a group's own Talk thread (see EntireMenuScreen's identical
                    // "Split a bill with friends" row) -- this just adds the same
                    // real entry point here too.
                    TransferHubRow(
                        symbol: "person.3.fill",
                        title: "Split a bill",
                        subtitle: "Settle up with friends in Talk",
                        action: onSplitBill
                    )
                    HStack {
                        Text("Transfer history")
                            .font(.footnote)
                            .foregroundColor(IDS.Colors.textSecondary)
                        Spacer()
                    }
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onOpenHistory)
                    .padding(.vertical, 8)
                    .padding(.top, 12)
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 4)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadCount() }
        .fullScreenCover(isPresented: $showAutoTransfers) {
            AutoTransferListScreen(
                onBack: { showAutoTransfers = false },
                onChanged: { Task { await loadCount() } }
            )
        }
    }

    private func loadCount() async {
        if let res = try? await NetworkClient.shared.getMyAutoTransfers() {
            autoTransferCount = res.autoTransfers.filter { $0.status == "ACTIVE" }.count
        }
    }
}

private struct TransferHubRow: View {
    let symbol: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 12).fill(IDS.Colors.chipBackground)
                    Image(systemName: symbol).foregroundColor(IDS.Colors.brand)
                }
                .frame(width: 40, height: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text(subtitle).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(IDS.Colors.textTertiary)
            }
            // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
            // matches ShellSection/FlatSection's established catalog-list convention
            // (docs/UI_UX_GUIDELINES.md §10), no divider.
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Auto-transfer list

/// Real auto-transfer list + creation -- see AutoTransferDto's own doc comment in
/// NetworkClient.swift for the exact real ledger movement this schedules
/// (P2pService.sendDirect, unmodified, just triggered by a scheduler instead of a direct
/// tap). Mirrors Android's AutoTransferListScreen exactly.
struct AutoTransferListScreen: View {
    var onBack: () -> Void = {}
    var onChanged: () -> Void = {}

    @State private var autoTransfers: [AutoTransferDto]?
    @State private var loadError: String?
    @State private var showNewForm = false

    var body: some View {
        if showNewForm {
            NewAutoTransferScreen(
                onBack: { showNewForm = false },
                onCreated: {
                    showNewForm = false
                    Task { await load() }
                    onChanged()
                }
            )
        } else {
            VStack(spacing: 0) {
                HStack {
                    Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                    Spacer()
                    Text("Auto-transfer").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                .padding()

                Button(action: { showNewForm = true }) {
                    Text("+ New auto-transfer")
                        .font(.subheadline).bold()
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(IDS.Colors.brand)
                        .cornerRadius(12)
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)

                if let loadError {
                    Text(loadError)
                        .font(.caption).foregroundColor(.red)
                        .padding(.horizontal, IDS.Layout.screenHorizontal)
                        .padding(.top, 8)
                }

                if let list = autoTransfers {
                    if list.isEmpty {
                        EmptyStateView("No auto-transfers yet.")
                            .font(.subheadline)
                            .foregroundColor(IDS.Colors.textSecondary)
                            .padding(24)
                        Spacer()
                    } else {
                        ScrollView {
                            VStack(spacing: 10) {
                                ForEach(list) { autoTransfer in
                                    AutoTransferCard(
                                        autoTransfer: autoTransfer,
                                        onTogglePause: { togglePause(autoTransfer) },
                                        onCancel: { cancel(autoTransfer) }
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
            autoTransfers = try await NetworkClient.shared.getMyAutoTransfers().autoTransfers
            loadError = nil
        } catch {
            loadError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func togglePause(_ autoTransfer: AutoTransferDto) {
        Task {
            do {
                if autoTransfer.status == "ACTIVE" {
                    _ = try await NetworkClient.shared.pauseAutoTransfer(autoTransfer.id)
                } else {
                    _ = try await NetworkClient.shared.resumeAutoTransfer(autoTransfer.id)
                }
                await load()
            } catch {
                // Best-effort, same as Android -- this row just won't update this tap.
            }
        }
    }

    private func cancel(_ autoTransfer: AutoTransferDto) {
        Task {
            do {
                _ = try await NetworkClient.shared.cancelAutoTransfer(autoTransfer.id)
                await load()
                onChanged()
            } catch {
                // Best-effort, same as Android.
            }
        }
    }
}

private struct AutoTransferCard: View {
    let autoTransfer: AutoTransferDto
    let onTogglePause: () -> Void
    let onCancel: () -> Void

    private var cadence: String {
        switch autoTransfer.frequency {
        case .WEEKLY: return "Weekly"
        case .MONTHLY: return "Monthly on day \(autoTransfer.dayOfMonth.map(String.init) ?? "-")"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(autoTransfer.recipientName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Text("\(formatAutoTransferAmount(autoTransfer.amount)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            }
            Text(cadence).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if autoTransfer.status == "PAUSED" {
                Text("Paused").font(.caption).bold().foregroundColor(IDS.Colors.textTertiary)
            }
            if let reason = autoTransfer.lastFailureReason {
                Text("Last attempt skipped: \(reason)").font(.caption).foregroundColor(.red)
            }
            HStack(spacing: 8) {
                Button(autoTransfer.status == "PAUSED" ? "Resume" : "Pause", action: onTogglePause)
                    .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.chipBackground).cornerRadius(10)
                Button("Cancel", action: onCancel)
                    .font(.caption).bold().foregroundColor(.red)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.chipBackground).cornerRadius(10)
            }
            .padding(.top, 4)
        }
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- an
        // entity/management list (recurring transfers), no divider.
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private func formatAutoTransferAmount(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    if rounded == rounded.rounded(.towardZero) { return String(Int(rounded)) }
    return String(format: "%.2f", rounded)
}

// MARK: - New auto-transfer form

struct NewAutoTransferScreen: View {
    var onBack: () -> Void = {}
    var onCreated: () -> Void = {}

    @State private var recipient = ""
    @State private var amount = ""
    @State private var frequency: AutoTransferFrequency = .MONTHLY
    @State private var dayOfMonth = "1"
    @State private var dayOfWeek = 1
    @State private var description = ""
    @State private var error: String?
    @State private var submitting = false

    private let weekdays: [(Int, String)] = [(1, "Mon"), (2, "Tue"), (3, "Wed"), (4, "Thu"), (5, "Fri"), (6, "Sat"), (7, "Sun")]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("New auto-transfer").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    TextField("Phone number or account number", text: $recipient)
                        .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)

                    TextField("Amount (RWF)", text: $amount)
                        .keyboardType(.decimalPad)
                        .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)
                        .onChange(of: amount) { newValue in
                            amount = newValue.filter { $0.isNumber || $0 == "." }
                        }

                    HStack(spacing: 8) {
                        frequencyChip(.WEEKLY, label: "Weekly")
                        frequencyChip(.MONTHLY, label: "Monthly")
                    }

                    if frequency == .MONTHLY {
                        TextField("Day of month (1-28)", text: $dayOfMonth)
                            .keyboardType(.numberPad)
                            .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)
                            .onChange(of: dayOfMonth) { newValue in
                                dayOfMonth = newValue.filter(\.isNumber)
                            }
                    } else {
                        HStack(spacing: 6) {
                            ForEach(weekdays, id: \.0) { day, label in
                                dayChip(day, label: label)
                            }
                        }
                    }

                    TextField("What's this for? (optional)", text: $description)
                        .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }

                    Button(action: submit) {
                        Text(submitting ? "Setting up…" : "Set up auto-transfer")
                            .foregroundColor(.white).bold()
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(IDS.Colors.brand)
                            .cornerRadius(12)
                    }
                    .disabled(submitting)
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 4)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    @ViewBuilder
    private func frequencyChip(_ value: AutoTransferFrequency, label: String) -> some View {
        let selected = frequency == value
        Text(label)
            .font(.caption).bold()
            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
            .padding(.horizontal, 16).padding(.vertical, 8)
            .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
            .clipShape(Capsule())
            .onTapGesture { frequency = value }
    }

    @ViewBuilder
    private func dayChip(_ day: Int, label: String) -> some View {
        let selected = dayOfWeek == day
        Text(label)
            .font(.caption2).bold()
            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
            .clipShape(Capsule())
            .onTapGesture { dayOfWeek = day }
    }

    private func submit() {
        guard !recipient.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a phone number or account number."
            return
        }
        guard let amountValue = Double(amount), amountValue > 0 else {
            error = "Enter a valid amount."
            return
        }
        let dayOfMonthValue = frequency == .MONTHLY ? Int(dayOfMonth) : nil
        if frequency == .MONTHLY && !(1...28).contains(dayOfMonthValue ?? 0) {
            error = "Day of month must be between 1 and 28."
            return
        }
        submitting = true
        error = nil
        Task {
            do {
                _ = try await NetworkClient.shared.createAutoTransfer(
                    recipient: recipient.trimmingCharacters(in: .whitespaces),
                    amount: amountValue,
                    frequency: frequency,
                    dayOfWeek: frequency == .WEEKLY ? dayOfWeek : nil,
                    dayOfMonth: dayOfMonthValue,
                    description: description
                )
                onCreated()
            } catch let NetworkError.httpError(statusCode) {
                error = Self.errorMessage(statusCode)
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
            }
            submitting = false
        }
    }

    // Statuses mirror AutoTransferController's own real @ExceptionHandler mapping exactly
    // (INVALID_SCHEDULE/P2P_RECIPIENT_NOT_FOUND/SELF_PAYMENT_NOT_ALLOWED/ACCOUNT_NOT_FOUND/
    // INVALID_AMOUNT all -> 400/404, INSUFFICIENT_FUNDS -> 422, RATE_LIMITED -> 429) --
    // same statusCode-to-message convention this codebase already uses (see
    // CreateWeeklySavingsPlanView.errorMessage in WeeklySavingsScreenView.swift), a
    // deliberate divergence from Android's own regex-parsed error-body message: iOS's
    // NetworkClient only ever surfaces the statusCode from a failed authenticatedPost,
    // not the raw JSON body, so mapping the code is the real option here without a
    // bigger, riskier NetworkClient refactor just for this one screen.
    private static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 404: return "Couldn't find that recipient or account."
        case 422: return "Insufficient funds for this auto-transfer."
        case 429: return "Too many requests -- try again in a bit."
        case 400: return "That recipient, amount, or schedule isn't valid."
        default: return "Couldn't set up this auto-transfer. Please try again."
        }
    }
}
