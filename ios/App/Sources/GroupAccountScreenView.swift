import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- first iOS client for
// this feature (item 105, found via a fresh matrix scan for still-open "zero client on
// mobile" gaps: bank-mfe has had this since well before this session, Android ported
// the same day as item 104). Same no-ViewModel, "call NetworkClient.shared directly
// from Task {} blocks" convention as WeeklySavingsScreenView.swift/
// YouthAccountScreenView.swift. Mirrors bank-mfe's GroupAccountsSection/
// GroupAccountDetailView/CreateGroupAccountForm exactly.

private enum GroupAccountMode { case list, intro, create }

struct GroupAccountScreenView: View {
    var onBack: () -> Void = {}
    @State private var selectedId: String?
    @State private var mode: GroupAccountMode = .list

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: {
                    if selectedId != nil { selectedId = nil }
                    else if mode == .create { mode = .intro }
                    else if mode == .intro { mode = .list }
                    else { onBack() }
                }) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text(selectedId != nil ? "Group account detail" : mode == .create ? "New group account" : "Group accounts").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            if let id = selectedId {
                GroupAccountDetailContent(id: id)
            } else if mode == .intro {
                GroupAccountIntroContent(onContinue: { mode = .create })
            } else if mode == .create {
                GroupAccountCreateContent(onCreated: { mode = .list })
            } else {
                ScrollView {
                    GroupAccountListContent(onOpen: { selectedId = $0 }, onStartCreate: { mode = .intro })
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13) -- see
// IkiminaScreenView.swift's identical IkiminaIntroContent for the established
// convention. Real mechanics sourced from GroupAccountService.kt's own doc comment:
// Kakao Bank's real 모임통장 (shared account) -- the creator keeps real withdrawal
// authority, invited members can view and deposit but never withdraw, capped at a
// real 100 members, with optional monthly dues tracking/reminders (setDuesAmount/
// getDuesStatus). Matches Android's identical GroupAccountIntroContent.
private struct GroupAccountIntroContent: View {
    let onContinue: () -> Void

    var body: some View {
        FixedBottomCTA {
            VStack(alignment: .leading, spacing: 20) {
                Text("One shared account, money everyone can see")
                    .font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Anyone you invite can deposit")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Great for roommates, a family fund, or a shared trip -- everyone can add money and see the real balance and every contribution.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Only you can withdraw")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("As the creator, you keep sole withdrawal authority -- members can add money but never take it out, so the fund can't be drained by anyone but you.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Optional monthly dues, with automatic reminders")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Set a monthly dues amount later if you want -- itunda will remind anyone who hasn't paid yet this cycle. Up to 100 members per group.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            .padding()
        } cta: {
            IdsButton(text: "Continue", action: onContinue)
        }
    }
}

private struct GroupAccountCreateContent: View {
    let onCreated: () -> Void

    @State private var name = ""
    @State private var creating = false
    @State private var error: String?

    var body: some View {
        FixedBottomCTA {
            VStack(spacing: 8) {
                IdsTextField("Group name (e.g. Roommates)", text: $name)
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
            .padding()
        } cta: {
            IdsButton(text: creating ? "Creating…" : "Create", isEnabled: !creating, action: { Task { await create() } })
        }
    }

    private func create() async {
        guard !name.isEmpty else { return }
        creating = true
        do {
            _ = try await NetworkClient.shared.createGroupAccount(name: name)
            onCreated()
        } catch {
            self.error = "Could not create this group account."
        }
        creating = false
    }
}

private struct GroupAccountListContent: View {
    let onOpen: (String) -> Void
    let onStartCreate: () -> Void

    @State private var accounts: [GroupAccountDto] = []
    @State private var loaded = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 10) {
            IdsButton(text: "+ New group account", action: onStartCreate)

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
                            CountUpText("\(formatMoney(current.balance)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
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
                                IdsButton(text: busy ? "…" : "Deposit", isEnabled: !busy && !amount.isEmpty, action: { Task { await deposit() } })
                                if isOwner {
                                    IdsButton(text: busy ? "…" : "Withdraw", isEnabled: !busy && !amount.isEmpty, action: { Task { await withdraw() } })
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
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not deposit."
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
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not withdraw."
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
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "ALREADY_MEMBER" {
            phoneNumber = ""
            error = nil
            await load()
        } catch let NetworkError.httpErrorWithCode(_, _, message) {
            self.error = message ?? "Could not invite this member."
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
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not save the dues amount."
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
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not send reminders."
        } catch {
            self.error = "Could not send reminders."
        }
        duesBusy = false
    }
}

