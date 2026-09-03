import SwiftUI
import CoreDesignSystem
import CoreNetwork

struct StockDetailContent: View {
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
