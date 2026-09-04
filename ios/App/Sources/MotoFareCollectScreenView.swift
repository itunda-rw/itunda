import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same per-file shape TransactionHistoryScreen.swift
// already established.
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

// Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
// can make pay for tax and moto as well"). Mirrors TransitCollectScreenView.swift's
// shape -- same real CustomerPaymentCode primitive, same NFC-first/camera-fallback
// pattern (reuses TransitNfcReader as-is: the wire protocol is a bare payment-code
// string, not transit-specific, despite the class name) -- but simpler: no operator
// picker (a Kigali moto-taxi driver is an individual, not a fixed-route company), and
// a different, real sourced fare range (400-6000 RWF vs transit's 200-500). See the
// backend's MotoFareTrip.kt doc comment for the full sourced account.
private let minFare: Double = 400
private let maxFare: Double = 6000

private enum MotoFareTab {
    case trips
    case collect
}

struct MotoFareCollectScreenView: View {
    var onBack: () -> Void = {}
    // Real rider/driver sub-tab toggle (uncalled-endpoint sweep, 2026-09-02),
    // mirroring RidesView/DesignatedDriverView's own "customer side vs. provider
    // side" pattern -- most people opening this screen are riders checking their
    // own trips, not drivers about to scan a code, so trips is the default.
    @State private var tab: MotoFareTab = .trips
    @State private var code: String?
    @State private var nfcUnavailable = false
    @State private var scanUnavailable = false
    @State private var fare: Double = minFare
    @State private var busy = false
    @State private var error: String?
    @State private var collected: MotoFareCollectResultDto?
    @State private var nfcReader = TransitNfcReader()
    // Real gap found live (uncalled-endpoint sweep, 2026-08-29): this collect flow
    // existed with zero way for a driver to ever see what they'd collected.
    @State private var earningsTrips: [MotoFareTripDto] = []
    @State private var earningsTotal = 0
    // Real gap found live (uncalled-endpoint sweep, 2026-09-02): see
    // getMyMotoFareTripsAsRider's own doc comment on NetworkClient+CoreServices.swift.
    @State private var riderTrips: [MotoFareTripDto] = []
    @State private var riderTripsTotal = 0
    @State private var riderTripsLoaded = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Moto fare").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            tabRow

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if tab == .trips {
                        riderTripsContent
                    } else {
                        if let error {
                            Text(error).font(.footnote).foregroundColor(.red)
                        }
                        if let collected {
                            collectedView(collected)
                        } else if let code {
                            collectFormView(code)
                        } else {
                            if !earningsTrips.isEmpty {
                                tripSummaryView(trips: earningsTrips, total: earningsTotal, isDriver: true)
                            }
                            scanView
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .onAppear {
            nfcReader.onCodeRead = { code = $0 }
            nfcReader.onUnavailable = { nfcUnavailable = true }
            nfcReader.start()
            loadEarnings()
            loadRiderTrips()
        }
        .onDisappear { nfcReader.stop() }
    }

    private var tabRow: some View {
        HStack(spacing: 4) {
            ForEach([MotoFareTab.trips, .collect], id: \.self) { candidate in
                Button(action: { tab = candidate }) {
                    Text(candidate == .trips ? "My trips" : "Collect")
                        .font(.subheadline).bold()
                        .foregroundColor(tab == candidate ? .white : IDS.Colors.textTertiary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(tab == candidate ? IDS.Colors.brand : Color.clear)
                        .cornerRadius(8)
                }
            }
        }
        .padding(4)
        .background(IDS.Colors.backgroundTertiary)
        .cornerRadius(10)
        .padding(.horizontal)
    }

    // Real rider-side trip history (uncalled-endpoint sweep, 2026-09-02) -- a rider
    // only ever pays by showing their existing payment code to a driver, so unlike
    // the driver there's no scan/collect action here -- just their own past trips.
    private var riderTripsContent: some View {
        Group {
            if !riderTripsLoaded {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            } else if riderTrips.isEmpty {
                Text("No moto-taxi trips yet -- show your payment code to a driver next time you tap to pay.")
                    .font(.footnote).foregroundColor(IDS.Colors.textTertiary)
            } else {
                tripSummaryView(trips: riderTrips, total: riderTripsTotal, isDriver: false)
            }
        }
    }

    private func loadEarnings() {
        Task {
            if let result = try? await NetworkClient.shared.getMyMotoFareEarnings() {
                earningsTrips = result.trips
                earningsTotal = result.totalElements
            }
        }
    }

    private func loadRiderTrips() {
        Task {
            let result = try? await NetworkClient.shared.getMyMotoFareTripsAsRider()
            riderTrips = result?.trips ?? []
            riderTripsTotal = result?.totalElements ?? 0
            riderTripsLoaded = true
        }
    }

    // Real driver earnings / rider trip-history summary -- mirrors ride-hailing's
    // own "This week" earnings card in shape, but moto-fare's /earnings and /trips
    // endpoints both return a flat trip list, not day-bucketed totals, so the
    // aggregate is summed client-side over whatever page is fetched. Shared between
    // both roles (uncalled-endpoint sweep, 2026-09-02: the rider side of this same
    // real view was never built, only the driver side was, since the driver's own
    // uncalled-endpoint fix on 2026-08-29).
    private func tripSummaryView(trips: [MotoFareTripDto], total: Int, isDriver: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(isDriver ? "Your fares" : "Your trips").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            HStack {
                VStack(alignment: .leading) {
                    Text(isDriver ? "Fares collected" : "Trips paid").font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    Text("\(total)").font(.headline)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("Total (last \(trips.count))").font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    Text("\(formatMoney(trips.reduce(0) { $0 + $1.fare })) RWF").font(.headline).foregroundColor(isDriver ? .green : IDS.Colors.textPrimary)
                }
            }
            ForEach(trips.prefix(5)) { trip in
                HStack {
                    Text(trip.createdAt).font(.caption).foregroundColor(IDS.Colors.textTertiary)
                    Spacer()
                    Text("\(formatMoney(trip.fare)) RWF").font(.caption).bold()
                }
            }
        }
        .padding(.bottom, 8)
    }

    private var scanView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Scan the rider's payment code").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text("Ask the rider to open itunda and tap to show their payment code, then hold your phones back-to-back. No NFC? Point your camera at their QR instead. Fares go straight into your own itunda account -- no fee.")
                .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
            if nfcUnavailable {
                if scanUnavailable {
                    Text("Camera unavailable -- try again or ask the rider to read their code aloud.")
                        .font(.caption).foregroundColor(.red)
                } else {
                    QrScanCameraView(onDetect: { code = $0 }, onUnavailable: { scanUnavailable = true })
                        .frame(height: 320)
                        .cornerRadius(16)
                }
            } else {
                ProgressView("Waiting for a tap…").frame(maxWidth: .infinity).padding(40)
            }
        }
    }

    private func collectFormView(_ code: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Collect fare").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            HStack {
                Slider(value: $fare, in: minFare...maxFare, step: 100)
                Text("\(formatAmount(Int(fare))) RWF").font(.subheadline).bold().frame(minWidth: 80, alignment: .trailing)
            }
            CardActionButton(title: busy ? "Collecting…" : "Collect \(formatAmount(Int(fare))) RWF", disabled: busy, action: { collect(code: code) })
        }
    }

    private func collectedView(_ result: MotoFareCollectResultDto) -> some View {
        VStack(spacing: 8) {
            Text("Collected").font(.system(size: 28, weight: .heavy)).foregroundColor(.green)
            Text("\(formatMoney(result.fare)) RWF").font(.headline)
            CardActionButton(title: "Collect next fare", disabled: false, action: reset)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 24)
    }

    private func reset() {
        code = nil
        collected = nil
        error = nil
        nfcUnavailable = false
        scanUnavailable = false
        nfcReader.start()
    }

    private func collect(code: String) {
        busy = true
        error = nil
        Task {
            do {
                collected = try await NetworkClient.shared.collectMotoFare(code: code, fare: fare).collected
                loadEarnings()
            } catch let NetworkError.httpErrorWithMessage(_, message) {
                self.error = message ?? "Could not collect this fare."
            } catch {
                self.error = "Could not collect this fare."
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
