import SwiftUI
import CoreDesignSystem

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

/// Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see
/// VendorCashAdvanceDto's own doc comment for the full sourced account.
/// merchant-mfe shipped first, Android's native merchantapp ported it
/// (VendorCashAdvanceScreen.kt) -- this iOS MerchantApp had zero client for the
/// entire feature until now, found while triaging the defined-but-uncalled-endpoint
/// sweep's remaining candidates (2026-08-05). Straight port of Android's own screen,
/// same real business logic and copy verbatim: an offer computed from the merchant's
/// own real average daily itunda-routed settlement (never a fixed installment the
/// merchant initiates -- repayment is auto-collected as a % of real daily sales), and
/// the same honest disclosure that off-platform cash sales are invisible to both
/// underwriting and collection.
struct VendorCashAdvanceTab: View {
    let merchantId: String

    @State private var advance: VendorCashAdvanceDto?
    @State private var loaded = false
    @State private var offer: VendorCashAdvanceOfferResponse?
    @State private var error: String?
    @State private var busy = false
    @State private var repayAmount = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Isoko Vendor Cash Advance").font(.headline)
                    Text("A cash advance against your own real itunda sales history. There's no fixed repayment schedule -- itunda automatically collects a share of your real QR/card sales here each day until it's paid off. This can only see and collect sales that actually go through itunda; cash you collect off-platform isn't part of this at all.")
                        .font(.footnote).foregroundColor(.secondary)
                }
                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground)).cornerRadius(12)

                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }

                if loaded {
                    if advance == nil, let offer, offer.eligible {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("You're eligible for").font(.footnote).foregroundColor(.secondary)
                            Text("\(Int(offer.offerAmount ?? 0)) RWF").font(.title2).bold()
                            Text("One-time fee: \(Int(offer.feeAmount ?? 0)) RWF -- itunda then collects \(Int(offer.collectionRatePercent ?? 0))% of your real daily itunda-collected sales here until \(Int((offer.offerAmount ?? 0) + (offer.feeAmount ?? 0))) RWF is repaid. Based on your real average of \(Int(offer.averageDailySettlement ?? 0)) RWF/day over your last \(offer.tradingDays ?? 0) real trading days.")
                                .font(.footnote).foregroundColor(.secondary)
                            Button(action: { Task { await apply() } }) {
                                Text(busy ? "Applying…" : "Apply for this advance")
                                    .bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy)
                        }
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }

                    if advance == nil, let offer, !offer.eligible {
                        Text("Not eligible yet -- \(offer.reason ?? "not eligible"). Keep collecting real QR/card sales through itunda and check back.")
                            .font(.footnote).foregroundColor(.secondary)
                            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }

                    if let advance, advance.status == "REQUESTED" {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Your \(formatAmount(Int(advance.principalAmount))) RWF advance was approved and is ready to disburse to your account.")
                                .font(.footnote).foregroundColor(.secondary)
                            Button(action: { Task { await disburse(advance.id) } }) {
                                Text(busy ? "Disbursing…" : "Disburse to my account")
                                    .bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy)
                        }
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }

                    if let advance, advance.status == "DISBURSED" {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Remaining owed").font(.footnote).foregroundColor(.secondary)
                            Text("\(formatAmount(Int(advance.remainingOwed))) RWF").font(.title2).bold()
                            GeometryReader { geo in
                                let progress = advance.totalOwed > 0 ? min(1, max(0, (advance.totalOwed - advance.remainingOwed) / advance.totalOwed)) : 0
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 4).fill(Color(.systemGray5)).frame(height: 8)
                                    RoundedRectangle(cornerRadius: 4).fill(IDS.Colors.brand).frame(width: geo.size.width * progress, height: 8)
                                }
                            }
                            .frame(height: 8)
                            Text("of \(formatAmount(Int(advance.totalOwed))) RWF total owed -- \(Int(advance.collectionRatePercent))% of your real daily itunda sales is collected automatically" + (advance.lastCollectionAt.map { ", last collected \(String($0.prefix(10)))" } ?? "") + ".")
                                .font(.footnote).foregroundColor(.secondary)
                        }
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(12)

                        VStack(alignment: .leading, spacing: 10) {
                            Text("Repay early").font(.headline)
                            IdsTextField("Amount (RWF)", text: $repayAmount, keyboardType: .decimalPad)
                            Button(action: { Task { await repayEarly(advance.id) } }) {
                                Text(busy ? "Repaying…" : "Repay now")
                                    .bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy)
                        }
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let a = try await MerchantNetworkClient.shared.getMyVendorCashAdvance(merchantId: merchantId).advance
            advance = a
            loaded = true
            error = nil
            if a == nil {
                offer = try? await MerchantNetworkClient.shared.getVendorCashAdvanceOffer(merchantId: merchantId)
            }
        } catch {
            self.error = "Could not load your vendor cash advance."
        }
    }

    private func apply() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await MerchantNetworkClient.shared.applyForVendorCashAdvance(merchantId: merchantId)
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't apply for a vendor cash advance."
        } catch {
            self.error = "Couldn't apply for a vendor cash advance."
        }
    }

    private func disburse(_ advanceId: String) async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await MerchantNetworkClient.shared.disburseVendorCashAdvance(advanceId)
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't disburse this advance."
        } catch {
            self.error = "Couldn't disburse this advance."
        }
    }

    private func repayEarly(_ advanceId: String) async {
        guard let value = Double(repayAmount), value > 0 else {
            error = "Enter a real amount."
            return
        }
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await MerchantNetworkClient.shared.repayVendorCashAdvanceEarly(advanceId, amount: value)
            repayAmount = ""
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't repay this advance. Check your balance."
        } catch {
            self.error = "Couldn't repay this advance. Check your balance."
        }
    }
}
