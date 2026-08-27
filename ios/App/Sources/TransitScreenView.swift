import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Kigali public-transit stored-value balance (2026-08-27, direct user follow-up
// after the card-design-picker feature: "after this we will build transit features").
// See the backend's TransitBalance.kt doc comment for the full sourced account of
// Kigali's real Tap&Go fare system (AC Group Ltd, Kigali Bus Services, Royal Express)
// and the honest boundary this simulates -- itunda has no real partnership with any of
// them, so this screen never uses their "Tap&Go" name for itunda's own product. Same
// no-ViewModel, "call NetworkClient.shared directly from Task {} blocks" convention as
// CardScreenView.swift.
private enum TransitMode {
    case loading, noBalance, active
}

private let transitOperators = ["Kigali Bus Services", "Royal Express"]
private let minFare: Double = 200
private let maxFare: Double = 500

struct TransitScreenView: View {
    var onBack: () -> Void = {}
    var onOpenCollect: () -> Void = {}
    @State private var mode: TransitMode = .loading
    @State private var balance: TransitBalanceDto?
    @State private var trips: [TransitTripDto] = []
    @State private var topUpAmount = "1000"
    @State private var operatorName = transitOperators[0]
    @State private var fare: Double = minFare
    @State private var busy = false
    @State private var error: String?
    @State private var tapMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Transit").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    switch mode {
                    case .loading:
                        ProgressView().frame(maxWidth: .infinity).padding(40)
                    case .noBalance, .active:
                        VStack(alignment: .leading, spacing: 4) {
                            Text("itunda Transit balance").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(formatMoney(balance?.balance ?? 0)) RWF").font(.system(size: 28, weight: .heavy))
                        }

                        Text("Kigali's real public buses (Kigali Bus Services, Royal Express) run on a real contactless fare system called Tap&Go, built by AC Group. itunda has no real partnership with them -- this is itunda's own simulated transit balance: real money moves, real fares apply, it just isn't carried by a real bus card reader.")
                            .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                        Button(action: onOpenCollect) {
                            Text("Collecting fares for Kigali Bus Services or Royal Express? Open the collector →")
                                .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Top up").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            HStack(spacing: 8) {
                                IdsTextField("Amount (RWF)", text: $topUpAmount, keyboardType: .numberPad)
                                CardActionButton(title: busy ? "Topping up…" : "Top up", disabled: busy, action: topUp)
                            }
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)

                        if mode == .active {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Tap to pay your fare").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Text("Pick your real operator and fare -- Kigali's real fares run 200-500 RWF depending on distance; itunda has no GPS-derived distance to calculate one automatically.")
                                    .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                                if let tapMessage {
                                    Text(tapMessage).font(.caption).foregroundColor(.green)
                                }
                                HStack(spacing: 8) {
                                    ForEach(transitOperators, id: \.self) { op in
                                        Button(op) { operatorName = op }
                                            .font(.caption).bold()
                                            .foregroundColor(operatorName == op ? .white : IDS.Colors.textPrimary)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .background(operatorName == op ? IDS.Colors.brand : Color(.tertiarySystemBackground))
                                            .cornerRadius(10)
                                    }
                                }
                                HStack {
                                    Slider(value: $fare, in: minFare...maxFare, step: 50)
                                    Text("\(Int(fare)) RWF").font(.subheadline).bold().frame(minWidth: 80, alignment: .trailing)
                                }
                                let insufficientBalance = (balance?.balance ?? 0) < fare
                                CardActionButton(
                                    title: insufficientBalance ? "Balance too low" : (busy ? "Tapping…" : "Tap \(Int(fare)) RWF"),
                                    disabled: busy || insufficientBalance,
                                    action: tapFare
                                )
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)

                            VStack(alignment: .leading, spacing: 6) {
                                Text("Ride history").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                if trips.isEmpty {
                                    EmptyStateView("No transit taps yet — once you tap to pay a fare, they'll show up here.")
                                } else {
                                    ForEach(trips) { trip in
                                        HStack {
                                            Text(trip.operatorName).font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                            Spacer()
                                            Text("\(formatMoney(trip.fare)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        }
                                    }
                                }
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(16)
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { load() }
    }

    private func load() {
        Task {
            do {
                let res = try await NetworkClient.shared.getMyTransitBalance()
                balance = res.balance
                mode = .active
            } catch NetworkError.httpError(let statusCode) where statusCode == 404 {
                mode = .noBalance
            } catch {
                self.error = "Could not load your transit balance."
            }
            if let tripsRes = try? await NetworkClient.shared.getTransitTrips() {
                trips = tripsRes.trips
            }
        }
    }

    private func topUp() {
        guard let amount = Double(topUpAmount), amount > 0 else {
            error = "Enter a real, positive top-up amount."
            return
        }
        busy = true
        error = nil
        Task {
            do {
                balance = try await NetworkClient.shared.topUpTransit(amount: amount).balance
                mode = .active
                load()
            } catch {
                self.error = "Could not top up your transit balance."
            }
            busy = false
        }
    }

    private func tapFare() {
        busy = true
        error = nil
        Task {
            do {
                let res = try await NetworkClient.shared.tapTransitFare(operatorName: operatorName, fare: fare)
                balance = res.balance
                tapMessage = "Tapped \(formatMoney(res.trip.fare)) RWF at \(res.trip.operatorName)"
                load()
            } catch {
                self.error = "Could not tap to pay this fare."
            }
            busy = false
        }
    }
}

private struct CardActionButton: View {
    let title: String
    let disabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).bold().foregroundColor(.white)
                .frame(maxWidth: .infinity).padding(.vertical, 14)
                .background(disabled ? Color.gray : IDS.Colors.brand).cornerRadius(12)
        }
        .disabled(disabled)
    }
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    if rounded == rounded.rounded(.down) {
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: rounded)) ?? String(Int64(rounded))
    }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: rounded)) ?? String(format: "%.2f", rounded)
}
