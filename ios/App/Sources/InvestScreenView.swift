import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Securities-style stock investing UI (2026-07-20) -- the first Invest UI
// this feature has ever had on any client, ported from bank-mfe/Android the same
// session. Day-over-day movement/price history/portfolio history are all real
// deterministic simulations against itunda's own Rwanda RSE catalog, not live market
// data or fabricated randomness -- see the backend's StockCatalog.kt for the full
// account.

private enum InvestMode: String, CaseIterable { case market = "Market", portfolio = "Portfolio", watchlist = "Watchlist" }

struct InvestScreenView: View {
    var onBack: () -> Void = {}
    @State private var mode: InvestMode = .market
    @State private var selectedStock: StockDto?
    @State private var watchlist: [StockDto] = []

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { selectedStock != nil ? (selectedStock = nil) : onBack() }) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Invest").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            if let stock = selectedStock {
                StockDetailContent(
                    stock: stock,
                    isWatched: watchlist.contains(where: { $0.id == stock.id }),
                    onTraded: {},
                    onWatchToggled: { loadWatchlist() }
                )
            } else {
                Picker("", selection: $mode) {
                    ForEach(InvestMode.allCases, id: \.self) { m in Text(m.rawValue).tag(m) }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.bottom, 8)

                ScrollView {
                    switch mode {
                    case .market:
                        MarketContent(watchlist: watchlist, onOpen: { selectedStock = $0 })
                    case .portfolio:
                        PortfolioContent()
                    case .watchlist:
                        WatchlistContent(watchlist: watchlist, onOpen: { selectedStock = $0 })
                    }
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { loadWatchlist() }
    }

    private func loadWatchlist() {
        Task {
            if let res = try? await NetworkClient.shared.getStockWatchlist(), res.success {
                watchlist = res.watchlist ?? []
            }
        }
    }
}

private struct MarketContent: View {
    let watchlist: [StockDto]
    let onOpen: (StockDto) -> Void
    @State private var stocks: [StockDto]?
    @State private var error: String?
    // Real Toss/Naver 해외주식 (overseas stock trading, item 230) -- mirrors bank-mfe's
    // own All/Rwanda(RSE)/Overseas(NASDAQ) filter exactly.
    @State private var marketFilter = "ALL"

    var body: some View {
        VStack(spacing: 10) {
            if let error {
                InvestErrorCard(message: error, onRetry: load)
            } else if let stocks {
                HStack(spacing: 8) {
                    ForEach([("ALL", "All"), ("RSE", "Rwanda (RSE)"), ("NASDAQ", "Overseas")], id: \.0) { id, label in
                        Text(label).font(.caption).bold(marketFilter == id)
                            .foregroundColor(marketFilter == id ? IDS.Colors.brand : IDS.Colors.textSecondary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(marketFilter == id ? IDS.Colors.brand.opacity(0.12) : Color.clear)
                            .clipShape(Capsule())
                            .onTapGesture { marketFilter = id }
                    }
                    Spacer()
                }
                ForEach(stocks.filter { marketFilter == "ALL" || $0.market == marketFilter }) { stock in
                    StockRow(stock: stock, isWatched: watchlist.contains(where: { $0.id == stock.id })) { onOpen(stock) }
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
                let res = try await NetworkClient.shared.getStocks()
                if res.success { stocks = res.stocks ?? [] }
                error = nil
            } catch {
                self.error = "Could not load the real market."
            }
        }
    }
}

private struct WatchlistContent: View {
    let watchlist: [StockDto]
    let onOpen: (StockDto) -> Void

    var body: some View {
        VStack(spacing: 10) {
            if watchlist.isEmpty {
                EmptyStateView("No stocks watched yet — open a stock in the Market tab and tap the star to follow it.")
            } else {
                ForEach(watchlist) { stock in
                    StockRow(stock: stock, isWatched: true) { onOpen(stock) }
                }
            }
        }
        .padding(.horizontal)
    }
}

private struct StockRow: View {
    let stock: StockDto
    let isWatched: Bool
    let onTap: () -> Void
    private var positive: Bool { stock.changePercent >= 0 }

    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(stock.symbol).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if stock.market != "RSE" {
                            Text(stock.market).font(IDS.scaledFont(size: 9, weight: .bold, relativeTo: .caption2)).foregroundColor(IDS.Colors.textSecondary)
                                .padding(.horizontal, 5).padding(.vertical, 2)
                                .background(IDS.Colors.chipBackground).cornerRadius(4)
                        }
                    }
                    Text(stock.name).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                if isWatched {
                    IDS.Icons.star(size: 12, color: .yellow)
                }
                VStack(alignment: .trailing, spacing: 2) {
                    Text("\(formatMoney(stock.price)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    HStack(spacing: 2) {
                        Image(systemName: positive ? "arrow.up.right" : "arrow.down.right").font(.caption2)
                        Text("\(positive ? "+" : "")\(String(format: "%.2f", stock.changePercent))%").font(.caption).bold()
                    }
                    .foregroundColor(positive ? .green : .red)
                }
            }
            .padding()
            .background(IDS.Colors.card)
            .cornerRadius(14).idsCardBorder(cornerRadius: 14)
        }
        .buttonStyle(.plain)
    }
}

private struct PortfolioContent: View {
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

private struct HoldingRow: View {
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
private struct AddFundsCard: View {
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

private struct StockDetailContent: View {
    let stock: StockDto
    let isWatched: Bool
    let onTraded: () -> Void
    let onWatchToggled: () -> Void

    @State private var history: [StockPricePointDto]?
    @State private var shares = ""
    @State private var buyMode = true
    @State private var error: String?
    @State private var submitting = false
    @State private var watching: Bool
    // Real device binding step-up (2026-07-21) -- Stocks buy/sell was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but a
    // bare `catch { }` swallowed it into a generic error, same fix already applied to
    // Transfer/Savings.
    @State private var needsDeviceVerification = false
    // Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- found via
    // a fresh "defined but uncalled" endpoint sweep: the backend shipped fully
    // live-verified 2026-08-17 but had zero client anywhere, on any platform. This is
    // iOS's first wiring for it.
    @State private var alertTargetPrice: Double?
    @State private var alertDirection: String?
    @State private var alertTriggeredAt: String?
    @State private var alertExpanded = false
    @State private var alertInput = ""
    @State private var alertAbove = true
    @State private var alertBusy = false
    @State private var alertError: String?

    init(stock: StockDto, isWatched: Bool, onTraded: @escaping () -> Void, onWatchToggled: @escaping () -> Void) {
        self.stock = stock
        self.isWatched = isWatched
        self.onTraded = onTraded
        self.onWatchToggled = onWatchToggled
        _watching = State(initialValue: isWatched)
    }

    private var positive: Bool { stock.changePercent >= 0 }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("\(stock.symbol) · \(stock.marketCap)").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                    Spacer()
                    Button(action: toggleWatch) {
                        IDS.Icons.star(size: 20, color: watching ? .yellow : IDS.Colors.textSecondary)
                    }
                    .accessibilityLabel(watching ? "Remove from watchlist" : "Add to watchlist")
                }
                Text(stock.name).font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
                Text("\(formatMoney(stock.price)) RWF").font(.largeTitle).bold().foregroundColor(IDS.Colors.textPrimary)
                HStack(spacing: 4) {
                    Image(systemName: positive ? "arrow.up.right" : "arrow.down.right")
                    Text("\(positive ? "+" : "")\(formatMoney(stock.change)) (\(positive ? "+" : "")\(String(format: "%.2f", stock.changePercent))%) today")
                }
                .font(.subheadline).bold().foregroundColor(positive ? .green : .red)

                if let history, !history.isEmpty {
                    Sparkline(values: history.map { $0.price }, positive: positive).frame(height: 48).padding(.top, 6)
                    Text("Last 14 days -- real deterministic simulation, not live RSE data").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                } else if history == nil {
                    ProgressView().frame(maxWidth: .infinity).padding(10)
                }

                Picker("", selection: $buyMode) {
                    Text("Buy").tag(true)
                    Text("Sell").tag(false)
                }
                .pickerStyle(.segmented)
                .padding(.top, 8)

                HStack {
                    TextField("Shares", text: $shares)
                        .keyboardType(.decimalPad)
                        .padding(10)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(10)
                    Button(action: trade) {
                        Text(submitting ? "Working…" : (buyMode ? "Buy" : "Sell"))
                            .foregroundColor(.white).bold()
                            .padding(.horizontal, 20).padding(.vertical, 12)
                            .background(buyMode ? IDS.Colors.brand : Color.red)
                            .cornerRadius(10)
                    }
                    .disabled(submitting)
                }

                DeviceStepUpHost(
                    visible: needsDeviceVerification,
                    onDismiss: { needsDeviceVerification = false },
                    onVerified: {
                        needsDeviceVerification = false
                        trade()
                    }
                )

                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }

                // Real Toss Securities 목표가 알림 (target price alert, section 113/167).
                Divider().padding(.top, 6)
                if let targetPrice = alertTargetPrice {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                IDS.Icons.bell(size: 13, color: IDS.Colors.textPrimary)
                                Text("Alert set: notify when \(alertDirection == "ABOVE" ? "≥" : "≤") \(formatMoney(targetPrice)) RWF")
                                    .font(.caption).bold()
                            }
                            .foregroundColor(IDS.Colors.textPrimary)
                            if alertTriggeredAt != nil {
                                Text("Already triggered -- set a new target to re-arm it.")
                                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                            }
                        }
                        Spacer()
                        Button("Remove", action: clearAlert)
                            .font(.caption).bold().foregroundColor(.red)
                            .disabled(alertBusy)
                    }
                } else if alertExpanded {
                    Text("Notify me when the price goes").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    Picker("", selection: $alertAbove) {
                        Text("Above").tag(true)
                        Text("Below").tag(false)
                    }
                    .pickerStyle(.segmented)
                    HStack {
                        TextField("Target price (RWF)", text: $alertInput)
                            .keyboardType(.decimalPad)
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                        Button(action: setAlert) {
                            Text(alertBusy ? "Working…" : "Set")
                                .foregroundColor(.white).bold()
                                .padding(.horizontal, 20).padding(.vertical, 12)
                                .background(IDS.Colors.brand)
                                .cornerRadius(10)
                        }
                        .disabled(alertBusy)
                    }
                    if let alertError {
                        Text(alertError).font(.caption).foregroundColor(.red)
                    }
                } else {
                    Button(action: { alertExpanded = true }) {
                        HStack(spacing: 6) {
                            IDS.Icons.bell(size: 15, color: IDS.Colors.brand)
                            Text("Set a price alert").font(.caption).bold()
                        }
                        .foregroundColor(IDS.Colors.brand)
                    }
                }
            }
            .padding()
        }
        .task {
            await loadHistory()
            await loadAlert()
        }
    }

    private func loadHistory() async {
        if let res = try? await NetworkClient.shared.getStockHistory(stockId: stock.id, days: 14), res.success {
            history = res.history
        } else {
            history = []
        }
    }

    private func loadAlert() async {
        if let res = try? await NetworkClient.shared.getPriceAlert(stockId: stock.id) {
            alertTargetPrice = res.targetPrice
            alertDirection = res.targetDirection
            alertTriggeredAt = res.alertTriggeredAt
        }
    }

    private func setAlert() {
        guard let target = Double(alertInput), target > 0 else {
            alertError = "Enter a real target price."
            return
        }
        alertBusy = true
        alertError = nil
        Task {
            do {
                let direction = alertAbove ? "ABOVE" : "BELOW"
                _ = try await NetworkClient.shared.setPriceAlert(stockId: stock.id, targetPrice: target, direction: direction)
                alertTargetPrice = target
                alertDirection = direction
                alertTriggeredAt = nil
                alertInput = ""
                alertExpanded = false
                if !watching {
                    watching = true
                    onWatchToggled()
                }
            } catch {
                alertError = "Could not set that alert."
            }
            alertBusy = false
        }
    }

    private func clearAlert() {
        alertBusy = true
        Task {
            do {
                _ = try await NetworkClient.shared.clearPriceAlert(stockId: stock.id)
                alertTargetPrice = nil
                alertDirection = nil
                alertTriggeredAt = nil
            } catch {
                // Non-critical -- same "no error surfaced" convention the watch toggle above uses.
            }
            alertBusy = false
        }
    }

    private func toggleWatch() {
        Task {
            do {
                if watching {
                    _ = try await NetworkClient.shared.unwatchStock(stockId: stock.id)
                } else {
                    _ = try await NetworkClient.shared.watchStock(stockId: stock.id)
                }
                watching.toggle()
                onWatchToggled()
            } catch {
                // Non-critical -- the star just doesn't flip.
            }
        }
    }

    private func trade() {
        guard let shareCount = Double(shares), shareCount > 0 else {
            error = "Enter a real number of shares."
            return
        }
        submitting = true
        needsDeviceVerification = false
        Task {
            do {
                if buyMode {
                    _ = try await NetworkClient.shared.buyStock(stockId: stock.id, shares: shareCount)
                } else {
                    _ = try await NetworkClient.shared.sellStock(stockId: stock.id, shares: shareCount)
                }
                shares = ""
                error = nil
                onTraded()
            } catch NetworkError.deviceNotVerified {
                needsDeviceVerification = true
            } catch {
                self.error = "Could not \(buyMode ? "buy" : "sell") this stock."
            }
            submitting = false
        }
    }
}

// Lightweight dependency-free bar sparkline -- no charting library exists anywhere in
// this app, matching the exact same "not disproportionate to a real MVP chart" call
// bank-mfe's own Sparkline component made the same session.
private struct Sparkline: View {
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

private struct InvestErrorCard: View {
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

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    if rounded == rounded.rounded(.towardZero) {
        return String(Int(rounded))
    }
    return String(format: "%.2f", rounded)
}
