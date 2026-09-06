import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Kakao Pay 머니굴리기 ("rolling money") round-up auto-saving (rw.itunda.savings.
// RoundUpService, real since well before this session) -- first iOS client for this
// feature (item 113; bank-mfe item 112, Android already had it). Same
// no-ViewModel, "call NetworkClient.shared directly from Task {} blocks" convention as
// YouthAccountScreenView.swift/SpendingScreenView.swift.
//
// Real stock-destination option (Wealth product-completeness pass, 2026-09-06) --
// the backend has supported targetStockId since 2026-07-27, but this view (and
// every other client) only ever wired the goal destination. `stocks` reuses the
// same real Invest market-browse list, not a fabricated/duplicated catalog.
private enum RoundUpDestination { case goal, stock }

struct RoundUpSettingsView: View {
    var onBack: () -> Void = {}
    @State private var settings: RoundUpSettingsDto?
    @State private var loaded = false
    @State private var goals: [SavingsGoal] = []
    @State private var stocks: [StockDto] = []
    @State private var increment: Double = 100
    @State private var destination: RoundUpDestination = .goal
    @State private var goalId = ""
    @State private var stockId = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Round-up savings").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if !loaded {
                        ProgressView().frame(maxWidth: .infinity).padding(40)
                    } else if let current = settings, current.enabled {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Every transfer rounds up to the nearest \(formatAmount(Int(current.roundToNearest))) RWF, saved into your \(current.targetStockId != nil ? "stock" : "goal").")
                                .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                            Button(action: { Task { await toggle(enabled: false) } }) {
                                Text(busy ? "…" : "Turn off").bold()
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                            }
                            .disabled(busy)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    } else {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Round up every transfer to a real RWF increment and auto-save the spare change.")
                                .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                            HStack(spacing: 6) {
                                ForEach(ROUND_UP_INCREMENTS, id: \.self) { value in
                                    Button(action: { increment = value }) {
                                        Text("\(formatAmount(Int(value))) RWF").font(.caption).bold()
                                            .foregroundColor(increment == value ? .white : IDS.Colors.textPrimary)
                                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                                            .background(increment == value ? IDS.Colors.brand : Color(.tertiarySystemBackground))
                                            .cornerRadius(8)
                                    }
                                }
                            }
                            Picker("Save into", selection: $destination) {
                                Text("A savings goal").tag(RoundUpDestination.goal)
                                Text("A stock").tag(RoundUpDestination.stock)
                            }
                            .pickerStyle(.segmented)
                            if destination == .goal {
                                if goals.isEmpty {
                                    Text("Create a savings goal first to enable round-up.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                } else {
                                    Picker("Savings goal", selection: $goalId) {
                                        Text("Choose a savings goal").tag("")
                                        ForEach(goals, id: \.id) { goal in
                                            Text(goal.name).tag(goal.id)
                                        }
                                    }
                                    .pickerStyle(.menu)
                                }
                            } else {
                                if stocks.isEmpty {
                                    Text("No stocks available right now.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                } else {
                                    Picker("Stock", selection: $stockId) {
                                        Text("Choose a stock").tag("")
                                        ForEach(stocks) { stock in
                                            Text("\(stock.symbol) · \(stock.name)").tag(stock.id)
                                        }
                                    }
                                    .pickerStyle(.menu)
                                }
                            }
                            Button(action: { Task { await toggle(enabled: true) } }) {
                                Text(busy ? "Turning on…" : "Turn on round-up").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || (destination == .goal ? goalId.isEmpty : stockId.isEmpty))
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        async let settingsResult = try? NetworkClient.shared.getRoundUpSettings().settings
        async let goalsResult = try? NetworkClient.shared.getSavingsGoals().goals
        async let stocksResult = try? NetworkClient.shared.getStocks().stocks
        settings = await settingsResult ?? nil
        goals = await goalsResult ?? []
        stocks = await stocksResult ?? []
        if let current = settings {
            increment = current.roundToNearest
            goalId = current.targetGoalId ?? ""
            stockId = current.targetStockId ?? ""
            destination = current.targetStockId != nil ? .stock : .goal
        }
        loaded = true
    }

    private func toggle(enabled: Bool) async {
        if enabled && destination == .goal && goalId.isEmpty {
            error = "Choose a savings goal first."
            return
        }
        if enabled && destination == .stock && stockId.isEmpty {
            error = "Choose a stock first."
            return
        }
        busy = true
        error = nil
        do {
            let targetGoal = enabled ? (destination == .goal ? goalId : nil) : (destination == .goal ? settings?.targetGoalId : nil)
            let targetStock = enabled ? (destination == .stock ? stockId : nil) : (destination == .stock ? settings?.targetStockId : nil)
            settings = try await NetworkClient.shared.setRoundUpSettings(enabled: enabled, roundToNearest: increment, targetGoalId: targetGoal, targetStockId: targetStock).settings
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not update round-up settings."
        } catch {
            self.error = "Could not update round-up settings."
        }
        busy = false
    }
}
