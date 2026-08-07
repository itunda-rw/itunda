import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Karrot-Score-style numeric trust/reputation badge (item 152) -- found
/// 2026-07-31 fully built on the backend (rw.itunda.trustscore's TrustScoreController)
/// and real on bank-mfe (TrustScoreView) with zero native UI anywhere. Distinct from
/// the per-listing trustScores batch map already used for seller/poster/lister badges
/// on Hood cards -- this is the self-view of a user's own full factor breakdown.
/// Mirrors CreditScoreScreenView's layout exactly, same shape (score + factor list).
struct TrustScoreScreenView: View {
    var onBack: () -> Void = {}

    @State private var result: TrustScoreResponse?
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Trust score").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if let result {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Your trust score").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(result.score) / 1000").font(.title).bold()
                            Text("How your neighbors see you on Marketplace, Jobs, and Property.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)

                        VStack(alignment: .leading, spacing: 6) {
                            Text("What makes up your score").bold()
                            ForEach(result.factors) { f in
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(f.name).font(.subheadline)
                                        Text(f.description).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    Spacer()
                                    Text("+\(f.points)").bold()
                                }
                            }
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    } else { ProgressView() }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            do { result = try await NetworkClient.shared.getTrustScore() }
            catch { self.error = "Could not load your trust score." }
        }
    }
}
