import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real group-chat message search (Talk product-completeness pass, 2026-09-06) --
// extracted into its own file to keep TalkGroupThread.swift under the file-size-lint
// 500-line guideline; mirrors TalkChatThread.swift's own 1:1 search UI/logic exactly.
struct TalkGroupSearchBar: View {
    let groupId: String
    let onResultsChange: ([GroupMessageDto]?) -> Void
    let onError: (String) -> Void

    @State private var query = ""
    @State private var searching = false
    @State private var count: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                IdsTextField("Search this group", text: $query)
                    .onChange(of: query) { newValue in
                        if newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            count = nil
                            onResultsChange(nil)
                        }
                    }
                Button(searching ? "…" : "Search") { Task { await search() } }
                    .disabled(searching || query.trimmingCharacters(in: .whitespacesAndNewlines).count < 2)
            }
            if let count {
                Text("\(count) matching message\(count == 1 ? "" : "s")")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .padding(.horizontal, IDS.Layout.screenHorizontal)
    }

    private func search() async {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return }
        searching = true
        defer { searching = false }
        do {
            let results = try await NetworkClient.shared.searchGroupMessages(groupId: groupId, query: trimmed).messages
            count = results.count
            onResultsChange(results)
        } catch {
            onError("Couldn't search this group. Check your connection and try again.")
        }
    }
}
