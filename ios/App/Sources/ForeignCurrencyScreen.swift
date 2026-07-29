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

    @State private var wallets: [Wallet]?
    @State private var conversions: [CurrencyConversionDto] = []
    @State private var error: String?
    @State private var openingCurrency: String?

    private func load() async {
        do {
            let walletsRes = try await NetworkClient.shared.getForeignWallets()
            wallets = walletsRes.wallets
            conversions = (try? await NetworkClient.shared.getMyConversions().conversions) ?? []
            error = nil
        } catch {
            self.error = "Could not load your foreign-currency accounts."
        }
    }

    private func openWallet(_ currency: String) async {
        openingCurrency = currency
        defer { openingCurrency = nil }
        do {
            _ = try await NetworkClient.shared.openForeignWallet(OpenForeignWalletRequest(currency: currency))
            await load()
        } catch {
            self.error = "Could not open this account."
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Foreign currency").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }

                    if let wallets {
                        if wallets.isEmpty {
                            Text("Open a USD, EUR, or GBP account to hold foreign currency and convert between it and RWF at a real live rate.")
                                .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        } else {
                            ForEach(wallets, id: \.id) { wallet in
                                HStack {
                                    Text(wallet.currency).bold()
                                    Spacer()
                                    Text("\(formatFx(wallet.balance)) \(wallet.currency)").bold()
                                }
                                .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                            }
                        }

                        let openCurrencies = Set(wallets.map { $0.currency })
                        let missing = supportedCurrencies.filter { !openCurrencies.contains($0) }
                        if !missing.isEmpty {
                            HStack(spacing: 8) {
                                ForEach(missing, id: \.self) { code in
                                    Button(action: { Task { await openWallet(code) } }) {
                                        Text(openingCurrency == code ? "…" : "+ Open \(code)")
                                            .font(.footnote).bold().foregroundColor(.white)
                                            .padding(.horizontal, 14).padding(.vertical, 10)
                                            .background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(openingCurrency != nil)
                                }
                            }
                        }

                        if !wallets.isEmpty {
                            ConvertPanel(wallets: wallets, onConverted: { Task { await load() } })
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
    let wallets: [Wallet]
    let onConverted: () -> Void

    @State private var toForeign = true
    @State private var foreignCurrency: String
    @State private var amountText = ""
    @State private var rate: Double?
    @State private var submitting = false
    @State private var error: String?
    @State private var success: String?

    init(wallets: [Wallet], onConverted: @escaping () -> Void) {
        self.wallets = wallets
        self.onConverted = onConverted
        _foreignCurrency = State(initialValue: wallets.first?.currency ?? "USD")
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
                ForEach(wallets.map { $0.currency }, id: \.self) { code in
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

private func formatFx(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int(rounded)) : String(format: "%.2f", rounded)
}
