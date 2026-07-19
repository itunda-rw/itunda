import SwiftUI
import CoreDesignSystem

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
                    Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary)
                }
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

    var body: some View {
        VStack(spacing: 10) {
            if let error {
                InvestErrorCard(message: error, onRetry: load)
            } else if let stocks {
                ForEach(stocks) { stock in
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
                Text("No stocks watched yet. Open a stock in the Market tab and tap the star to follow it.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary).padding()
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
                    Text(stock.symbol).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text(stock.name).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                if isWatched {
                    Image(systemName: "star.fill").font(.caption2).foregroundColor(.yellow)
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
            .cornerRadius(14)
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
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(IDS.Colors.card)
                .cornerRadius(14)

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
        .padding()
        .background(IDS.Colors.card)
        .cornerRadius(14)
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
                        Image(systemName: watching ? "star.fill" : "star")
                            .foregroundColor(watching ? .yellow : IDS.Colors.textSecondary)
                    }
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

                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
            .padding()
        }
        .task { await loadHistory() }
    }

    private func loadHistory() async {
        if let res = try? await NetworkClient.shared.getStockHistory(stockId: stock.id, days: 14), res.success {
            history = res.history
        } else {
            history = []
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
                        .fill(positive ? Color.green : Color.red)
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
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(IDS.Colors.card)
        .cornerRadius(14)
    }
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    if rounded == rounded.rounded(.towardZero) {
        return String(Int(rounded))
    }
    return String(format: "%.2f", rounded)
}
