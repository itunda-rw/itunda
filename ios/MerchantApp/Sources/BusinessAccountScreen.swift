import SwiftUI
import CoreDesignSystem

/// Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent (item 151)
/// -- closes a real gap named in the backend's own Merchant.kt doc comment: every
/// merchant's real card/QR collection has always settled straight into their PERSONAL
/// main wallet, with business income and personal spending genuinely inseparable.
/// This tab lets a merchant open a real, dedicated business wallet and deliberately
/// move money into/out of it, with its own real transaction history. Mirrors Android
/// merchantapp's own BusinessAccountScreen.kt (BusinessAccountTab) exactly.
struct BusinessAccountTab: View {
    @State private var wallet: BusinessWalletDto?
    @State private var loaded = false
    @State private var merchant: MerchantDto?
    @State private var transactions: [BusinessLedgerEntryDto] = []
    @State private var opening = false
    @State private var error: String?

    var body: some View {
        Group {
            if !loaded {
                VStack { Spacer(); ProgressView(); Spacer() }
            } else if let wallet {
                accountView(wallet)
            } else {
                openAccountView
            }
        }
        .task { await load() }
    }

    private var openAccountView: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let merchant {
                FeeWaiverCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                WebhookUrlCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                StoreSettingsCard(merchant: merchant, onUpdated: { self.merchant = $0 })
            }
            Text("Business account").font(.title3).bold()
            Text("Keep your business money separate from your personal wallet. Your real card/QR collections still settle to your personal wallet as before — move money into your business account whenever you're ready to set it aside.")
                .font(.footnote).foregroundColor(.secondary)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            Button(action: { Task { await open() } }) {
                Text(opening ? "Opening…" : "Open business account")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(opening)
            Spacer()
        }
        .padding(16)
    }

    private func accountView(_ wallet: BusinessWalletDto) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let merchant {
                    FeeWaiverCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                    StoreSettingsCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Business balance").font(.caption).foregroundColor(.secondary)
                    Text("\(formattedRWF(wallet.balance)) RWF").font(.title).bold()
                    Text(wallet.accountNumber).font(.caption).foregroundColor(.secondary)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(12)

                MoveMoneyCard(onMoved: { Task { await load() } })

                Text("Business transactions").font(.headline)
                if transactions.isEmpty {
                    Text("No business transactions yet.").font(.footnote).foregroundColor(.secondary)
                } else {
                    ForEach(transactions) { entry in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.memo).font(.subheadline)
                                Text(String(entry.createdAt.prefix(10))).font(.caption).foregroundColor(.secondary)
                            }
                            Spacer()
                            Text("\(entry.direction == "CREDIT" ? "+" : "-")\(formattedRWF(entry.amount)) RWF").bold()
                        }
                        .padding(14)
                        .background(Color(.secondarySystemBackground))
                        .cornerRadius(12)
                    }
                }
            }
            .padding(16)
        }
    }

    private func load() async {
        do {
            wallet = try await MerchantNetworkClient.shared.getBusinessAccount().wallet
            transactions = (try? await MerchantNetworkClient.shared.getBusinessTransactions().transactions) ?? []
            error = nil
        } catch {
            wallet = nil
        }
        merchant = try? await MerchantNetworkClient.shared.getMyMerchant().merchant
        loaded = true
    }

    private func open() async {
        opening = true
        error = nil
        defer { opening = false }
        do {
            _ = try await MerchantNetworkClient.shared.openBusinessAccount()
            await load()
        } catch {
            self.error = "Couldn't open a business account. Try again."
        }
    }
}

private struct MoveMoneyCard: View {
    let onMoved: () -> Void

    @State private var amountText = ""
    @State private var moving = false
    @State private var error: String?
    @State private var needsDeviceVerification = false

    var body: some View {
        if needsDeviceVerification {
            ZStack {
                Color.black.opacity(0.3).ignoresSafeArea()
                DeviceStepUpDialog(
                    onVerified: { needsDeviceVerification = false },
                    onCancel: { needsDeviceVerification = false }
                )
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text("Move money").bold()
                TextField("Amount (RWF)", text: $amountText)
                    .keyboardType(.numberPad)
                    .padding(12)
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(10)
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                HStack(spacing: 8) {
                    Button(action: { Task { await move(toBusiness: true) } }) {
                        Text("To business").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(moving)
                    Button(action: { Task { await move(toBusiness: false) } }) {
                        Text("To personal").bold()
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .background(Color(.secondarySystemBackground)).cornerRadius(10)
                    }
                    .disabled(moving)
                }
            }
            .padding(16)
            .background(Color(.secondarySystemBackground))
            .cornerRadius(12)
        }
    }

    private func move(toBusiness: Bool) async {
        guard let amount = Double(amountText), amount > 0 else {
            error = "Enter a real amount."
            return
        }
        moving = true
        error = nil
        defer { moving = false }
        do {
            _ = toBusiness
                ? try await MerchantNetworkClient.shared.moveToBusiness(amount: amount)
                : try await MerchantNetworkClient.shared.moveToPersonal(amount: amount)
            amountText = ""
            onMoved()
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch {
            self.error = "Couldn't move this money. Check your balance."
        }
    }
}

/// Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
/// see rw.itunda.merchant.MerchantFeeWaiverService's own doc comment. Eligibility (a real
/// 30-day payment-volume threshold) is checked server-side; this card just surfaces the
/// current state and lets an eligible merchant apply. merchant-mfe and Android already
/// have this; this is the first iOS client.
private struct FeeWaiverCard: View {
    let merchant: MerchantDto
    let onUpdated: (MerchantDto) -> Void

    @State private var busy = false
    @State private var error: String?

    private var waived: Bool { merchant.feeRateOverride == 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Small-merchant fee waiver").bold()
                    Text(waived
                        ? "Active — you pay no platform fee on payments you collect."
                        : "If your payment volume over the last 30 days is small, you may qualify for a full fee waiver.")
                        .font(.footnote).foregroundColor(.secondary)
                }
                Spacer()
                if !waived {
                    Button(action: { Task { await apply() } }) {
                        Text(busy ? "…" : "Apply")
                            .bold().foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
            }
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    private func apply() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            onUpdated(try await MerchantNetworkClient.shared.applyForFeeWaiver().merchant)
        } catch {
            self.error = "Couldn't apply for a fee waiver."
        }
    }
}

/// Real payment-event webhook URL settings -- see
/// rw.itunda.merchant.MerchantService.setWebhookUrl's own doc comment. merchant-mfe/
/// Android already have this; this is the first iOS client.
private struct WebhookUrlCard: View {
    let merchant: MerchantDto
    let onUpdated: (MerchantDto) -> Void

    @State private var webhookUrl = ""
    @State private var busy = false
    @State private var error: String?
    @State private var saved = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Webhook URL").bold()
            TextField("https://your-server.example.com/webhooks/itunda", text: $webhookUrl)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                .onChange(of: webhookUrl) { _ in saved = false }
            Text("We'll notify this address every time a payment completes. If it doesn't respond, we'll keep retrying for about 3 days.")
                .font(.footnote).foregroundColor(.secondary)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            if saved {
                Text("Saved.").font(.footnote).foregroundColor(IDS.Colors.brand)
            }
            Button(action: { Task { await save() } }) {
                Text(busy ? "Saving…" : "Save")
                    .bold().foregroundColor(.white)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.brand).cornerRadius(8)
            }
            .disabled(busy)
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .onAppear { webhookUrl = merchant.webhookUrl ?? "" }
    }

    private func save() async {
        busy = true
        error = nil
        saved = false
        defer { busy = false }
        do {
            onUpdated(try await MerchantNetworkClient.shared.setWebhookUrl(webhookUrl).merchant)
            saved = true
        } catch {
            self.error = "Could not save."
        }
    }
}

/// Real store-settings bundle -- category, photo URL, minimum order amount, boosted
/// cashback rate, and scheduled-orders opt-in. See MerchantController.kt's own doc
/// comments for each endpoint. Found 2026-08-01 via a dead-field sweep: category was
/// a real MerchantDto field with zero UI anywhere on this app; photo/min-order/
/// cashback-rate/scheduled-orders were real backend endpoints with zero client
/// anywhere at all (not even merchant-mfe) until this same pass.
private struct StoreSettingsCard: View {
    let merchant: MerchantDto
    let onUpdated: (MerchantDto) -> Void

    @State private var category = ""
    @State private var photoUrl = ""
    @State private var minOrderAmount = ""
    @State private var cashbackPercent = ""
    @State private var busy = false
    @State private var error: String?
    @State private var saved = false
    @State private var scheduledBusy = false
    @State private var scheduledError: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Store settings").bold()
            TextField("Category (e.g. Rwandan, Bakery, Cafe)", text: $category)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                .onChange(of: category) { _ in saved = false }
            TextField("Store photo URL", text: $photoUrl)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                .onChange(of: photoUrl) { _ in saved = false }
            TextField("Minimum order (RWF, blank = none)", text: $minOrderAmount)
                .keyboardType(.numberPad)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                .onChange(of: minOrderAmount) { _ in saved = false }
            TextField("Boosted cashback (0-5%, blank = standard)", text: $cashbackPercent)
                .keyboardType(.decimalPad)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                .onChange(of: cashbackPercent) { _ in saved = false }
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            if saved {
                Text("Saved.").font(.footnote).foregroundColor(IDS.Colors.brand)
            }
            Button(action: { Task { await save() } }) {
                Text(busy ? "Saving…" : "Save")
                    .bold().foregroundColor(.white)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.brand).cornerRadius(8)
            }
            .disabled(busy)
            Divider()
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Accept scheduled orders").bold().font(.subheadline)
                    Text("Let buyers pick a future delivery/pickup time.").font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                Button(action: { Task { await toggleScheduledOrders() } }) {
                    Text(scheduledBusy ? "…" : (merchant.acceptsScheduledOrders ? "On" : "Off"))
                        .bold().font(.caption)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                .disabled(scheduledBusy)
            }
            if let scheduledError {
                Text(scheduledError).font(.footnote).foregroundColor(.red)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .onAppear {
            category = merchant.category ?? ""
            photoUrl = merchant.photoUrl ?? ""
            minOrderAmount = merchant.minOrderAmount.map { String(Int($0)) } ?? ""
            cashbackPercent = merchant.cashbackRate.map { String(format: "%.1f", $0 * 100) } ?? ""
        }
    }

    private func save() async {
        let trimmedCategory = category.trimmingCharacters(in: .whitespaces)
        guard !trimmedCategory.isEmpty else {
            error = "Enter a real category."
            return
        }
        let trimmedMinOrder = minOrderAmount.trimmingCharacters(in: .whitespaces)
        let minOrder: Double?
        if trimmedMinOrder.isEmpty {
            minOrder = nil
        } else if let value = Double(trimmedMinOrder) {
            minOrder = value
        } else {
            error = "Enter a real minimum order amount."
            return
        }
        let trimmedCashback = cashbackPercent.trimmingCharacters(in: .whitespaces)
        let cashbackRate: Double?
        if trimmedCashback.isEmpty {
            cashbackRate = nil
        } else if let value = Double(trimmedCashback) {
            cashbackRate = value / 100
        } else {
            error = "Enter a real cashback percent."
            return
        }
        busy = true
        error = nil
        saved = false
        defer { busy = false }
        do {
            var updated = try await MerchantNetworkClient.shared.setCategory(trimmedCategory).merchant
            updated = try await MerchantNetworkClient.shared.setMerchantPhotoUrl(photoUrl.trimmingCharacters(in: .whitespaces)).merchant
            updated = try await MerchantNetworkClient.shared.setMinOrderAmount(minOrder).merchant
            updated = try await MerchantNetworkClient.shared.setCashbackRate(cashbackRate).merchant
            onUpdated(updated)
            saved = true
        } catch {
            self.error = "Could not save."
        }
    }

    private func toggleScheduledOrders() async {
        scheduledBusy = true
        scheduledError = nil
        defer { scheduledBusy = false }
        do {
            onUpdated(try await MerchantNetworkClient.shared.setAcceptsScheduledOrders(!merchant.acceptsScheduledOrders).merchant)
        } catch {
            scheduledError = "Could not save."
        }
    }
}
