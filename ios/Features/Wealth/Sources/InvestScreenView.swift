import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Toss Securities-style stock investing UI (2026-07-20) -- the first Invest UI
// this feature has ever had on any client, ported from bank-mfe/Android the same
// session. Day-over-day movement/price history/portfolio history are all real
// deterministic simulations against itunda's own Rwanda RSE catalog, not live market
// data or fabricated randomness -- see the backend's StockCatalog.kt for the full
// account.
//
// Real content moved 2026-09-03 (Wealth Feature-module extraction, mirroring the same
// Android move the same session): relocated from App/Sources/InvestScreenView.swift,
// zero cross-module blockers beyond the standard public/public-init requirement every
// other extracted screen already carries. Split across InvestScreenView.swift (market/
// watchlist), InvestPortfolio.swift (portfolio + add-funds), and StockDetailScreen.swift
// (buy/sell + price alerts) to stay under the 500-line file-size-lint cap.

enum InvestMode: String, CaseIterable { case market = "Market", portfolio = "Portfolio", watchlist = "Watchlist" }

public struct InvestScreenView: View {
    var onBack: () -> Void
    @State private var mode: InvestMode = .market
    @State private var selectedStock: StockDto?
    @State private var watchlist: [StockDto] = []

    public init(onBack: @escaping () -> Void = {}) {
        self.onBack = onBack
    }

    public var body: some View {
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

struct MarketContent: View {
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

struct WatchlistContent: View {
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

struct StockRow: View {
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
