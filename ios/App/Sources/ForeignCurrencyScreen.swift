import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (item 160) -- see
// NetworkClient.swift's own doc comment. Android's main app already has this
// (ForeignCurrencyScreen.kt, ItundaAppScreen.kt); bank-mfe got it in item 154. This is
// the iOS port, mirroring Android's own screen shape (open-per-currency, convert form
// with a live rate quote, recent conversions).
private let supportedCurrencies = ["USD", "EUR", "GBP"]

struct ForeignCurrencyScreenView: View {
    var onBack: () -> Void = {}

    @State private var accounts: [Account]?
    @State private var conversions: [CurrencyConversionDto] = []
    @State private var rateAlerts: [ExchangeRateAlertDto] = []
    @State private var error: String?
    @State private var openingCurrency: String?

    private func load() async {
        do {
            let accountsRes = try await NetworkClient.shared.getForeignAccounts()
            accounts = accountsRes.accounts
            conversions = (try? await NetworkClient.shared.getMyConversions().conversions) ?? []
            rateAlerts = (try? await NetworkClient.shared.getMyRateAlerts().alerts) ?? []
            error = nil
        } catch {
            self.error = "Could not load your foreign-currency accounts."
        }
    }

    private func openAccount(_ currency: String) async {
        openingCurrency = currency
        defer { openingCurrency = nil }
        do {
            _ = try await NetworkClient.shared.openForeignAccount(OpenForeignAccountRequest(currency: currency))
            await load()
        } catch NetworkError.httpError(let statusCode) where statusCode == 409 {
            // FOREIGN_ACCOUNT_ALREADY_EXISTS in practice (matches Android's identical
            // ForeignCurrencyScreen.kt fix, 2026-08-15) -- the account genuinely
            // already exists. Resolve forward: reload and show it instead of a
            // dead-end error.
            await load()
        } catch {
            self.error = "Could not open this account."
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Foreign currency").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }

                    if let accounts {
                        if accounts.isEmpty {
                            Text("Open a USD, EUR, or GBP account to hold foreign currency and convert between it and RWF at a real live rate.")
                                .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        } else {
                            ForEach(accounts, id: \.id) { account in
                                HStack {
                                    Text(account.currency).bold()
                                    Spacer()
                                    Text("\(formatFx(account.balance)) \(account.currency)").bold()
                                }
                                .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                            }
                        }

                        let openCurrencies = Set(accounts.map { $0.currency })
                        let missing = supportedCurrencies.filter { !openCurrencies.contains($0) }
                        if !missing.isEmpty {
                            HStack(spacing: 8) {
                                ForEach(missing, id: \.self) { code in
                                    Button(action: { Task { await openAccount(code) } }) {
                                        Text(openingCurrency == code ? "…" : "+ Open \(code)")
                                            .font(.footnote).bold().foregroundColor(.white)
                                            .padding(.horizontal, 14).padding(.vertical, 10)
                                            .background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(openingCurrency != nil)
                                }
                            }
                        }

                        if !accounts.isEmpty {
                            ConvertPanel(accounts: accounts, onConverted: { Task { await load() } })
                            Text("Rate alerts").bold()
                            RateAlertsPanel(accounts: accounts, alerts: rateAlerts, onChanged: { Task { await load() } })
                        }
                    } else {
                        ProgressView()
                    }

                    if !conversions.isEmpty {
                        Text("Recent conversions").bold()
                        ForEach(conversions) { c in
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(formatFx(c.fromAmount)) \(c.fromCurrency) → \(formatFx(c.toAmount)) \(c.toCurrency)").font(.subheadline).bold()
                                Text("Rate \(c.rate) · itunda fee \(formatFx(c.marginAmount)) \(c.toCurrency)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }
}

private struct ConvertPanel: View {
    let accounts: [Account]
    let onConverted: () -> Void

    @State private var toForeign = true
    @State private var foreignCurrency: String
    @State private var amountText = ""
    @State private var rate: Double?
    @State private var submitting = false
    @State private var error: String?
    @State private var success: String?

    init(accounts: [Account], onConverted: @escaping () -> Void) {
        self.accounts = accounts
        self.onConverted = onConverted
        _foreignCurrency = State(initialValue: accounts.first?.currency ?? "USD")
    }

    private var fromCurrency: String { toForeign ? "RWF" : foreignCurrency }
    private var toCurrency: String { toForeign ? foreignCurrency : "RWF" }
    private var previewAmount: Double? {
        guard let amt = Double(amountText.trimmingCharacters(in: .whitespaces)), let r = rate else { return nil }
        return amt * r * 0.985
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Picker("", selection: $toForeign) {
                Text("RWF → foreign").tag(true)
                Text("Foreign → RWF").tag(false)
            }
            .pickerStyle(.segmented)

            HStack(spacing: 8) {
                ForEach(accounts.map { $0.currency }, id: \.self) { code in
                    Button(action: { foreignCurrency = code }) {
                        Text(code).font(.footnote).bold()
                            .foregroundColor(code == foreignCurrency ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(code == foreignCurrency ? IDS.Colors.brand : IDS.Colors.backgroundPrimary)
                            .cornerRadius(8)
                    }
                }
            }

            TextField("Amount (\(fromCurrency))", text: $amountText)
                .keyboardType(.decimalPad)
                .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)

            if let rate { Text("Live rate: 1 \(fromCurrency) = \(String(format: "%.4f", rate)) \(toCurrency)").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
            if let previewAmount { Text("You'll receive ~\(formatFx(previewAmount)) \(toCurrency) (after itunda's 1.5% fee)").font(.subheadline).bold().foregroundColor(.green) }
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            if let success { Text(success).font(.caption).foregroundColor(.green) }

            Button(action: { Task { await convert() } }) {
                Text(submitting ? "Converting…" : "Convert").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        .task(id: "\(fromCurrency)-\(toCurrency)") {
            rate = try? await NetworkClient.shared.getExchangeRate(from: fromCurrency, to: toCurrency).rate
        }
    }

    private func convert() async {
        guard let amount = Double(amountText.trimmingCharacters(in: .whitespaces)), amount > 0 else {
            error = "Enter a real amount."
            return
        }
        submitting = true
        error = nil
        success = nil
        defer { submitting = false }
        do {
            let res = try await NetworkClient.shared.convertCurrency(ConvertCurrencyRequest(fromCurrency: fromCurrency, toCurrency: toCurrency, amount: amount))
            success = "Converted -- \(formatFx(res.conversion.toAmount)) \(toCurrency) credited."
            amountText = ""
            onConverted()
        } catch {
            self.error = "Could not convert this amount."
        }
    }
}

// Real Toss 외환 환율 알림 (exchange rate alert, section 121/168) -- see
// NetworkClient.swift's SetRateAlertRequest doc comment. Backend shipped fully with a
// live-verified-safe scheduler but zero client caller anywhere; found via a fresh
// uncalled-endpoint sweep, same pattern as section 113/167's stock target-price alert
// (InvestScreenView.swift).
private struct RateAlertsPanel: View {
    let accounts: [Account]
    let alerts: [ExchangeRateAlertDto]
    let onChanged: () -> Void

    @State private var currency: String
    @State private var above = true
    @State private var targetText = ""
    @State private var submitting = false
    @State private var error: String?

    init(accounts: [Account], alerts: [ExchangeRateAlertDto], onChanged: @escaping () -> Void) {
        self.accounts = accounts
        self.alerts = alerts
        self.onChanged = onChanged
        _currency = State(initialValue: accounts.first?.currency ?? "USD")
    }

    private func alert(for code: String) -> ExchangeRateAlertDto? {
        alerts.first { $0.fromCurrency == "RWF" ? $0.toCurrency == code : $0.fromCurrency == code }
    }

    private func clear(_ code: String) async {
        do {
            _ = try await NetworkClient.shared.clearRateAlert(fromCurrency: "RWF", toCurrency: code)
            onChanged()
        } catch {
            // Non-critical -- same "no error surfaced" convention as elsewhere in this screen.
        }
    }

    private func setAlert() async {
        guard let target = Double(targetText.trimmingCharacters(in: .whitespaces)), target > 0 else {
            error = "Enter a real target rate."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.setRateAlert(fromCurrency: "RWF", toCurrency: currency, targetRate: target, direction: above ? "ABOVE" : "BELOW")
            targetText = ""
            onChanged()
        } catch {
            self.error = "Could not set that alert."
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(accounts.map { $0.currency }, id: \.self) { code in
                if let a = alert(for: code) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("RWF/\(code): notify when \(a.direction == "ABOVE" ? "≥" : "≤") \(a.targetRate)").font(.subheadline).bold()
                            if a.alertTriggeredAt != nil {
                                Text("Already triggered -- set a new target to re-arm it.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                        }
                        Spacer()
                        Button("Remove") { Task { await clear(code) } }.font(.footnote).bold().foregroundColor(.red)
                    }
                    .padding(14).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    ForEach(accounts.map { $0.currency }, id: \.self) { code in
                        Button(action: { currency = code }) {
                            Text("RWF/\(code)").font(.footnote).bold()
                                .foregroundColor(code == currency ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .background(code == currency ? IDS.Colors.brand : IDS.Colors.backgroundPrimary)
                                .cornerRadius(8)
                        }
                    }
                }
                Picker("", selection: $above) {
                    Text("Above").tag(true)
                    Text("Below").tag(false)
                }
                .pickerStyle(.segmented)
                TextField("Target rate (1 RWF = ? \(currency))", text: $targetText)
                    .keyboardType(.decimalPad)
                    .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                Button(action: { Task { await setAlert() } }) {
                    Text(submitting ? "Setting…" : "Set alert").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 14)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(submitting)
            }
            .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
        }
    }
}

private func formatFx(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int(rounded)) : String(format: "%.2f", rounded)
}
