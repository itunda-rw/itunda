import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- first iOS client for
// this feature (item 105, found via a fresh matrix scan for still-open "zero client on
// mobile" gaps: bank-mfe has had this since well before this session, Android ported
// the same day as item 104). Same no-ViewModel, "call NetworkClient.shared directly
// from Task {} blocks" convention as WeeklySavingsScreenView.swift/
// MiniWalletScreenView.swift. Mirrors bank-mfe's GroupAccountsSection/
// GroupAccountDetailView/CreateGroupAccountForm exactly.

struct GroupAccountScreenView: View {
    var onBack: () -> Void = {}
    @State private var selectedId: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { selectedId != nil ? (selectedId = nil) : onBack() }) {
                    Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Group accounts").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            if let id = selectedId {
                GroupAccountDetailContent(id: id)
            } else {
                ScrollView {
                    GroupAccountListContent(onOpen: { selectedId = $0 })
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct GroupAccountListContent: View {
    let onOpen: (String) -> Void

    @State private var accounts: [GroupAccountDto] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var showCreate = false
    @State private var name = ""
    @State private var creating = false

    var body: some View {
        VStack(spacing: 10) {
            if showCreate {
                VStack(spacing: 8) {
                    IdsTextField("Group name (e.g. Roommates)", text: $name)
                    HStack(spacing: 8) {
                        Button("Cancel") { showCreate = false; name = "" }
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.secondarySystemBackground)).cornerRadius(10)
                        Button(action: { Task { await create() } }) {
                            Text(creating ? "Creating…" : "Create").foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(creating)
                    }
                }
            } else {
                Button(action: { showCreate = true }) {
                    Text("+ New group account").bold()
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Color(.secondarySystemBackground)).cornerRadius(10)
                }
            }

            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }

            if !loaded {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            } else if accounts.isEmpty {
                EmptyStateView("No group accounts yet. Start one to split a shared expense with roommates or friends.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary).padding()
            } else {
                ForEach(accounts, id: \.id) { account in
                    Button(action: { onOpen(account.id) }) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(account.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            if let dues = account.monthlyDuesAmount {
                                Text("\(formatMoney(dues)) RWF / month dues").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color(.secondarySystemBackground)).cornerRadius(10)
                    }
                }
            }
        }
        .padding(.horizontal)
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyGroupAccounts()
            if res.success { accounts = res.groupAccounts }
            error = nil
        } catch {
            self.error = "Could not load your group accounts."
        }
        loaded = true
    }

    private func create() async {
        guard !name.isEmpty else { return }
        creating = true
        do {
            _ = try await NetworkClient.shared.createGroupAccount(name: name)
            name = ""
            showCreate = false
            await load()
        } catch {
            self.error = "Could not create this group account."
        }
        creating = false
    }
}

private struct GroupAccountDetailContent: View {
    let id: String

    @State private var detail: GroupAccountDetailResponse?
    @State private var dues: GroupAccountDuesDto?
    @State private var error: String?
    @State private var amount = ""
    @State private var phoneNumber = ""
    @State private var duesAmountInput = ""
    @State private var busy = false
    @State private var duesBusy = false
    @State private var remindedCount: Int?
    @State private var needsDeviceVerification = false
    // Real fix (2026-08-10): found live-testing bank-mfe's identical Group account
    // screen -- deposit and withdraw share this one flag+dialog, but onVerified below
    // unconditionally called deposit(). If a user was actually WITHDRAWING and hit
    // deviceNotVerified, verifying would silently DEPOSIT the same amount instead --
    // the opposite of what they asked for, not just a friction gap. Tracks which
    // action was actually pending so the retry redoes the right one.
    @State private var pendingDeviceAction: (() async -> Void)?

    private var isOwner: Bool {
        guard let detail, let myUserId = KeychainTokenStore.shared.getUserId() else { return false }
        return detail.groupAccount.ownerId == myUserId
    }

    var body: some View {
        ZStack {
            ScrollView {
                if let current = detail {
                    VStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(current.groupAccount.name).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(formatMoney(current.balance)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text("\(current.members.count) member\(current.members.count == 1 ? "" : "s")").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                        VStack(alignment: .leading, spacing: 6) {
                            Text("Members").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            ForEach(current.members, id: \.userId) { m in
                                HStack {
                                    Text("\(m.firstName) \(m.lastName)\(m.userId == KeychainTokenStore.shared.getUserId() ? " (you)" : "")")
                                        .font(.footnote)
                                    Spacer()
                                    if m.isOwner {
                                        Text("Organizer").font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                    }
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                        duesCard

                        if let error {
                            Text(error).font(.footnote).foregroundColor(.red)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text(isOwner ? "Deposit or withdraw" : "Deposit").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            IdsTextField("Amount (RWF)", text: $amount, keyboardType: .numberPad)
                            HStack(spacing: 8) {
                                Button(action: { Task { await deposit() } }) {
                                    Text(busy ? "…" : "Deposit").bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy || amount.isEmpty)
                                if isOwner {
                                    Button(action: { Task { await withdraw() } }) {
                                        Text(busy ? "…" : "Withdraw").bold()
                                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                                    }
                                    .disabled(busy || amount.isEmpty)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                        if isOwner {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Invite a member").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                HStack(spacing: 8) {
                                    IdsTextField("Phone number", text: $phoneNumber, keyboardType: .phonePad)
                                    Button(action: { Task { await invite() } }) {
                                        Text(busy ? "…" : "Invite").bold().foregroundColor(.white)
                                            .padding(.horizontal, 16).padding(.vertical, 12)
                                            .background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busy || phoneNumber.isEmpty)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                        }
                    }
                    .padding(.horizontal)
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(40)
                }
            }
            DeviceStepUpHost(
                visible: needsDeviceVerification,
                onDismiss: { pendingDeviceAction = nil; needsDeviceVerification = false },
                onVerified: {
                    let action = pendingDeviceAction
                    pendingDeviceAction = nil
                    await action?()
                }
            )
        }
        .task {
            await load()
            await loadDues()
        }
    }

    @ViewBuilder
    private var duesCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Monthly dues").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            if let currentDues = dues {
                if let duesAmount = currentDues.duesAmount {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(formatMoney(duesAmount)) RWF / month · \(currentDues.cycleMonth)").font(.footnote)
                        ForEach(currentDues.members, id: \.userId) { m in
                            HStack {
                                Text("\(m.firstName) \(m.lastName)\(m.userId == KeychainTokenStore.shared.getUserId() ? " (you)" : "")")
                                    .font(.footnote)
                                Spacer()
                                Text(m.paid ? "✓ Paid" : "\(formatMoney(m.contributedAmount)) / \(formatMoney(duesAmount))")
                                    .font(.footnote).bold(m.paid)
                                    .foregroundColor(m.paid ? .green : IDS.Colors.textSecondary)
                            }
                        }
                        if isOwner {
                            HStack(spacing: 8) {
                                Button(action: { Task { await remindUnpaid() } }) {
                                    Text(duesBusy ? "…" : "Remind unpaid members").font(.footnote).bold()
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                                }
                                .disabled(duesBusy)
                                Button(action: { Task { await setDues(nil) } }) {
                                    Text("Clear").font(.footnote).bold()
                                        .padding(.horizontal, 14).padding(.vertical, 10)
                                        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                                }
                                .disabled(duesBusy)
                            }
                        }
                        if let remindedCount {
                            Text(remindedCount == 0 ? "Everyone has already paid or been reminded this month." : "Reminded \(remindedCount) member\(remindedCount == 1 ? "" : "s").")
                                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                } else if isOwner {
                    HStack(spacing: 8) {
                        IdsTextField("Monthly dues (RWF)", text: $duesAmountInput, keyboardType: .numberPad)
                        Button(action: { if let amt = Double(duesAmountInput) { Task { await setDues(amt) } } }) {
                            Text(duesBusy ? "…" : "Set").bold().foregroundColor(.white)
                                .padding(.horizontal, 16).padding(.vertical, 10)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(duesBusy)
                    }
                } else {
                    Text("The organizer hasn't set a monthly dues amount.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                }
            } else {
                ProgressView().frame(maxWidth: .infinity).padding(20)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }

    private func load() async {
        do {
            detail = try await NetworkClient.shared.getGroupAccount(id: id)
            error = nil
        } catch {
            self.error = "Could not load this group account."
        }
    }

    private func loadDues() async {
        dues = try? await NetworkClient.shared.getGroupAccountDues(id: id).dues
    }

    private func deposit() async {
        guard let parsedAmount = Double(amount), parsedAmount > 0 else { return }
        busy = true
        needsDeviceVerification = false
        do {
            _ = try await NetworkClient.shared.depositToGroupAccount(id: id, amount: parsedAmount)
            amount = ""
            error = nil
            await load()
        } catch NetworkError.deviceNotVerified {
            pendingDeviceAction = { await self.deposit() }
            needsDeviceVerification = true
        } catch {
            self.error = "Could not deposit."
        }
        busy = false
    }

    private func withdraw() async {
        guard let parsedAmount = Double(amount), parsedAmount > 0 else { return }
        busy = true
        needsDeviceVerification = false
        do {
            _ = try await NetworkClient.shared.withdrawFromGroupAccount(id: id, amount: parsedAmount)
            amount = ""
            error = nil
            await load()
        } catch NetworkError.deviceNotVerified {
            pendingDeviceAction = { await self.withdraw() }
            needsDeviceVerification = true
        } catch {
            self.error = "Could not withdraw."
        }
        busy = false
    }

    private func invite() async {
        guard !phoneNumber.isEmpty else { return }
        busy = true
        do {
            _ = try await NetworkClient.shared.inviteGroupAccountMember(id: id, phoneNumber: phoneNumber)
            phoneNumber = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not invite this member."
        }
        busy = false
    }

    private func setDues(_ newAmount: Double?) async {
        duesBusy = true
        do {
            _ = try await NetworkClient.shared.setGroupAccountDuesAmount(id: id, amount: newAmount)
            duesAmountInput = ""
            await loadDues()
        } catch {
            self.error = "Could not save the dues amount."
        }
        duesBusy = false
    }

    private func remindUnpaid() async {
        duesBusy = true
        remindedCount = nil
        do {
            remindedCount = try await NetworkClient.shared.requestUnpaidGroupAccountDues(id: id).remindedCount
        } catch {
            self.error = "Could not send reminders."
        }
        duesBusy = false
    }
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
