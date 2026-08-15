import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Ported out of App/Sources/OverviewLoansCreditScoreScreens.swift (2026-07-23) into
// its real Tuist Feature module -- the first Feature<Name> module besides
// Payments/Banking to hold a real screen, see docs/MULTI_AGENT_ISOLATION.md. Fully
// self-contained: only needs CoreDesignSystem (IDS) and CoreNetwork
// (NetworkClient.shared.getCreditScore()), matching the same "no App-only
// dependency" bar Payments/Banking already met.
public struct CreditScoreScreenView: View {
    public var onBack: () -> Void

    public init(onBack: @escaping () -> Void = {}) {
        self.onBack = onBack
    }

    @State private var result: CreditScoreResponse?
    @State private var suggestions: [CreditScoreSuggestionDto] = []
    @State private var error: String?

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Credit score").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if let result {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Your score").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text("\(result.score) / 850").font(.title).bold()
                            Text("Based on your own account activity, not a bureau report.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
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

                        if !suggestions.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Ways to raise your score").bold()
                                ForEach(suggestions) { s in
                                    HStack {
                                        VStack(alignment: .leading) {
                                            Text(s.action).font(.subheadline)
                                            Text(s.description).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        }
                                        Spacer()
                                        Text("+\(s.pointsGain)").bold()
                                    }
                                }
                            }
                            .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    } else { ProgressView() }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            do { result = try await NetworkClient.shared.getCreditScore() }
            catch { self.error = "Could not load your credit score." }
            suggestions = (try? await NetworkClient.shared.getCreditScoreSuggestions().suggestions) ?? []
        }
    }
}
