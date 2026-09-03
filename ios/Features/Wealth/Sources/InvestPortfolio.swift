import SwiftUI
import CoreDesignSystem
import CoreNetwork

struct PortfolioContent: View {
    @State private var portfolio: StockPortfolioDto?
    @State private var history: [PortfolioValuePointDto]?
    @State private var error: String?

    var body: some View {
        VStack(spacing: 10) {
            if let error {
                InvestErrorCard(message: error, onRetry: load)
            } else if let portfolio {
                let positive = portfolio.totalReturn >= 0
                VStack(alignment: .leading, spacing: 6) {
                    Text("Total value").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                    Text("\(formatMoney(portfolio.totalValue)) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("\(positive ? "+" : "")\(formatMoney(portfolio.totalReturn)) RWF (\(positive ? "+" : "")\(String(format: "%.2f", portfolio.totalReturnPercent))%)")
                        .font(.subheadline).bold().foregroundColor(positive ? .green : .red)
                    if let history, !history.isEmpty {
                        Sparkline(values: history.map { $0.value }, positive: positive).frame(height: 48).padding(.top, 6)
                        Text("Last 30 days -- based on your current holdings applied to real historical prices, not a full historical reconstruction")
                            .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)

                AddFundsCard(onFunded: load)
                Divider().overlay(IDS.Colors.divider)

                if portfolio.holdings.isEmpty {
                    Text("You don't hold any real shares yet. Browse the Market tab to buy some.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary).padding()
                } else {
                    ForEach(portfolio.holdings) { holding in HoldingRow(holding: holding) }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            }
        }
        .padding(.horizontal)
        .task { load() }
    }

    private func load() {
        Task {
            do {
                async let portfolioRes = NetworkClient.shared.getStockPortfolio()
                async let historyRes = NetworkClient.shared.getPortfolioHistory()
                let (p, h) = try await (portfolioRes, historyRes)
                if p.success { portfolio = p.portfolio }
                if h.success { history = h.history }
                error = nil
            } catch {
                self.error = "Could not load your real portfolio."
            }
        }
    }
}

struct HoldingRow: View {
    let holding: StockHoldingDto
    private var positive: Bool { holding.returnPercent >= 0 }

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(holding.symbol).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Text("\(formatMoney(holding.value)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            }
            HStack {
                Text("\(formatMoney(holding.shares)) shares @ \(formatMoney(holding.avgPrice)) avg").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                Spacer()
                Text("\(positive ? "+" : "")\(String(format: "%.2f", holding.returnPercent))%").font(.caption).bold().foregroundColor(positive ? .green : .red)
            }
        }
        .padding(.vertical, 10)
    }
}

// Real Investment-account top-up (2026-08-04) -- see NetworkClient.swift's own
// FundInvestmentRequest doc comment. Without this, a user with no pre-seeded
// investment balance had no in-app way to ever actually buy a stock.
struct AddFundsCard: View {
    let onFunded: () -> Void

    @State private var expanded = false
    @State private var amount = ""
    @State private var busy = false
    @State private var error: String?
    @State private var needsDeviceVerification = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Investment cash").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button(expanded ? "Cancel" : "Add funds") { expanded.toggle(); error = nil }
                    .font(.caption).bold().foregroundColor(IDS.Colors.brand)
            }
            Text("Move money from your main account into your investment account.")
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            if expanded {
                HStack {
                    TextField("Amount (RWF)", text: $amount)
                        .keyboardType(.decimalPad)
                        .padding(10)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(10)
                    Button(action: fund) {
                        Text(busy ? "Working…" : "Add")
                            .foregroundColor(.white).bold()
                            .padding(.horizontal, 20).padding(.vertical, 12)
                            .background(IDS.Colors.brand)
                            .cornerRadius(10)
                    }
                    .disabled(busy)
                }
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        DeviceStepUpHost(
            visible: needsDeviceVerification,
            onDismiss: { needsDeviceVerification = false },
            onVerified: { needsDeviceVerification = false; fund() }
        )
    }

    private func fund() {
        guard let value = Double(amount), value > 0 else {
            error = "Enter a real amount."
            return
        }
        busy = true
        needsDeviceVerification = false
        Task {
            do {
                _ = try await NetworkClient.shared.fundInvestmentAccount(amount: value)
                amount = ""
                expanded = false
                error = nil
                onFunded()
            } catch NetworkError.deviceNotVerified {
                needsDeviceVerification = true
            } catch {
                self.error = "Could not add funds."
            }
            busy = false
        }
    }
}

// Lightweight dependency-free bar sparkline -- no charting library exists anywhere in
// this app, matching the exact same "not disproportionate to a real MVP chart" call
// bank-mfe's own Sparkline component made the same session.
struct Sparkline: View {
    let values: [Double]
    let positive: Bool

    var body: some View {
        GeometryReader { geo in
            let minV = values.min() ?? 0
            let maxV = values.max() ?? 1
            let range = max(maxV - minV, 0.0001)
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(Array(values.enumerated()), id: \.offset) { _, v in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(positive ? IDS.Colors.success : Color.red)
                        .frame(height: max(4, geo.size.height * CGFloat((v - minV) / range)))
                }
            }
            .frame(maxWidth: .infinity, alignment: .bottom)
        }
    }
}

struct InvestErrorCard: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(message).font(.caption).foregroundColor(.red)
            Button("Retry", action: onRetry).font(.caption).bold().foregroundColor(IDS.Colors.brand)
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
