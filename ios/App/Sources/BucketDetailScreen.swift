import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real per-bucket detail screen (2026-08-31, direct user-supplied Toss Bank
// screenshots: 보관하기/매일모으기 each get their own full-screen ledger, not just a
// compact row). Generalizes AccountLedgerDetailView's own balance-hero/ledger shape
// (Features/Banking) to work for ANY savings bucket (Interest Jar, a Savings Goal,
// a Weekly/Grow31/Upfront plan, the Youth account) -- lives in App/Sources (not
// Features/Banking) because it calls NetworkClient.shared directly, matching
// WeeklySavingsScreenView.swift's own established "no-ViewModel, NetworkClient-
// direct" convention, the same module-boundary reason that file isn't in
// Features/Banking either.
struct BucketDetailScreen: View {
    let title: String
    let subtitle: String?
    let balanceText: String
    var secondaryStatLabel: String? = nil
    var secondaryStatValue: String? = nil
    let fetchTransactions: () async throws -> [BucketTransactionDto]
    var fillLabel: String? = nil
    var onFill: (() -> Void)? = nil
    var withdrawLabel: String? = nil
    var onWithdraw: (() -> Void)? = nil
    let onBack: () -> Void

    @State private var transactions: [BucketTransactionDto]?
    @State private var loadError = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .body).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    if let subtitle {
                        Text(subtitle)
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                    Text(title)
                        .font(IDS.scaledFont(size: 15, weight: .bold, relativeTo: .subheadline))
                        .foregroundColor(IDS.Colors.textPrimary)
                    CountUpText(balanceText)
                        .font(IDS.scaledFont(size: 32, weight: .bold, relativeTo: .largeTitle))
                        .foregroundColor(IDS.Colors.textPrimary)
                    if let secondaryStatLabel, let secondaryStatValue {
                        Text("\(secondaryStatLabel): \(secondaryStatValue)")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                    if onFill != nil || onWithdraw != nil {
                        HStack(spacing: 10) {
                            if let onFill {
                                Button(action: onFill) {
                                    Text(fillLabel ?? "Fill")
                                        .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .body))
                                        .foregroundColor(IDS.Colors.brand)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 44)
                                        .background(IDS.Colors.backgroundSecondary)
                                        .cornerRadius(10)
                                }
                            }
                            if let onWithdraw {
                                Button(action: onWithdraw) {
                                    Text(withdrawLabel ?? "Withdraw")
                                        .font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .body))
                                        .foregroundColor(IDS.Colors.brand)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 44)
                                        .background(IDS.Colors.backgroundSecondary)
                                        .cornerRadius(10)
                                }
                            }
                        }
                        .padding(.top, 8)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)

                Spacer().frame(height: 24)

                Group {
                    if loadError {
                        Text("Could not load this account's history.")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(.red)
                    } else if transactions == nil {
                        Text("Loading…")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textTertiary)
                    } else if transactions!.isEmpty {
                        // Real, honest empty state (2026-08-31) -- a pre-existing bucket
                        // migrated to per-bucket ledger isolation on the day this shipped
                        // has no itemized history before that point.
                        Text("No transactions to show yet. If this account already held money before today, its itemized history starts from here.")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textTertiary)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(transactions!.sorted(by: { $0.createdAt > $1.createdAt })) { tx in
                                BucketTransactionRow(tx: tx)
                                Divider()
                            }
                        }
                    }
                }
                .padding(.horizontal, 24)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
        .task {
            do {
                transactions = try await fetchTransactions()
            } catch {
                loadError = true
            }
        }
    }
}

struct BucketTransactionRow: View {
    let tx: BucketTransactionDto

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(tx.isCredit ? IDS.Colors.brand : IDS.Colors.textTertiary)
                .frame(width: 38, height: 38)
                .overlay(
                    Image(systemName: tx.isCredit ? "arrow.down.left" : "arrow.up.right")
                        .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .callout))
                        .foregroundColor(.white)
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(tx.description).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary).lineLimit(1)
                Text(bucketLedgerFullDateTime(tx.createdAt)).font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textTertiary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(tx.isCredit ? "+" : "-")\(formatBucketAmount(tx.amount))")
                    .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout))
                    .foregroundColor(tx.isCredit ? IDS.Colors.brand : IDS.Colors.textPrimary)
                Text(formatBucketAmount(tx.balanceAfter))
                    .font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2))
                    .foregroundColor(IDS.Colors.textTertiary)
            }
        }
        .padding(.vertical, 10)
    }
}

private func formatBucketAmount(_ value: Double) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.maximumFractionDigits = 0
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

func bucketLedgerFullDateTime(_ iso: String) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = formatter.date(from: iso) ?? {
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)
    }()
    guard let date else { return iso }
    let display = DateFormatter()
    display.dateFormat = "MMM d, yyyy HH:mm"
    display.locale = Locale(identifier: "en_US")
    return display.string(from: date)
}
