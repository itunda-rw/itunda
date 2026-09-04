import SwiftUI

struct ReportsTab: View {
    @State private var days: [ReportDayDto]?
    @State private var topProducts: [TopSellingProductDto]?
    @State private var error: String?
    @State private var rangeDays = 7

    var body: some View {
        VStack(spacing: 0) {
            Picker("Report range", selection: $rangeDays) {
                Text("Last 7 days").tag(7)
                Text("Last 30 days").tag(30)
            }
            .pickerStyle(.segmented)
            .padding([.horizontal, .top], 16)

            if let error {
                Text(error).foregroundColor(.red).padding(16)
            } else if let days {
                report(days)
            } else {
                Spacer(); ProgressView(); Spacer()
            }
        }
        .navigationTitle("Reports")
        .task(id: rangeDays) { await loadReport() }
    }

    @ViewBuilder
    private func report(_ days: [ReportDayDto]) -> some View {
        if days.isEmpty {
            Spacer()
            Text("No settled collections in this range.").foregroundColor(.secondary)
            Spacer()
        } else {
            let collections = days.reduce(0) { $0 + $1.collectionCount }
            let channels = channelSummary(days)
            ScrollView {
                LazyVStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("\(collections) collections").font(.headline)
                        Text(channels).font(.footnote).foregroundColor(.secondary)
                    }
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground)).cornerRadius(12)

                    ForEach(days) { day in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(day.date).bold()
                            HStack {
                                Text("\(day.collectionCount) sales").font(.footnote)
                                Spacer()
                                Text("\(formattedRWF(day.netAmount)) RWF net").font(.footnote).bold()
                            }
                            Text("Gross \(formattedRWF(day.grossAmount)) RWF · Fees \(formattedRWF(day.fees)) RWF")
                                .font(.caption).foregroundColor(.secondary)
                        }
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }

                    // Real Coupang WING-style best-selling-products report --
                    // merchant-mfe's own ReportsScreen.tsx has had this since
                    // 2026-08-16, ported here via the uncalled-endpoint sweep.
                    Text("Top-selling products").font(.headline).padding(.top, 8)
                    if let topProducts {
                        if topProducts.isEmpty {
                            Text("No products sold in this range.").foregroundColor(.secondary)
                                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(.secondarySystemBackground)).cornerRadius(12)
                        } else {
                            ForEach(topProducts) { product in
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(product.productName).bold()
                                        Text("\(product.unitsSold) sold").font(.footnote).foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Text("\(formattedRWF(product.revenue)) RWF").bold()
                                }
                                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(.secondarySystemBackground)).cornerRadius(12)
                            }
                        }
                    } else {
                        ProgressView().frame(maxWidth: .infinity).padding(16)
                    }
                }.padding(16)
            }
        }
    }

    private func loadReport() async {
        days = nil
        topProducts = nil
        error = nil
        let calendar = Calendar.current
        let to = Date()
        let from = calendar.date(byAdding: .day, value: -(rangeDays - 1), to: to) ?? to
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = calendar
        formatter.dateFormat = "yyyy-MM-dd"
        let fromStr = formatter.string(from: from)
        let toStr = formatter.string(from: to)
        do {
            days = try await MerchantNetworkClient.shared.getReport(from: fromStr, to: toStr).days
            topProducts = try await MerchantNetworkClient.shared.getTopSellingProducts(from: fromStr, to: toStr).products
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't load your reports right now."
        } catch {
            self.error = "Couldn't load your reports right now."
        }
    }

    private func channelSummary(_ days: [ReportDayDto]) -> String {
        let counts = days.flatMap(\.byChannel).reduce(into: [String: Int]()) { result, entry in result[entry.key, default: 0] += entry.value }
        return counts.sorted { $0.value > $1.value }.map { "\($0.key.replacingOccurrences(of: "_", with: " ")) \($0.value)" }.joined(separator: " · ")
    }
}
