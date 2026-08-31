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

/// Real Toss Bank reference (20 screenshots, 2026-08-21, direct user instruction:
/// "should look 100% like in this images pixels by pixels"): the real ledger
/// drill-in, reached by tapping AccountSummaryCard's balance -- structurally
/// parallel to bank-mfe's AccountDetailScreen.tsx and Android's own
/// AccountDetailScreen, all three built the same session from the same real
/// reference. Send is bottom-pinned (not scrolling with the ledger above it) per
/// the same live follow-up ("those buttons at bottom") that corrected the first
/// pass, which had the ledger folded directly into BankView. Each row carries a
/// real category icon (classified from the row's own title text, not a
/// fabricated per-merchant logo) matching Android's identical ledgerRowIcon fix
/// and bank-mfe's identical AccountDetailScreen.tsx one.
public struct AccountLedgerDetailView: View {
    let balanceText: String
    let accountNumber: String?
    let transactions: [RecentTransactionRowData]
    let onBack: () -> Void
    let onSend: () -> Void
    // Real Toss Bank Card/Manage top-bar pair (2026-09-01, direct user-supplied
    // Toss screenshots) -- matches bank-mfe's/Android's identical AccountDetailScreen
    // top bar (see AccountManageScreen.swift's own doc comment for what "Manage"
    // opens). Defaulted to {} so the FeatureBankingExample preview target and any
    // other caller that doesn't wire these keeps compiling untouched.
    var onOpenCard: () -> Void = {}
    var onOpenManage: () -> Void = {}

    // Real fix (2026-08-24, direct user follow-up: "no I mean presable effect" --
    // clarifying that the ledger row's missing press feedback was really pointing
    // at a bigger gap, that tapping a row didn't go anywhere at all). Matches
    // Android's/bank-mfe's identical selectedTransaction + TransactionDetailScreen.
    @State private var selectedTransaction: RecentTransactionRowData?
    // Real interest-claim banner (2026-09-01) -- web's/Android's own
    // AccountDetailScreen already had this ("Interest 7 RWF" + "Get interest"
    // chip); iOS never did. FeatureBanking already depends on CoreNetwork (see
    // BankView.swift's own identical NetworkClient.shared usage), so this is
    // fetched directly rather than threaded in as a prop.
    @State private var jar: InterestJar?
    @State private var claiming = false

    public init(balanceText: String, accountNumber: String?, transactions: [RecentTransactionRowData], onBack: @escaping () -> Void, onSend: @escaping () -> Void, onOpenCard: @escaping () -> Void = {}, onOpenManage: @escaping () -> Void = {}) {
        self.balanceText = balanceText
        self.accountNumber = accountNumber
        self.transactions = transactions
        self.onBack = onBack
        self.onSend = onSend
        self.onOpenCard = onOpenCard
        self.onOpenManage = onOpenManage
    }

    private func claim() async {
        claiming = true
        defer { claiming = false }
        // Non-critical -- the claim banner just stays as-is on failure, matching
        // bank-mfe's AccountDetailScreen.handleClaim's own established tolerance.
        _ = try? await NetworkClient.shared.claimInterest()
        jar = try? await NetworkClient.shared.getInterestJar().jar
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, color: IDS.Colors.textPrimary, relativeTo: .body).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Spacer()
                Button(action: onOpenCard) {
                    HStack(spacing: 4) {
                        Image(systemName: "creditcard").font(.system(size: 14))
                        Text("Card").font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline))
                    }
                    .foregroundColor(IDS.Colors.textSecondary)
                }
                Button(action: onOpenManage) {
                    HStack(spacing: 4) {
                        Image(systemName: "gearshape").font(.system(size: 14))
                        Text("Manage").font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline))
                    }
                    .foregroundColor(IDS.Colors.textSecondary)
                }
                .padding(.leading, 14)
            }
            .padding(.horizontal, 16)

            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    if let accountNumber {
                        // Real gap found live (2026-08-31, direct user correction: "it's
                        // not itunda account number it's itunda bank account number")
                        // -- matches real Toss's own "토스뱅크 1000-XXXX-XXXX" pattern.
                        Text("itunda Bank \(accountNumber.chunked(4).joined(separator: "-"))")
                            .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                    CountUpText(balanceText)
                        .font(IDS.scaledFont(size: 32, weight: .bold, relativeTo: .largeTitle))
                        .foregroundColor(IDS.Colors.textPrimary)
                    if let jar, jar.earnedThisMonth > 0 {
                        HStack {
                            Text("Interest \(formatAmount(Int(jar.earnedThisMonth))) RWF")
                                .font(IDS.scaledFont(size: 13, weight: .regular, relativeTo: .footnote))
                                .foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Button(action: { Task { await claim() } }) {
                                Text(claiming ? "…" : "Get interest")
                                    .font(IDS.scaledFont(size: 12, weight: .bold, relativeTo: .caption2))
                                    .foregroundColor(IDS.Colors.brand)
                            }
                            .disabled(claiming)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(IDS.Colors.backgroundTertiary)
                        .cornerRadius(10)
                        .padding(.top, 10)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)

                Spacer().frame(height: 24)

                if transactions.isEmpty {
                    Text("No transactions yet")
                        .font(IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline))
                        .foregroundColor(IDS.Colors.textTertiary)
                        .padding(.top, 24)
                } else {
                    VStack(spacing: 0) {
                        ForEach(transactions) { tx in
                            Button(action: { selectedTransaction = tx }) {
                                AccountLedgerDetailRow(tx: tx)
                            }
                            .buttonStyle(PressScaleButtonStyle())
                            Divider()
                        }
                    }
                    .padding(.horizontal, 24)
                }
            }

            Divider()
            Button(action: onSend) {
                Text("Send")
                    .font(IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .body))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(IDS.Colors.brand)
                    .cornerRadius(999)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
        .task {
            jar = try? await NetworkClient.shared.getInterestJar().jar
        }
        .fullScreenCover(item: $selectedTransaction) { tx in
            TransactionDetailScreen(tx: tx, onBack: { selectedTransaction = nil })
        }
    }
}

/// Real transaction-detail drill-in (2026-08-24, direct user follow-up: "no I mean
/// presable effect" -- clarifying that the ledger row's missing press feedback was
/// really pointing at a bigger gap, that tapping a row didn't go anywhere at all).
/// Real Toss Bank reference (직접 3rd screenshot from this same thread's very first
/// message: 상세내역 screen -- merchant name, amount, 적요/거래유형/일시/거래 후
/// 잔액 as a clean label:value list). Honestly scoped to only the fields
/// RecentTransactionRowData actually carries -- no invented "입금처/출금처"
/// account-name row (this display model has no sender/recipient display name to
/// show) and no "증명서 발급하기" certificate action (a real Korean bank-specific
/// feature itunda has no backend for). `tx.title` is shown here UNSTRIPPED (the
/// list row's own ledgerRowTitle only strips the redundant category prefix for
/// that row -- this screen is where the complete text lives). Matches Android's/
/// bank-mfe's identical TransactionDetailScreen.
private struct TransactionDetailScreen: View {
    let tx: RecentTransactionRowData
    let onBack: () -> Void

    private var rows: [(String, String)] {
        var r = [
            ("Description", tx.title),
            ("Type", transactionTypeLabel(tx.type)),
            ("Status", tx.subtitle),
            ("Date & time", ledgerFullDateTime(tx.createdAt)),
            ("Balance after", "\(tx.currency) \(formatAmount(Int(tx.afterBalance)))"),
        ]
        if tx.fee > 0 { r.append(("Fee", "\(tx.currency) \(formatAmount(Int(tx.fee)))")) }
        return r
    }

    var body: some View {
        let category = ledgerRowIcon(for: tx.title)
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
                VStack(alignment: .leading, spacing: 4) {
                    Circle()
                        .fill(category.color)
                        .frame(width: 56, height: 56)
                        .overlay(
                            Image(systemName: category.symbol)
                                .font(IDS.scaledFont(size: 22, weight: .regular, relativeTo: .title2))
                                .foregroundColor(.white)
                        )
                    Spacer().frame(height: 16)
                    Text(ledgerRowTitle(tx.title))
                        .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title3))
                        .foregroundColor(IDS.Colors.textPrimary)
                    Spacer().frame(height: 6)
                    Text(tx.amountText)
                        .font(IDS.scaledFont(size: 32, weight: .bold, relativeTo: .largeTitle))
                        .foregroundColor(tx.isOutgoing ? IDS.Colors.textPrimary : .blue)
                    Spacer().frame(height: 32)
                    VStack(spacing: 0) {
                        ForEach(rows, id: \.0) { label, value in
                            HStack(alignment: .top, spacing: 16) {
                                Text(label)
                                    .font(IDS.scaledFont(size: 14, weight: .regular, relativeTo: .callout))
                                    .foregroundColor(IDS.Colors.textTertiary)
                                Spacer()
                                Text(value)
                                    .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout))
                                    .foregroundColor(IDS.Colors.textPrimary)
                                    .multilineTextAlignment(.trailing)
                            }
                            .padding(.vertical, 12)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.top, 4)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .navigationBarHidden(true)
    }
}

private func transactionTypeLabel(_ type: String) -> String {
    switch type {
    case "TRANSFER": return "Transfer"
    case "PAYMENT": return "Payment"
    case "DEPOSIT": return "Deposit"
    case "WITHDRAWAL": return "Withdrawal"
    case "BILL": return "Bill payment"
    case "AIRTIME": return "Airtime"
    case "LOAN": return "Loan"
    case "INTEREST": return "Interest"
    default: return type.capitalized
    }
}

private func ledgerFullDateTime(_ iso: String) -> String {
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

private struct AccountLedgerDetailRow: View {
    let tx: RecentTransactionRowData

    var body: some View {
        let category = ledgerRowIcon(for: tx.title)
        HStack(spacing: 12) {
            Circle()
                .fill(category.color)
                .frame(width: 38, height: 38)
                .overlay(
                    Image(systemName: category.symbol)
                        .font(IDS.scaledFont(size: 16, weight: .regular, relativeTo: .callout))
                        .foregroundColor(.white)
                )
            VStack(alignment: .leading, spacing: 2) {
                // Matches Android's identical fix (2026-08-24, direct on-device
                // screenshot: a long free-text title ran straight into the amount
                // column with no gap) -- lineLimit(1) keeps a long transfer title from
                // pushing into or wrapping under the amountText that follows the
                // Spacer() below.
                Text(ledgerRowTitle(tx.title)).font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout)).foregroundColor(IDS.Colors.textPrimary).lineLimit(1)
                Text(tx.subtitle).font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .footnote)).foregroundColor(IDS.Colors.textTertiary)
            }
            Spacer()
            Text(tx.amountText)
                .font(IDS.scaledFont(size: 14, weight: .semibold, relativeTo: .callout))
                .foregroundColor(tx.isOutgoing ? IDS.Colors.textPrimary : .blue)
        }
        .padding(.vertical, 10)
    }
}

/// Real category classification (2026-08-21) -- see AccountLedgerDetailView's own
/// header comment. Description keyword first (real itunda-specific products the
/// title already names), falling back to a generic transfer symbol for anything
/// not specifically named -- this plain display model has no separate
/// TransactionType field to fall back on the way Android's/bank-mfe's identical
/// classifiers do (RecentTransactionRowData only carries title/subtitle/
/// amountText/isOutgoing), so unclassified rows get the same honest generic
/// transfer icon rather than a guessed-wrong specific one.
private func ledgerRowIcon(for title: String) -> (symbol: String, color: Color) {
    let d = title.lowercased()
    if d.contains("ride") { return ("car.fill", .blue) }
    if d.contains("eats") || d.contains("booking") || d.contains("dine-in") { return ("fork.knife", .orange) }
    if d.contains("gift") { return ("gift.fill", .purple) }
    if d.contains("escrow") || d.contains("marketplace") { return ("bag.fill", .teal) }
    if d.contains("cashback") { return ("percent", .orange) }
    if d.contains("interest") || d.contains("savings") { return ("building.columns.fill", .blue) }
    if d.contains("ussd") { return ("phone.fill", .purple) }
    if d.contains("bill") { return ("doc.text.fill", .orange) }
    if d.contains("airtime") { return ("simcard.fill", .purple) }
    if d.contains("loan") { return ("wallet.pass.fill", .blue) }
    return ("arrow.left.arrow.right", IDS.Colors.textSecondary)
}

/// Real fix (2026-08-24, direct user side-by-side of itunda's own ledger against a
/// real Toss Bank ledger + detail-screen screenshot: "since we are using real logos
/// and icons no need to mention Eat order, kigali grill house will be enough and
/// clear anyway when click on each transactions they get to see it's detail
/// screen"). Toss's own real ledger rows show the bare counterparty/merchant name
/// only -- the category is already carried by the row's icon.
///
/// Follow-up fix (same day, "they are still some transactions that don't follow
/// the same pattern"): the first pass only allowlisted eats/gift/escrow-marketplace,
/// but a full sweep of every Transaction.description call site across the backend
/// (Order/Card payment/Payment/QR payment/Salary payment/Booking deposit/
/// Subscription charge/Dine-in order/Split bill share/Transfer/Delayed transfer)
/// shows the exact same "{category} - {a real name}" shape almost everywhere; the
/// allowlist was just incomplete, not the right model. Flipped to strip-by-default
/// with an EXCLUDE list for the only two real exceptions found -- "Bill payment -
/// $billId" and "$provider Airtime - $phoneNumber" -- where the text after the
/// dash is a raw id/phone number, not a name, and stripping it would make the row
/// less clear. Matches Android's/bank-mfe's identical ledgerRowTitle fix.
private let ledgerTitleKeepPrefix = ["bill payment", "airtime"]

private func ledgerRowTitle(_ title: String) -> String {
    let lower = title.lowercased()
    guard !ledgerTitleKeepPrefix.contains(where: { lower.contains($0) }) else { return title }
    if let range = title.range(of: " -- ") {
        let tail = title[range.upperBound...].trimmingCharacters(in: .whitespaces)
        if !tail.isEmpty { return tail }
    }
    if let range = title.range(of: " - ") {
        let tail = title[range.upperBound...].trimmingCharacters(in: .whitespaces)
        if !tail.isEmpty { return tail }
    }
    return title
}
