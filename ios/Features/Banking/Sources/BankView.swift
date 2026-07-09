//
//  BankView.swift
//  Ported from mobile_clients/ios (2026-07-10) into its correct Tuist module --
//  see ARCHITECTURE.md §3.
//
//  NOT build-verified -- see Core/Risk/Sources/ZeroTrust.swift for why.
//

import SwiftUI
import CoreDesignSystem

public struct BankView: View {
    public init() {}

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: IDS.Layout.cardGap) {
                HomeTopBar()
                AccountSummaryCard()
                QuickActionsRow()
                HomeSectionCard(title: "Connected money", actionLabel: "Manage", rows: BankViewData.connectedMoney)
                HomeSectionCard(title: "For life in Rwanda", actionLabel: "More", rows: BankViewData.rwandaServices)
                HomeSectionCard(title: "Rewards and savings", actionLabel: "View", rows: BankViewData.rewards)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private enum BankViewData {
    static let connectedMoney = [
        HomeRowData(title: "BK Bank account", subtitle: "Salary and card settlement", trailing: "RWF 842,000", symbol: "building.columns", iconBackground: IDS.Colors.backgroundTertiary),
        HomeRowData(title: "MTN MoMo", subtitle: "Daily spending wallet", trailing: "RWF 118,400", symbol: "iphone", iconBackground: IDS.Colors.successTint),
        HomeRowData(title: "Airtel Money", subtitle: "Backup cash-out line", trailing: "Connected", symbol: "creditcard", iconBackground: IDS.Colors.warningTint)
    ]

    static let rwandaServices = [
        HomeRowData(title: "Pay CashPower", subtitle: "Top up electricity instantly", trailing: "Open", symbol: "doc.text", iconBackground: IDS.Colors.warningTint),
        HomeRowData(title: "Irembo services", subtitle: "Government and document payments", trailing: "Browse", symbol: "building.columns", iconBackground: IDS.Colors.pressed),
        HomeRowData(title: "My spending", subtitle: "View monthly categories and trends", trailing: "See all", symbol: "wallet.pass", iconBackground: IDS.Colors.backgroundTertiary)
    ]

    static let rewards = [
        HomeRowData(title: "Itunda rewards", subtitle: "Claim today's cashback and offers", trailing: "140 RWF", symbol: "sparkles", iconBackground: IDS.Colors.successTint),
        HomeRowData(title: "Goal saver", subtitle: "Rainy day fund progress", trailing: "62%", symbol: "leaf", iconBackground: IDS.Colors.pressed)
    ]
}

private struct HomeRowData: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let trailing: String
    let symbol: String
    let iconBackground: Color
}

private struct HomeTopBar: View {
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                Text("Good morning")
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
                Text("Itunda")
                    .font(IDS.Typography.header)
                    .foregroundColor(IDS.Colors.textPrimary)
            }

            Spacer()

            HStack(spacing: IDS.Layout.inlineGap) {
                TopBarActionButton(symbol: "bell")
                TopBarActionButton(symbol: "person")
            }
        }
    }
}

private struct TopBarActionButton: View {
    let symbol: String

    var body: some View {
        Button(action: {}) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(IDS.Colors.iconPrimary)
                .frame(width: IDS.Layout.topBarActionSize, height: IDS.Layout.topBarActionSize)
                .background(IDS.Colors.backgroundSecondary)
                .clipShape(Circle())
        }
    }
}

private struct AccountSummaryCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: IDS.Layout.cardGap) {
            VStack(alignment: .leading, spacing: IDS.Layout.tightGap) {
                Text("Itunda total balance")
                    .font(IDS.Typography.sectionLabel)
                    .foregroundColor(IDS.Colors.textSecondary)
                Text("RWF 1,284,350")
                    .font(IDS.Typography.largeAmount)
                    .foregroundColor(IDS.Colors.textPrimary)
                Text("Wallet, bank and mobile money in one place")
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
            }

            HStack(spacing: IDS.Layout.inlineGap) {
                BalanceTile(title: "Main wallet", amount: "RWF 324,000")
                BalanceTile(title: "Spend today", amount: "RWF 18,200")
            }

            HStack(spacing: IDS.Layout.tightGap) {
                Text("Send money now")
                    .font(IDS.Typography.bodyBold)
                    .foregroundColor(IDS.Colors.textBrand)
                Image(systemName: "chevron.right")
                    .foregroundColor(IDS.Colors.textBrand)
                    .font(.system(size: 13, weight: .semibold))
            }
            .padding(.horizontal, IDS.Layout.cardPadding)
            .padding(.vertical, IDS.Layout.inlineGap)
            .background(IDS.Colors.pressed)
            .clipShape(Capsule())
        }
        .padding(IDS.Layout.cardPadding)
        .background(IDS.Colors.raisedCard)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .shadow(color: IDS.Colors.shadow, radius: 10, x: 0, y: 4)
    }
}

private struct BalanceTile: View {
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

private struct QuickActionsRow: View {
    private let actions: [(String, String, Color)] = [
        ("Transfer", "arrow.up.right", IDS.Colors.successTint),
        ("Bills", "doc.text", IDS.Colors.warningTint),
        ("MoMo", "iphone", IDS.Colors.pressed),
        ("Savings", "leaf", IDS.Colors.dangerTint)
    ]

    var body: some View {
        HStack(spacing: IDS.Layout.inlineGap) {
            ForEach(actions, id: \.0) { action in
                VStack(spacing: IDS.Layout.tightGap) {
                    Image(systemName: action.1)
                        .font(.system(size: 20, weight: .medium))
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

private struct HomeSectionCard: View {
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
        .padding(IDS.Layout.cardPadding)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.sectionCornerRadius)
        .shadow(color: IDS.Colors.shadow.opacity(0.7), radius: 8, x: 0, y: 3)
    }
}

private struct CompactListRow: View {
    let row: HomeRowData

    var body: some View {
        HStack(spacing: IDS.Layout.inlineGap) {
            Image(systemName: row.symbol)
                .font(.system(size: 19, weight: .medium))
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
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(IDS.Colors.textTertiary)
            }
        }
        .padding(.vertical, IDS.Layout.tightGap)
    }
}

struct BankView_Previews: PreviewProvider {
    static var previews: some View {
        BankView()
    }
}
