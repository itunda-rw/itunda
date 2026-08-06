import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real recurring-payment ("subscription") detection over a user's own real transaction
/// history -- see rw.itunda.wallet.SubscriptionDetectionService's own doc comment. Plus
/// real Kakao Pay 정기결제/Toss 빌링키-style merchant subscriptions the customer actually
/// authorized (billing-key charges, distinct from the detected-from-history section
/// above: real active authorizations, not a heuristic guess). bank-mfe/Android already
/// have both; this is the first iOS client, mirroring bank-mfe's `SubscriptionsView`
/// exactly.
struct SubscriptionsScreenView: View {
    var onBack: () -> Void = {}

    @State private var detected: [DetectedSubscriptionDto]?
    @State private var estimatedMonthlyTotal: Double = 0
    @State private var billingSubs: [MerchantBillingSubscriptionDto]?
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Subscriptions").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let detected {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Estimated monthly total").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(Int(estimatedMonthlyTotal)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text("Detected from your own real payment history, not a linked-card feed.")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(IDS.Layout.cardCornerRadius)

                        if detected.isEmpty {
                            EmptyStateView("No recurring payments detected yet.")
                        } else {
                            ForEach(detected, id: \.displayName) { s in
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(s.displayName).bold().font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                        Text("\(s.cadence == "WEEKLY" ? "Weekly" : "Monthly") · \(s.occurrenceCount) payments seen")
                                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    Spacer()
                                    VStack(alignment: .trailing, spacing: 2) {
                                        Text("\(Int(s.amount)) RWF").bold().font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                        if s.priceIncreased, let previous = s.previousAmount {
                                            Text("↑ from \(Int(previous)) RWF").font(.caption2).foregroundColor(.red)
                                        }
                                    }
                                }
                                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(IDS.Layout.cardCornerRadius)
                            }
                        }
                    } else {
                        Text("Loading…").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Merchant subscriptions").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                        Text("Plans you've subscribed to. These charge your wallet automatically until you cancel.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        if let error {
                            Text(error).font(.caption).foregroundColor(.red)
                        }
                        if let billingSubs {
                            if billingSubs.isEmpty {
                                EmptyStateView("No merchant subscriptions yet — plans you subscribe to will show up here.")
                            } else {
                                ForEach(billingSubs) { sub in
                                    MerchantBillingSubscriptionRow(subscription: sub, onChanged: { Task { await loadBilling() } })
                                }
                            }
                        } else {
                            Text("Loading…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground)).cornerRadius(IDS.Layout.cardCornerRadius)
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getDetectedSubscriptions()
            detected = res.subscriptions
            estimatedMonthlyTotal = res.estimatedMonthlyTotal
        } catch {
            self.error = "Could not load your subscriptions."
        }
        await loadBilling()
    }

    private func loadBilling() async {
        do {
            billingSubs = try await NetworkClient.shared.getMyBillingSubscriptions().subscriptions
        } catch {
            self.error = "Could not load your subscriptions."
        }
    }
}

private struct MerchantBillingSubscriptionRow: View {
    let subscription: MerchantBillingSubscriptionDto
    let onChanged: () -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(subscription.chargeCount) charge\(subscription.chargeCount == 1 ? "" : "s") so far")
                        .bold().font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                    Text(subscription.status == "ACTIVE"
                        ? "Next charge \(String(subscription.nextChargeAt.prefix(10)))"
                        : "Cancelled \(subscription.cancelledAt.map { String($0.prefix(10)) } ?? "")")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let failure = subscription.lastFailureReason, subscription.status == "ACTIVE" {
                        Text("Last charge failed: \(failure)").font(.caption2).foregroundColor(.red)
                    }
                }
                Spacer()
                if subscription.status == "ACTIVE" {
                    Button(action: { Task { await cancel() } }) {
                        Text(busy ? "…" : "Cancel").bold().font(.caption)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                    }
                    .disabled(busy)
                }
            }
            if let error {
                Text(error).font(.caption2).foregroundColor(.red)
            }
        }
        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
    }

    private func cancel() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.cancelBillingSubscription(subscription.id)
            onChanged()
        } catch {
            self.error = "Could not cancel this subscription."
        }
    }
}
