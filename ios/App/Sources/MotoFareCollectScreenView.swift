import SwiftUI
import CoreDesignSystem
import CoreNetwork

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

struct MotoFareCollectScreenView: View {
    var onBack: () -> Void = {}
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

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Collect moto fare").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    if let collected {
                        collectedView(collected)
                    } else if let code {
                        collectFormView(code)
                    } else {
                        if !earningsTrips.isEmpty {
                            earningsSummaryView
                        }
                        scanView
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
        }
        .onDisappear { nfcReader.stop() }
    }

    private func loadEarnings() {
        Task {
            if let result = try? await NetworkClient.shared.getMyMotoFareEarnings() {
                earningsTrips = result.trips
                earningsTotal = result.totalElements
            }
        }
    }

    // Mirrors ride-hailing's own "This week" earnings card in shape, but
    // moto-fare's /earnings endpoint returns a flat trip list, not day-bucketed
    // totals, so the aggregate is summed client-side over whatever page is fetched.
    private var earningsSummaryView: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Your fares").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            HStack {
                VStack(alignment: .leading) {
                    Text("Fares collected").font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    Text("\(earningsTotal)").font(.headline)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text("Total (last \(earningsTrips.count))").font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    Text("\(formatMoney(earningsTrips.reduce(0) { $0 + $1.fare })) RWF").font(.headline).foregroundColor(.green)
                }
            }
            ForEach(earningsTrips.prefix(5)) { trip in
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
                Text("\(Int(fare)) RWF").font(.subheadline).bold().frame(minWidth: 80, alignment: .trailing)
            }
            CardActionButton(title: busy ? "Collecting…" : "Collect \(Int(fare)) RWF", disabled: busy, action: { collect(code: code) })
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
