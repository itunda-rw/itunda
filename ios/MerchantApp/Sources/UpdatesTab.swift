import SwiftUI
import CoreDesignSystem

// Real gap found live (uncalled-endpoint sweep, 2026-08-29/30): MerchantUpdateController
// has been fully built on the backend since the itunda Maps redesign (2026-08-28), and
// merchant-mfe/Android merchantapp already have this real UI -- this is the native-
// iOS-merchant-app port, same real create+list feature. periodStart/periodEnd still
// deliberately omitted (both genuinely optional/independent per the backend entity's
// own doc comment).
private let updateLabels = ["NOTICE", "EVENT", "PROMO"]

struct UpdatesTab: View {
    let merchantId: String
    @State private var updates: [MerchantUpdateDto]?
    @State private var loadError: String?
    @State private var label = "NOTICE"
    @State private var title = ""
    @State private var body_ = ""
    @State private var posting = false
    @State private var postError: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Business updates").font(.headline)
                Text("Post a notice, event, or promo — customers see this on your Maps page.")
                    .font(.caption).foregroundColor(.secondary)
                if let postError { Text(postError).font(.caption).foregroundColor(.red) }

                HStack(spacing: 8) {
                    ForEach(updateLabels, id: \.self) { l in
                        Button(action: { label = l }) {
                            Text(l.capitalized)
                                .font(.caption).bold()
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(label == l ? Color.accentColor : Color(.systemGray5))
                                .foregroundColor(label == l ? .white : .primary)
                                .cornerRadius(8)
                        }
                    }
                }
                IdsTextField("Title", text: $title)
                IdsTextField("What's the update?", text: $body_)
                Button(action: { Task { await post() } }) {
                    Text(posting ? "Posting…" : "Post update").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Color.accentColor).cornerRadius(10)
                }
                .disabled(posting || title.trimmingCharacters(in: .whitespaces).isEmpty || body_.trimmingCharacters(in: .whitespaces).isEmpty)

                if let loadError {
                    Text(loadError).font(.caption).foregroundColor(.red)
                } else if let updates {
                    if updates.isEmpty {
                        Text("No updates yet — post one and it shows up on your Maps page right away.")
                            .font(.caption).foregroundColor(.secondary)
                    } else {
                        ForEach(updates) { update in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(update.label).font(.caption2).bold().foregroundColor(.accentColor)
                                Text(update.title).font(.subheadline).bold()
                                Text(update.body).font(.caption).foregroundColor(.secondary)
                                Text("\(update.likeCount) likes").font(.caption2).foregroundColor(.secondary)
                            }
                            .padding(.top, 4)
                        }
                    }
                } else {
                    ProgressView()
                }
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        do {
            updates = try await MerchantNetworkClient.shared.getMerchantUpdates(merchantId: merchantId).updates
            loadError = nil
        } catch {
            loadError = "Could not load your updates."
        }
    }

    private func post() async {
        posting = true
        postError = nil
        do {
            _ = try await MerchantNetworkClient.shared.postMerchantUpdate(label: label, title: title.trimmingCharacters(in: .whitespaces), body: body_.trimmingCharacters(in: .whitespaces))
            title = ""
            body_ = ""
            label = "NOTICE"
            await load()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            postError = message ?? "Could not post your update."
        } catch {
            postError = "Could not post your update."
        }
        posting = false
    }
}
