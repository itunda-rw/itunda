import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real "agent collects a fare from a rider's presented code" flow (2026-08-27, direct
// user follow-up: "for simplification we need nfc"). Reuses the same
// CustomerPaymentCode every user already generates and shows as a QR via "My payment
// code" (MyPaymentCodeCard.swift) -- see the backend's TransitService.tapFareByCode
// doc comment for why this isn't a new token system. Any logged-in user may act as a
// collector; itunda has no real relationship with actual Kigali conductors to gate
// this against.
//
// Two real transports for the same code: NFC (TransitNfcReader.swift) is tried first
// -- this only ever works against an Android rider's phone, since iOS can never
// emulate a card for a third-party app (see that file's own doc comment) -- falling
// back to the same QrScanCameraView already used for merchant-payment scanning.
private let transitOperators = ["Kigali Bus Services", "Royal Express"]
private let minFare: Double = 200
private let maxFare: Double = 500

struct TransitCollectScreenView: View {
    var onBack: () -> Void = {}
    @State private var code: String?
    @State private var nfcUnavailable = false
    @State private var scanUnavailable = false
    @State private var operatorName = transitOperators[0]
    @State private var fare: Double = minFare
    @State private var busy = false
    @State private var error: String?
    @State private var collected: TransitCollectResultDto?
    @State private var nfcReader = TransitNfcReader()
    // Real gap closed 2026-09-07 (Transit product-completeness pass): bank-mfe's
    // TransitCollectScreen.tsx has had a real 3rd-tier manual-code-entry fallback
    // since it was built; iOS already had the scanUnavailable signal (unlike
    // Android's CameraQrScanner) but only ever showed a dead-end message
    // suggesting the collector "ask the rider to read their code aloud" with no
    // actual way to type it in.
    @State private var manualCodeInput = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text("Collect fare").font(.headline).foregroundColor(IDS.Colors.textPrimary)
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
        }
        .onDisappear { nfcReader.stop() }
    }

    private var scanView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Scan the rider's payment code").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            Text("Ask the rider to open itunda and tap to show their payment code, then hold your phones back-to-back. No NFC? Point your camera at their QR instead.")
                .font(.caption2).foregroundColor(IDS.Colors.textTertiary)
            if nfcUnavailable {
                if scanUnavailable {
                    Text("Camera unavailable -- enter the rider's code instead.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    TextField("Payment code", text: $manualCodeInput)
                        .padding(10).background(IDS.Colors.backgroundPrimary).cornerRadius(8)
                    CardActionButton(title: "Use this code", disabled: manualCodeInput.trimmingCharacters(in: .whitespaces).isEmpty, action: {
                        code = manualCodeInput.trimmingCharacters(in: .whitespaces)
                    })
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
                Text("\(formatAmount(Int(fare))) RWF").font(.subheadline).bold().frame(minWidth: 80, alignment: .trailing)
            }
            CardActionButton(title: busy ? "Collecting…" : "Collect \(formatAmount(Int(fare))) RWF", disabled: busy, action: { collect(code: code) })
        }
    }

    private func collectedView(_ result: TransitCollectResultDto) -> some View {
        VStack(spacing: 8) {
            Text("Collected").font(.system(size: 28, weight: .heavy)).foregroundColor(.green)
            Text("\(formatMoney(result.fare)) RWF · \(result.operatorName)").font(.headline)
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
        manualCodeInput = ""
        nfcReader.start()
    }

    private func collect(code: String) {
        busy = true
        error = nil
        Task {
            do {
                collected = try await NetworkClient.shared.tapTransitFareByCode(code: code, operatorName: operatorName, fare: fare).collected
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

