import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168/177) -- see
// NetworkClient.swift's own doc comment. bank-mfe (item 168) and Android (item 176)
// already have this; this is the iOS port, mirroring RequestMoneyScreenView/
// ForeignCurrencyScreenView's own shape.
struct AutoTopUpScreenView: View {
    var onBack: () -> Void = {}

    @State private var accountId: String?
    @State private var setting: AutoTopUpSettingDto?
    @State private var settingLoaded = false
    @State private var linkedAccounts: [LinkedAccountDto]?
    @State private var error: String?
    @State private var refreshKey = 0

    private func loadAccountAndAccounts() async {
        do {
            let accounts = try await NetworkClient.shared.getAccounts().accounts
            accountId = (accounts.first(where: { $0.type == "MAIN" }) ?? accounts.first)?.id
        } catch {
            self.error = "Could not load your account."
        }
        do {
            linkedAccounts = try await NetworkClient.shared.getLinkedAccounts().linkedAccounts
        } catch {
            linkedAccounts = []
        }
    }

    private func loadSetting() async {
        guard let accountId else { return }
        settingLoaded = false
        do {
            setting = try await NetworkClient.shared.getAutoTopUpSetting(accountId: accountId).setting
        } catch let NetworkError.httpError(statusCode) {
            setting = nil
            if statusCode != 404 { error = TalkScreen.errorMessage(statusCode) }
        } catch {
            setting = nil
        }
        settingLoaded = true
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Auto top-up").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Automatically top up your account from a linked account whenever it drops below a threshold you set.")
                        .font(.footnote).foregroundColor(IDS.Colors.textSecondary)

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }

                    if accountId == nil || linkedAccounts == nil || !settingLoaded {
                        SkeletonBlock(height: 120)
                    } else if let accounts = linkedAccounts, !accounts.contains(where: { $0.status == "LINKED" }) {
                        // Real fix (2026-08-24, flat-design sweep): dropped the Card
                        // wrapper -- a lone message.
                        Text("Link an external account first -- see My > Linked accounts.")
                            .font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                            .padding(.vertical, 10)
                    } else if let accountId, let accounts = linkedAccounts {
                        AutoTopUpConfigCard(
                            accountId: accountId,
                            linkedAccounts: accounts.filter { $0.status == "LINKED" },
                            setting: setting,
                            onChanged: { refreshKey += 1 }
                        )
                        if setting != nil {
                            Button(action: {
                                Task {
                                    do {
                                        _ = try await NetworkClient.shared.triggerAutoTopUp(accountId: accountId)
                                        refreshKey += 1
                                    } catch let NetworkError.httpError(statusCode) {
                                        error = TalkScreen.errorMessage(statusCode)
                                    } catch {
                                        self.error = "Couldn't reach itunda. Check your connection and try again."
                                    }
                                }
                            }) {
                                Text("Check now").bold().foregroundColor(IDS.Colors.brand)
                                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                                    .background(IDS.Colors.card).cornerRadius(10).idsCardBorder(cornerRadius: 10)
                            }
                        }
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadAccountAndAccounts() }
        .task(id: accountId) { await loadSetting() }
        .task(id: refreshKey) { await loadSetting() }
    }
}

private struct AutoTopUpConfigCard: View {
    let accountId: String
    let linkedAccounts: [LinkedAccountDto]
    let setting: AutoTopUpSettingDto?
    let onChanged: () -> Void

    @State private var selectedAccountId: String
    @State private var thresholdText: String
    @State private var topUpText: String
    @State private var enabled: Bool
    @State private var saving = false
    @State private var error: String?

    init(accountId: String, linkedAccounts: [LinkedAccountDto], setting: AutoTopUpSettingDto?, onChanged: @escaping () -> Void) {
        self.accountId = accountId
        self.linkedAccounts = linkedAccounts
        self.setting = setting
        self.onChanged = onChanged
        _selectedAccountId = State(initialValue: setting?.linkedAccountId ?? linkedAccounts.first?.id ?? "")
        _thresholdText = State(initialValue: setting.map { formatMoneyAutoTopUp($0.thresholdAmount) } ?? "")
        _topUpText = State(initialValue: setting.map { formatMoneyAutoTopUp($0.topUpAmount) } ?? "")
        _enabled = State(initialValue: setting?.enabled ?? true)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(setting == nil ? "Set up auto top-up" : "Auto top-up settings").bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                if setting != nil {
                    Toggle("", isOn: $enabled).labelsHidden()
                }
            }
            if linkedAccounts.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(linkedAccounts) { account in
                            Button(action: { selectedAccountId = account.id }) {
                                Text("\(account.provider) \(account.externalAccountNumberMasked)")
                                    .font(.caption).foregroundColor(selectedAccountId == account.id ? .white : IDS.Colors.textPrimary)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(selectedAccountId == account.id ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(10)
                            }
                        }
                    }
                }
            } else if let account = linkedAccounts.first {
                Text("\(account.provider) \(account.externalAccountNumberMasked)").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            }
            TextField("Top up when account drops below (RWF)", text: $thresholdText)
                .keyboardType(.decimalPad)
                .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
            TextField("Amount to top up (RWF)", text: $topUpText)
                .keyboardType(.decimalPad)
                .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
            if let setting {
                Text("Triggered \(setting.triggersToday)/\(setting.dailyTriggerCap) times today.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            Button(action: { Task { await save() } }) {
                Text(saving ? "Saving…" : "Save").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(saving)
        }
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- matches
        // Android's identical AutoTopUpConfigCard conversion (docs/UI_UX_GUIDELINES.md
        // §10), a lone form section on this screen.
    }

    private func save() async {
        guard let threshold = Double(thresholdText.trimmingCharacters(in: .whitespaces)), threshold >= 0,
              let topUp = Double(topUpText.trimmingCharacters(in: .whitespaces)), topUp > 0 else {
            error = "Enter real amounts."
            return
        }
        saving = true
        error = nil
        defer { saving = false }
        do {
            _ = try await NetworkClient.shared.configureAutoTopUp(
                accountId: accountId, linkedAccountId: selectedAccountId,
                thresholdAmount: threshold, topUpAmount: topUp, enabled: enabled
            )
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private func formatMoneyAutoTopUp(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    if rounded == rounded.rounded(.down) {
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: rounded)) ?? String(Int64(rounded))
    }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: rounded)) ?? String(format: "%.2f", rounded)
}
