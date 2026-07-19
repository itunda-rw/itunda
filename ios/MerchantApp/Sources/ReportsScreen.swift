import SwiftUI

struct ReportsTab: View {
    @State private var days: [ReportDayDto]?
    @State private var error: String?

    var body: some View {
        Group {
            if let error {
                Text(error).foregroundColor(.red).padding(16)
            }
            if let days {
                if days.isEmpty {
                    Spacer()
                    Text("No sales yet.").foregroundColor(.secondary)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
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
                                .padding(16)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(12)
                            }
                        }
                        .padding(16)
                    }
                }
            } else {
                Spacer()
                ProgressView()
                Spacer()
            }
        }
        .task {
            do {
                days = try await MerchantNetworkClient.shared.getReport().days
            } catch {
                self.error = "Couldn't load your reports right now."
            }
        }
    }
}
