import SwiftUI
import CoreDesignSystem

// Split out of MerchantHomeScreen.swift (2026-08-30, same file-size-lint "extract
// instead of growing a baselined file" discipline used repeatedly this session) --
// that file was already at its exact recorded baseline with zero headroom, needed to
// make real room for UpdatesTab/PhotosTab (see those files' own doc comments).
// Original Naver Smart Store-style "관심고객" broadcast-to-followers feature (item
// 118) unchanged.
struct FollowersTab: View {
    @State private var count: Int?
    @State private var title = ""
    @State private var body_ = ""
    @State private var sending = false
    @State private var sentCount: Int?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(count == nil ? "Loading…" : "\(count!) customer\(count == 1 ? "" : "s") following your store")
                    .font(.subheadline).foregroundColor(.secondary)
                IdsTextField("Title", text: $title)
                IdsTextField("Tell your followers what's new.", text: $body_)
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                let hasFollowers = (count ?? 0) > 0
                Button(action: { Task { await send() } }) {
                    Text(sending ? "Sending…" : "Broadcast to followers").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(hasFollowers ? Color.accentColor : Color.gray).cornerRadius(10)
                }
                .disabled(sending || !hasFollowers || title.trimmingCharacters(in: .whitespaces).isEmpty || body_.trimmingCharacters(in: .whitespaces).isEmpty)
                if !hasFollowers {
                    Text("You need at least one follower to send a broadcast.").font(.caption).foregroundColor(.secondary)
                }
                if let sentCount {
                    Text("Sent to \(sentCount) follower\(sentCount == 1 ? "" : "s").").font(.caption).foregroundColor(.accentColor)
                }
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        count = (try? await MerchantNetworkClient.shared.getFollowerCount())?.count ?? 0
    }

    private func send() async {
        sending = true
        error = nil
        sentCount = nil
        do {
            let res = try await MerchantNetworkClient.shared.broadcastToFollowers(title: title.trimmingCharacters(in: .whitespaces), body: body_.trimmingCharacters(in: .whitespaces))
            sentCount = res.recipientCount
            title = ""
            body_ = ""
        } catch {
            self.error = "Could not send this broadcast."
        }
        sending = false
    }
}
