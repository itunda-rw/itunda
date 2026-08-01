import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model. See the backend's SaccoShareholding.kt doc comment for
// the full sourced account (416 real government-backed cooperatives, one per
// administrative sector, established 2008/2009 -- 4M+ members, RWF 200B+ deposits as
// of 2024). Distinct from IkiminaScreenView.swift (informal rotating-pot ROSCA, no
// shares/dividends): a SACCO member buys real shares and receives periodic real
// dividend distributions tied to the pool's real performance. The second feature in
// this codebase not sourced from Toss/Kakao/Naver/Coupang. Mirrors bank-mfe's
// SaccoSharesSection exactly, same no-ViewModel, "call NetworkClient.shared directly
// from Task {} blocks" convention IkiminaScreenView.swift already established.

struct SaccoScreenView: View {
    var onBack: () -> Void = {}

    @State private var sharesHeld: Double?
    @State private var totalContributed: Double?
    @State private var currentValue: Double?
    @State private var dividends: [SaccoDividendPayoutDto]?
    @State private var error: String?
    @State private var amount = ""
    @State private var busy = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("SACCO shares").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(spacing: 12) {
                    Text("Buy real cooperative shares and earn a real periodic dividend, matching Rwanda's own Umurenge SACCO model.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Shares held").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Text("\(formatMoneySacco(sharesHeld ?? 0)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("Total contributed: \(formatMoneySacco(totalContributed ?? 0)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                    VStack(spacing: 8) {
                        TextField("Amount (RWF)", text: $amount)
                            .keyboardType(.numberPad)
                            .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                        HStack(spacing: 8) {
                            Button(action: { Task { await buy() } }) {
                                Text(busy ? "…" : "Buy shares").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || !((Double(amount) ?? 0) > 0))
                            Button(action: { Task { await redeem() } }) {
                                Text(busy ? "…" : "Redeem").bold().foregroundColor(IDS.Colors.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.secondarySystemBackground)).cornerRadius(10)
                            }
                            .disabled(busy || !((Double(amount) ?? 0) > 0))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Dividend history").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if let dividends {
                            if dividends.isEmpty {
                                Text("No dividends declared yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                ForEach(dividends, id: \.id) { d in
                                    HStack {
                                        Text(String(d.createdAt.prefix(10))).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        Spacer()
                                        Text("+\(formatMoneySacco(d.amount)) RWF").font(.footnote).bold().foregroundColor(.green)
                                    }
                                }
                            }
                        } else {
                            ProgressView()
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMySaccoShareholding()
            sharesHeld = res.shareholding?.sharesHeld
            totalContributed = res.shareholding?.totalContributed
            currentValue = res.currentValue
            error = nil
        } catch {
            self.error = "Could not load your SACCO shareholding."
        }
        do {
            dividends = try await NetworkClient.shared.getMySaccoDividendHistory().payouts
        } catch {
            // Non-critical -- shareholding is the primary view.
        }
    }

    private func buy() async {
        guard let value = Double(amount), value > 0 else { return }
        busy = true
        do {
            _ = try await NetworkClient.shared.buySaccoShares(amount: value)
            amount = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not buy SACCO shares."
        }
        busy = false
    }

    private func redeem() async {
        guard let value = Double(amount), value > 0 else { return }
        busy = true
        do {
            _ = try await NetworkClient.shared.redeemSaccoShares(amount: value)
            amount = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not redeem SACCO shares."
        }
        busy = false
    }
}

private func formatMoneySacco(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
