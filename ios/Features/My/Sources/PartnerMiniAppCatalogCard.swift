import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real published third-party mini-app catalog (Partners product-completeness pass,
/// 2026-09-07) -- ports bank-mfe's own MyView.tsx mini-app section to iOS, the last
/// of the 3 platforms to get this. Browse-only, matching bank-mfe's own scope: the
/// real runtime bundle loader stays Android-only (PartnerMiniAppLoader.kt) -- see
/// PartnerService.kt's own doc comment for the honest boundary. Own file, matching
/// PinUpgradeCard.swift's own "own file, not inline" precedent for MyTabView.swift's
/// file-size discipline.
///
/// Real Toss/Kakao mini-app-store reference (2026-09-11, Mini-Apps hub pass) --
/// capped to a 3-item teaser + a real "See all" row opening MiniAppsHubScreenView,
/// matching bank-mfe's/Android's identical split. The per-row layout is now a
/// shared PartnerMiniAppRow (below) so the new hub screen doesn't duplicate it.
struct PartnerMiniAppCatalogCard: View {
    let miniApps: [PartnerMiniAppDto]
    var onSeeAll: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Mini apps").font(IDS.scaledFont(size: 15, weight: .bold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
            Text("Third-party apps reviewed and approved to run inside itunda.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            ForEach(miniApps.prefix(3)) { app in
                PartnerMiniAppRow(app: app)
            }
            Button(action: onSeeAll) {
                HStack {
                    Text("See all").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption).foregroundColor(IDS.Colors.textTertiary)
                }
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Shared row layout (2026-09-11) -- used by both the teaser above and
/// MiniAppsHubScreenView's own full list, so the icon/name/description markup
/// isn't duplicated.
struct PartnerMiniAppRow: View {
    let app: PartnerMiniAppDto

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            if let iconUrl = app.iconUrl, let url = URL(string: iconUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color(.tertiarySystemBackground)
                }
                .frame(width: 36, height: 36).clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                RoundedRectangle(cornerRadius: 8).fill(Color(.tertiarySystemBackground)).frame(width: 36, height: 36)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(app.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Text(app.description).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
        }
    }
}
