import SwiftUI
import CoreDesignSystem

/// Real Coupang WING 상품분석 (product analytics) -- ported from merchant-mfe/Android
/// (2026-09-03), see ProductAnalyticsResponse's own doc comment.
struct ProductAnalyticsView: View {
    let productId: String
    let productName: String
    @Environment(\.dismiss) private var dismiss

    @State private var analytics: ProductAnalyticsResponse?
    @State private var error: String?

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                } else if let analytics {
                    HStack(spacing: 40) {
                        VStack {
                            Text("\(analytics.viewCount)").bold().font(.title2)
                            Text("Views").font(.caption).foregroundColor(.secondary)
                        }
                        VStack {
                            Text("\(analytics.orderCount)").bold().font(.title2)
                            Text("Orders").font(.caption).foregroundColor(.secondary)
                        }
                    }
                } else {
                    ProgressView()
                }
                Spacer()
            }
            .padding(16)
            .navigationTitle(productName)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            analytics = try await MerchantNetworkClient.shared.getProductAnalytics(productId)
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't load analytics."
        } catch {
            self.error = "Couldn't load analytics."
        }
    }
}
