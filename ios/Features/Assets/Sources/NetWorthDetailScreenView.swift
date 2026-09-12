import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Toss "총자산" detail screen (2026-09-12, direct user-supplied total-assets
/// screenshots) -- tapping the net worth header on OverviewScreenView opens this: a
/// proportional breakdown bar + percentage list (account balances vs. reward
/// points, matching the backend's NetWorthSnapshot scope exactly -- see that
/// entity's own doc comment for why savings/investments/loans are deliberately
/// excluded), plus the "자산 변화" monthly trend chart backed by the new
/// GET /api/v1/overview/net-worth-history endpoint. Own file, not inline in
/// OverviewScreenView.swift, to avoid crossing the file-size-lint 500-line
/// guideline. Mirrors web's TotalAssetsDetailScreen.tsx/Android's
/// NetWorthDetailScreen.kt exactly.
private let detailStrings: [AppLocale: [String: String]] = [
    .en: [
        "currentTotal": "Current total assets", "accounts": "Accounts", "points": "Points",
        "empty": "You don't have any tracked assets yet.", "trendTitle": "Asset change",
        "trendEmpty": "Not enough history yet -- check back after a few days.",
        "disclosureA": "Loans, investments, and linked external accounts aren't included.",
        "disclosureB": "They're excluded because they'd affect how accurate the trend is.",
        "historyError": "Could not load your asset history.", "loading": "Loading…",
    ],
    .rw: [
        "currentTotal": "Umutungo wose ufite ubu", "accounts": "Konti", "points": "Amanota",
        "empty": "Nta mutungo ukurikiranwa ufite kugeza ubu.", "trendTitle": "Impinduka mu mutungo",
        "trendEmpty": "Nta makuru ahagije yabitswe -- garuka nyuma y'iminsi mike.",
        "disclosureA": "Inguzanyo, ishoramari, na konti z'amahanga zihujwe ntabwo bishyirwamo.",
        "disclosureB": "Ntibishyirwamo kuko byagira ingaruka ku kuri kw'impinduka zigaragazwa.",
        "historyError": "Ntibyakunze gushaka amateka y'umutungo wawe.", "loading": "Birimo gutunganywa…",
    ],
    .fr: [
        "currentTotal": "Total des actifs actuels", "accounts": "Comptes", "points": "Points",
        "empty": "Vous n'avez pas encore d'actifs suivis.", "trendTitle": "Évolution des actifs",
        "trendEmpty": "Pas encore assez d'historique -- revenez dans quelques jours.",
        "disclosureA": "Les prêts, investissements et comptes externes liés ne sont pas inclus.",
        "disclosureB": "Ils sont exclus car ils affecteraient la précision de la tendance.",
        "historyError": "Impossible de charger l'historique de vos actifs.", "loading": "Chargement…",
    ],
]

private struct BreakdownSegment: Identifiable {
    let id = UUID()
    let label: String
    let amount: Double
    let percent: Int
    let color: Color
}

struct NetWorthDetailScreenView: View {
    let overview: OverviewResponse
    let onBack: () -> Void

    @State private var locale: AppLocale = loadStoredLocale()
    @State private var history: [NetWorthHistoryPointDto]?
    @State private var error: String?

    private func t(_ key: String) -> String {
        detailStrings[locale]?[key] ?? detailStrings[.en]?[key] ?? key
    }

    private var accountsTotal: Double { overview.accounts.reduce(0) { $0 + $1.balance } }
    private var pointsTotal: Double { overview.points.rewardsTotal }
    private var trackedTotal: Double { accountsTotal + pointsTotal }

    private var segments: [BreakdownSegment] {
        guard trackedTotal > 0 else { return [] }
        var result: [BreakdownSegment] = []
        if accountsTotal > 0 {
            result.append(BreakdownSegment(label: t("accounts"), amount: accountsTotal, percent: Int((accountsTotal / trackedTotal * 100).rounded()), color: IDS.Colors.brand))
        }
        if pointsTotal > 0 {
            result.append(BreakdownSegment(label: t("points"), amount: pointsTotal, percent: Int((pointsTotal / trackedTotal * 100).rounded()), color: IDS.Colors.success))
        }
        return result
    }

    private func load() async {
        do {
            history = try await NetworkClient.shared.getNetWorthHistory().history
        } catch {
            self.error = t("historyError")
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    Text(t("currentTotal")).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    Text("\(formatAmount(Int(trackedTotal))) RWF").font(.title).bold()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)
                .padding(.bottom, 12)

                if segments.isEmpty {
                    Text(t("empty")).font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(.horizontal)
                } else {
                    breakdownBar
                }

                Divider().padding(.vertical, 16)

                VStack(alignment: .leading, spacing: 8) {
                    Text(t("trendTitle")).font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    } else if let history {
                        if history.isEmpty {
                            Text(t("trendEmpty")).font(.caption).foregroundColor(IDS.Colors.textSecondary).padding(.vertical, 24)
                        } else {
                            trendChart(history)
                        }
                    } else {
                        Text(t("loading")).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Text(t("disclosureA")).font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                    Text(t("disclosureB")).font(.caption2).foregroundColor(IDS.Colors.textTertiary)
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
        }
        .task { await load() }
    }

    private var breakdownBar: some View {
        VStack(alignment: .leading, spacing: 0) {
            GeometryReader { geo in
                HStack(spacing: 0) {
                    ForEach(segments) { s in
                        Rectangle().fill(s.color).frame(width: geo.size.width * CGFloat(s.percent) / 100)
                    }
                }
            }
            .frame(height: 10)
            .clipShape(RoundedRectangle(cornerRadius: 5))
            .padding(.horizontal)

            ForEach(segments) { s in
                HStack(spacing: 10) {
                    Circle().fill(s.color).frame(width: 10, height: 10)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.label).font(.subheadline).bold()
                        Text("\(s.percent)%").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Text("\(formatAmount(Int(s.amount))) RWF").font(.headline)
                }
                .padding(.horizontal)
                .padding(.vertical, 10)
            }
        }
    }

    private func trendChart(_ history: [NetWorthHistoryPointDto]) -> some View {
        let maxValue = max(history.map(\.liquidTotal).max() ?? 1, 1)
        return HStack(alignment: .bottom, spacing: 10) {
            ForEach(Array(history.enumerated()), id: \.offset) { index, point in
                let isLatest = index == history.count - 1
                VStack(spacing: 6) {
                    Text(formatAmount(Int(point.liquidTotal)))
                        .font(.system(size: 10))
                        .foregroundColor(isLatest ? IDS.Colors.brand : IDS.Colors.textSecondary)
                        .fontWeight(isLatest ? .bold : .regular)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(isLatest ? IDS.Colors.brand : IDS.Colors.divider)
                        .frame(height: max(CGFloat(point.liquidTotal / maxValue) * 100, 4))
                    Text(monthLabel(point.month))
                        .font(.caption)
                        .foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 160)
        .padding(.vertical, 16)
    }

    private func monthLabel(_ month: String) -> String {
        let parts = month.split(separator: "-")
        guard parts.count == 2, let year = Int(parts[0]), let monthNum = Int(parts[1]) else { return month }
        var components = DateComponents()
        components.year = year
        components.month = monthNum
        components.day = 1
        guard let date = Calendar.current.date(from: components) else { return month }
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMM")
        formatter.locale = Locale(identifier: locale.rawValue)
        return formatter.string(from: date)
    }
}
