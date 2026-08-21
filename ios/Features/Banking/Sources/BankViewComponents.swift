//
//  BankViewComponents.swift
//  Extracted from BankView.swift (2026-08-22, file-size-lint backlog remediation --
//  see docs/ARCHITECTURE_GUIDELINES.md §2 "code that changes together lives
//  together") -- BankView.swift crossed its frozen file-size-lint baseline after
//  the 2026-08-22 flat-catalog rework grew it further. This is the presentational
//  half (top bar, balance summary, quick actions, flat section/row renderers) --
//  BankView itself, its real data-shaping (BankViewData), and its own
//  BankingLocale/bt() localization primitives stay in BankView.swift since those
//  are used across both files. Widened from `private` to internal (module-scoped)
//  since these types are now shared across two files in the same Tuist Feature
//  module, matching the same convention Section 262's MyPaymentCodeCard.swift
//  extraction already established.
//

import SwiftUI
import CoreDesignSystem

struct HomeRowData: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let trailing: String
    let symbol: String
    let iconBackground: Color
    // Added 2026-07-12 for the real Savings section's rows (deposit/claim) --
    // default nil preserves every existing purely-promotional row unchanged.
    var onTap: (() -> Void)? = nil
}

struct HomeTopBar: View {
    let locale: BankingLocale
    let onOpenNotifications: () -> Void
    let onOpenProfile: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                Text(bt("goodMorning", locale: locale))
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
                Text("Itunda")
                    .font(IDS.Typography.header)
                    .foregroundColor(IDS.Colors.textPrimary)
            }

            Spacer()

            HStack(spacing: IDS.Layout.inlineGap) {
                TopBarActionButton(symbol: "bell", accessibilityLabel: bt("notifications", locale: locale), action: onOpenNotifications)
                TopBarActionButton(symbol: "person", accessibilityLabel: bt("profile", locale: locale), action: onOpenProfile)
            }
        }
    }
}

// Real fix (product-feel audit, §235): both icons used to be a literal
// `Button(action: {})` no-op -- real, confirmed by checking Android's equivalent
// header (ItundaAppScreen.kt's TopBar), whose own doc comment records the identical
// bug found and fixed there 2026-07-22 ("Both icons were real no-op taps... despite
// their own real destinations already existing elsewhere in this file"). iOS never
// got that same fix. Android's bell opens a dedicated NotificationListScreen,
// separate from Settings' own notification-preferences row -- iOS has no equivalent
// dedicated feed screen (confirmed: the only real notification list on iOS lives
// inside SettingsScreen's own `viewModel.notifications` section), so both icons
// route to that one real screen rather than to nothing -- an honest destination
// today, not a fabricated dedicated feed iOS doesn't actually have. Icon-only
// buttons also need an explicit label -- SwiftUI doesn't derive one from the SF
// Symbol name, so without `accessibilityLabel` VoiceOver announced these as
// "Button" with no name (the same class of bug fixed in ItundaAppScreen.kt's
// TopIconButton on Android).
struct TopBarActionButton: View {
    let symbol: String
    let accessibilityLabel: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(IDS.scaledFont(size: 18, weight: .medium, relativeTo: .body))
                .foregroundColor(IDS.Colors.iconPrimary)
                .frame(width: IDS.Layout.topBarActionSize, height: IDS.Layout.topBarActionSize)
                .background(IDS.Colors.backgroundSecondary)
                .clipShape(Circle())
        }
        .accessibilityLabel(accessibilityLabel)
    }
}

struct AccountSummaryCard: View {
    let balanceText: String
    let accountNumber: String?
    let onSend: () -> Void
    // Real Toss Bank reference (20 screenshots, 2026-08-21) -- see this file's
    // own header doc comment on BankView: tapping the balance opens the real
    // ledger drill-in (AccountLedgerDetailView), matching the direct user
    // correction that moved the ledger off this home screen.
    let onOpenDetail: () -> Void
    let locale: BankingLocale

    var body: some View {
        VStack(alignment: .leading, spacing: IDS.Layout.cardGap) {
            Button(action: onOpenDetail) {
                VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                    Text(bt("totalBalance", locale: locale))
                        .font(IDS.Typography.sectionLabel)
                        .foregroundColor(IDS.Colors.textSecondary)
                    // Real Toss Bank reference (user-provided screenshots, 2026-08-11):
                    // the real account detail screen leads with the account's own real
                    // number ("토스뱅크 1000-3058-1980") directly above the balance --
                    // itunda's real, collision-checked AccountNumberGenerator has produced
                    // a real accountNumber for the MAIN account since it was built, but it
                    // was never actually shown anywhere except when entering someone
                    // ELSE's number to send to. Same fix on Android/web the same day.
                    if let accountNumber {
                        Text("itunda \(accountNumber.chunked(4).joined(separator: "-"))")
                            .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                            .foregroundColor(IDS.Colors.textTertiary)
                    }
                    Text(balanceText)
                        .font(IDS.Typography.largeAmount)
                        .foregroundColor(IDS.Colors.textPrimary)
                    Text(bt("balanceSubtitle", locale: locale))
                        .font(IDS.Typography.bodyMedium)
                        .foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            HStack(spacing: IDS.Layout.inlineGap) {
                BalanceTile(title: bt("mainAccount", locale: locale), amount: balanceText)
                BalanceTile(title: bt("spendToday", locale: locale), amount: "RWF 18,200")
            }

            Button(action: onSend) {
                HStack(spacing: IDS.Layout.tightGap) {
                    Text(bt("sendMoneyNow", locale: locale))
                        .font(IDS.Typography.bodyBold)
                        .foregroundColor(IDS.Colors.textBrand)
                    Image(systemName: "chevron.right")
                        .foregroundColor(IDS.Colors.textBrand)
                        .font(IDS.scaledFont(size: 13, weight: .semibold, relativeTo: .footnote))
                }
                .padding(.horizontal, IDS.Layout.cardPadding)
                .padding(.vertical, IDS.Layout.inlineGap)
                .background(IDS.Colors.pressed)
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(.bottom, IDS.Layout.cardPadding)
    }
}

struct BalanceTile: View {
    let title: String
    let amount: String

    var body: some View {
        VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
            Text(title)
                .font(IDS.Typography.caption)
                .foregroundColor(IDS.Colors.textTertiary)
            Text(amount)
                .font(IDS.Typography.metric)
                .foregroundColor(IDS.Colors.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(IDS.Layout.inlineGap)
        .background(IDS.Colors.backgroundPrimary)
        .cornerRadius(IDS.Layout.sectionCornerRadius)
    }
}

struct QuickActionsRow: View {
    let locale: BankingLocale

    private var actions: [(String, String, Color)] {
        [
            (bt("quickTransfer", locale: locale), "arrow.up.right", IDS.Colors.successTint),
            (bt("quickBills", locale: locale), "doc.text", IDS.Colors.warningTint),
            (bt("quickMoMo", locale: locale), "iphone", IDS.Colors.pressed),
            (bt("quickSavings", locale: locale), "leaf", IDS.Colors.dangerTint),
        ]
    }

    var body: some View {
        HStack(spacing: IDS.Layout.inlineGap) {
            ForEach(actions, id: \.1) { action in
                VStack(spacing: IDS.Layout.tightGap) {
                    Image(systemName: action.1)
                        .font(IDS.scaledFont(size: 20, weight: .medium, relativeTo: .body))
                        .foregroundColor(IDS.Colors.iconPrimary)
                        .frame(width: IDS.Layout.quickActionIconSize, height: IDS.Layout.quickActionIconSize)
                        .background(action.2)
                        .cornerRadius(IDS.Layout.iconCornerRadius)
                    Text(action.0)
                        .font(IDS.Typography.caption)
                        .foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, IDS.Layout.inlineGap)
                .background(IDS.Colors.backgroundSecondary)
                .cornerRadius(IDS.Layout.sectionCornerRadius)
            }
        }
    }
}

// Flattened 2026-08-22 (direct user directive: "our bank home screen should
// look 100% like toss bank screen") -- real Toss Bank's own product catalog
// renders as one continuous flat list, not a grid of separate white shadowed
// cards. Matches the identical flat-conversion of Android's ShellSection and
// bank-mfe's .itunda-flat-section shipped the same session: no background/
// cornerRadius/shadow, just a thin bottom divider between whole sections.
struct HomeSectionCard: View {
    let title: String
    let actionLabel: String
    let rows: [HomeRowData]

    var body: some View {
        VStack(alignment: .leading, spacing: IDS.Layout.rowGap) {
            HStack {
                Text(title)
                    .font(IDS.Typography.title)
                    .foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Text(actionLabel)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textTertiary)
            }

            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    CompactListRow(row: row)
                    if index < rows.count - 1 {
                        Divider()
                            .overlay(IDS.Colors.divider)
                    }
                }
            }
        }
        .padding(.bottom, IDS.Layout.cardPadding)
        .overlay(alignment: .bottom) {
            Divider().overlay(IDS.Colors.divider)
        }
    }
}

struct CompactListRow: View {
    let row: HomeRowData

    var body: some View {
        Group {
            if let onTap = row.onTap {
                Button(action: onTap) { rowContent }.buttonStyle(.plain)
            } else {
                rowContent
            }
        }
    }

    private var rowContent: some View {
        HStack(spacing: IDS.Layout.inlineGap) {
            Image(systemName: row.symbol)
                .font(IDS.scaledFont(size: 19, weight: .medium, relativeTo: .body))
                .foregroundColor(IDS.Colors.iconPrimary)
                .frame(width: IDS.Layout.rowIconSize, height: IDS.Layout.rowIconSize)
                .background(row.iconBackground)
                .cornerRadius(IDS.Layout.iconCornerRadius)

            VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                Text(row.title)
                    .font(IDS.Typography.bodyBold)
                    .foregroundColor(IDS.Colors.textPrimary)
                Text(row.subtitle)
                    .font(IDS.Typography.caption)
                    .foregroundColor(IDS.Colors.textTertiary)
            }

            Spacer()

            HStack(spacing: IDS.Layout.tightGap) {
                Text(row.trailing)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
                Image(systemName: "chevron.right")
                    .font(IDS.scaledFont(size: 12, weight: .semibold, relativeTo: .caption1))
                    .foregroundColor(IDS.Colors.textTertiary)
            }
        }
        .padding(.vertical, IDS.Layout.tightGap)
    }
}
