import SwiftUI
import CoreDesignSystem

// Real "My assets" tab-by-tab redesign shared pieces -- extracted from
// OverviewLoansCreditScoreScreens.swift (2026-08-28) to keep that file under its
// file-size-lint baseline, same convention CardExplainer.tsx/OverviewAssetsView.tsx
// already established on web for the identical constraint. Moved into
// FeatureAssets (2026-08-30) alongside OverviewScreenView, the only consumer --
// internal is enough now that both live in the same module target.

enum AssetTab: String, CaseIterable {
    case accounts, cards, loans, investment, insurance, realEstate, car, tax, points
}

struct AssetTabLabel: View {
    let title: String
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 6) {
                Text(title)
                    .font(selected ? .subheadline.bold() : .subheadline)
                    .foregroundColor(selected ? IDS.Colors.textPrimary : IDS.Colors.textSecondary)
                Rectangle()
                    .fill(IDS.Colors.brand)
                    .frame(width: selected ? 24 : 0, height: 2)
            }
        }
        .buttonStyle(.plain)
    }
}

// Real "My assets" real-card treatment -- see OverviewScreenView's own doc comment
// on why this screen deliberately reintroduces a Card look.
struct AssetSummaryCard: View {
    let title: String
    let value: String
    let ctaLabel: String
    let onCta: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.body).bold()
            Text(value).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            Button(action: onCta) { Text(ctaLabel).font(.caption).bold() }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(IDS.Colors.card)
        .cornerRadius(12)
        .idsCardBorder(cornerRadius: 12)
    }
}

// Real Toss up-sell-to-link masked teaser card -- native dashed border via
// StrokeStyle(dash:), no new shared component needed (unlike Android, Compose has
// no single built-in dashed-border parameter; see DashedBorder.kt's own comment).
struct AssetTeaserCard: View {
    let maskedValue: String
    let message: String
    let ctaLabel: String
    let onCta: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            Text(maskedValue).font(.title3).bold().foregroundColor(IDS.Colors.textTertiary)
            Text(message).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            Button(action: onCta) { Text(ctaLabel).font(.caption).bold() }
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(IDS.Colors.divider, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
        )
    }
}
