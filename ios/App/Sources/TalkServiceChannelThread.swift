import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real itunda service channel (itunda Talk redesign, 2026-08-28) -- see backend
// ServiceChannelService's own doc comment. A read-only bubble list over real,
// already-existing Notification rows -- there is deliberately no persisted
// conversation for this, so it never calls a conversation-creation endpoint.
struct ServiceChannelRow: View {
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(IDS.Colors.brand)
                    Image(systemName: "bell.fill").font(.system(size: 18)).foregroundColor(.white)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text("itunda").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("Payments, alerts, and updates").font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1)).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
            }
        }
        .buttonStyle(.plain)
        .padding(.vertical, 18)
    }
}

struct TalkServiceChannelThread: View {
    let onBack: () -> Void
    let onNavigate: (String) -> Void

    @State private var bubbles: [ServiceChannelBubbleDto]?
    @State private var error: String?
    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix
    // -- see project_itunda_pagination_discard_sweep memory).
    @State private var bubblesPage = 0
    @State private var bubblesHasMore = false
    @State private var loadingMoreBubbles = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44) }
                    .accessibilityLabel("Back")
                Text("itunda").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    if let error {
                        Text(error).foregroundColor(.red).font(.subheadline).padding(.horizontal, IDS.Layout.screenHorizontal)
                    } else if bubbles == nil {
                        SkeletonBlock(height: 100).padding(.horizontal, IDS.Layout.screenHorizontal).padding(.top, 12)
                    } else if bubbles!.isEmpty {
                        EmptyStateView("No updates yet.").padding(.horizontal, IDS.Layout.screenHorizontal).padding(.top, 40)
                    } else {
                        ForEach(bubbles!) { bubble in
                            ServiceChannelBubbleView(bubble: bubble, onTap: {
                                if let route = bubble.ctaRoute { onNavigate(route) }
                            })
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                        }
                        if bubblesHasMore {
                            Button(loadingMoreBubbles ? "Loading…" : "Load more") {
                                Task { await loadMoreBubbles() }
                            }
                            .disabled(loadingMoreBubbles)
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                        }
                    }
                }
                .padding(.top, 12)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getServiceChannel(page: 0)
            bubbles = res.bubbles
            bubblesHasMore = res.page + 1 < res.totalPages
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadMoreBubbles() async {
        let nextPage = bubblesPage + 1
        loadingMoreBubbles = true
        defer { loadingMoreBubbles = false }
        guard let res = try? await NetworkClient.shared.getServiceChannel(page: nextPage) else { return }
        bubbles = (bubbles ?? []) + res.bubbles
        bubblesPage = nextPage
        bubblesHasMore = res.page + 1 < res.totalPages
    }
}

private struct ServiceChannelBubbleView: View {
    let bubble: ServiceChannelBubbleDto
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(bubble.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if !bubble.isRead {
                        Circle().fill(IDS.Colors.brand).frame(width: 6, height: 6)
                    }
                }
                Text(bubble.body)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textSecondary)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(IDS.Colors.chipBackground)
            .cornerRadius(14)
        }
        .buttonStyle(.plain)
        .disabled(bubble.ctaRoute == nil)
    }
}
