import SwiftUI
import CoreDesignSystem

/// Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent (item 151)
/// -- closes a real gap named in the backend's own Merchant.kt doc comment: every
/// merchant's real card/QR collection has always settled straight into their PERSONAL
/// main account, with business income and personal spending genuinely inseparable.
/// This tab lets a merchant open a real, dedicated business account and deliberately
/// move money into/out of it, with its own real transaction history. Mirrors Android
/// merchantapp's own BusinessAccountScreen.kt (BusinessAccountTab) exactly.
struct BusinessAccountTab: View {
    @State private var account: BusinessAccountDto?
    @State private var loaded = false
    @State private var merchant: MerchantDto?
    @State private var transactions: [BusinessLedgerEntryDto] = []
    @State private var opening = false
    @State private var error: String?

    var body: some View {
        Group {
            if !loaded {
                VStack { Spacer(); ProgressView(); Spacer() }
            } else if let account {
                accountView(account)
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
                ApiIntegrationCard()
                StoreSettingsCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                MoreStoreSettingsCard(merchant: merchant, onUpdated: { self.merchant = $0 })
            }
            Text("Business account").font(.title3).bold()
            Text("Keep your business money separate from your personal account. Your real card/QR collections still settle to your personal account as before — move money into your business account whenever you're ready to set it aside.")
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

    private func accountView(_ account: BusinessAccountDto) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let merchant {
                    FeeWaiverCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                    ApiIntegrationCard()
                    StoreSettingsCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                    MoreStoreSettingsCard(merchant: merchant, onUpdated: { self.merchant = $0 })
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Business balance").font(.caption).foregroundColor(.secondary)
                    Text("\(formattedRWF(account.balance)) RWF").font(.title).bold()
                    Text(account.accountNumber).font(.caption).foregroundColor(.secondary)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(12)

                MoveMoneyCard(onMoved: { Task { await load() } })

                Text("Business transactions").font(.headline)
                if transactions.isEmpty {
                    // Real copy-voice fix (item 244, round 7 of the empty-state pass --
                    // native MerchantApp had the same bare strings web's merchant-mfe
                    // just had): auto-recorded, not user-initiated setup.
                    Text("No business transactions yet — once you send or receive money, it'll show up here.").font(.footnote).foregroundColor(.secondary)
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
            account = try await MerchantNetworkClient.shared.getBusinessAccount().account
            transactions = (try? await MerchantNetworkClient.shared.getBusinessTransactions().transactions) ?? []
            error = nil
        } catch {
            account = nil
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
    // Real fix (2026-08-10): found live-testing bank-mfe's identical Group account
    // deposit/withdraw bug -- "To business" and "To personal" move money in opposite
    // directions but shared this one flag+dialog, and onVerified just cleared the
    // flag with no retry at all. Worse than the pure-friction version of this bug:
    // moveToBusiness/moveToPersonal are genuinely different real transfers, so this
    // tracks which direction was actually pending rather than guessing.
    @State private var pendingMoveAction: (() async -> Void)?

    var body: some View {
        if needsDeviceVerification {
            ZStack {
                Color.black.opacity(0.3).ignoresSafeArea()
                DeviceStepUpDialog(
                    onVerified: { let action = pendingMoveAction; pendingMoveAction = nil; if let action { Task { await action() } } else { needsDeviceVerification = false } },
                    onCancel: { pendingMoveAction = nil; needsDeviceVerification = false }
                )
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text("Move money").bold()
                IdsTextField("Amount (RWF)", text: $amountText, keyboardType: .numberPad)
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
        needsDeviceVerification = false
        defer { moving = false }
        do {
            _ = toBusiness
                ? try await MerchantNetworkClient.shared.moveToBusiness(amount: amount)
                : try await MerchantNetworkClient.shared.moveToPersonal(amount: amount)
            amountText = ""
            onMoved()
        } catch NetworkError.deviceNotVerified {
            pendingMoveAction = { await self.move(toBusiness: toBusiness) }
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
            IdsTextField("https://your-server.example.com/webhooks/itunda", text: $webhookUrl)
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

/// Real API key + webhook delivery log/replay -- see NetworkClient.swift's own doc
/// comment. The natural companion to WebhookUrlCard above: generate a key to
/// authenticate programmatic requests, and see whether the configured URL is
/// actually receiving delivery attempts (with a Replay action once all 7 real
/// retries are exhausted).
private struct ApiIntegrationCard: View {
    @State private var apiKey: String?
    @State private var generating = false
    @State private var generateError: String?
    @State private var webhookSecret: String?
    @State private var generatingSecret = false
    @State private var generateSecretError: String?
    @State private var deliveries: [WebhookDeliveryDto]?
    @State private var replayingId: String?
    @State private var deliveriesError: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("API integration").bold()
            Text("For merchants integrating their own systems with itunda.")
                .font(.footnote).foregroundColor(.secondary)

            Button(action: { Task { await generate() } }) {
                Text(generating ? "Generating…" : "Generate a new API key")
                    .bold().foregroundColor(.white)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.brand).cornerRadius(8)
            }
            .disabled(generating)
            if let apiKey {
                Text(apiKey).font(.system(.footnote, design: .monospaced))
                    .padding(10).background(Color(.tertiarySystemBackground)).cornerRadius(8)
            }
            if let generateError {
                Text(generateError).font(.footnote).foregroundColor(.red)
            }

            // Real webhook signature verification (2026-08-30) -- see backend
            // WebhookDeliveryService's own doc comment. Lets a merchant's own server
            // verify a PAYMENT_STATUS_CHANGED POST genuinely came from itunda
            // (HMAC-SHA256 over the raw body, X-Itunda-Signature header).
            Text("Used to verify a webhook delivery genuinely came from itunda — check the X-Itunda-Signature header against an HMAC-SHA256 of the raw request body using this secret.")
                .font(.footnote).foregroundColor(.secondary)
            Button(action: { Task { await generateSecret() } }) {
                Text(generatingSecret ? "Generating…" : "Generate a new webhook secret")
                    .bold().foregroundColor(.white)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.brand).cornerRadius(8)
            }
            .disabled(generatingSecret)
            if let webhookSecret {
                Text(webhookSecret).font(.system(.footnote, design: .monospaced))
                    .padding(10).background(Color(.tertiarySystemBackground)).cornerRadius(8)
            }
            if let generateSecretError {
                Text(generateSecretError).font(.footnote).foregroundColor(.red)
            }

            Text("Recent webhook deliveries").bold().font(.subheadline).padding(.top, 8)
            if let deliveriesError {
                Text(deliveriesError).font(.footnote).foregroundColor(.red)
            }
            if let deliveries {
                if deliveries.isEmpty {
                    // Real copy-voice fix (item 244, round 7): event-driven, not
                    // something to set up further here.
                    Text("No webhook deliveries yet — deliveries will show up here once an event triggers your webhook.").font(.footnote).foregroundColor(.secondary)
                } else {
                    ForEach(deliveries.prefix(20)) { d in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(d.eventType).bold().font(.footnote)
                                Text("\(d.status) · \(d.attemptCount) attempt\(d.attemptCount == 1 ? "" : "s")")
                                    .font(.caption).foregroundColor(d.status == "EXHAUSTED" ? .red : .secondary)
                            }
                            Spacer()
                            if d.status == "EXHAUSTED" {
                                Button(action: { Task { await replay(d.id) } }) {
                                    Text(replayingId == d.id ? "Replaying…" : "Replay")
                                        .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                }
                                .disabled(replayingId == d.id)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            } else {
                Text("Loading…").font(.footnote).foregroundColor(.secondary)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .task { await loadDeliveries() }
    }

    private func loadDeliveries() async {
        do {
            deliveries = try await MerchantNetworkClient.shared.getWebhookDeliveries().deliveries
            deliveriesError = nil
        } catch {
            deliveriesError = "Could not load webhook deliveries."
        }
    }

    private func generate() async {
        generating = true
        generateError = nil
        defer { generating = false }
        do {
            apiKey = try await MerchantNetworkClient.shared.generateApiKey().apiKey
        } catch {
            generateError = "Could not generate an API key."
        }
    }

    private func generateSecret() async {
        generatingSecret = true
        generateSecretError = nil
        defer { generatingSecret = false }
        do {
            webhookSecret = try await MerchantNetworkClient.shared.generateWebhookSecret().webhookSecret
        } catch {
            generateSecretError = "Could not generate a webhook secret."
        }
    }

    private func replay(_ deliveryId: String) async {
        replayingId = deliveryId
        defer { replayingId = nil }
        do {
            _ = try await MerchantNetworkClient.shared.replayWebhookDelivery(deliveryId)
            await loadDeliveries()
        } catch {
            deliveriesError = "Could not replay this delivery."
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
            IdsTextField("Category (e.g. Rwandan, Bakery, Cafe)", text: $category)
                .onChange(of: category) { _ in saved = false }
            IdsTextField("Store photo URL", text: $photoUrl)
                .onChange(of: photoUrl) { _ in saved = false }
            IdsTextField("Minimum order (RWF, blank = none)", text: $minOrderAmount, keyboardType: .numberPad)
                .onChange(of: minOrderAmount) { _ in saved = false }
            IdsTextField("Boosted cashback (0-5%, blank = standard)", text: $cashbackPercent, keyboardType: .decimalPad)
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
