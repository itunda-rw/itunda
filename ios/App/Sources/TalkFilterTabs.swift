import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real KakaoTalk-style chat-list filter tabs (전체/안읽음/통화) (itunda Talk redesign,
// 2026-08-28) -- see TalkScreen.swift's own doc comment for the tab-switcher context
// this sits inside. 전체 (All) / 안읽음 (Unread) are pure client-side filters over the
// conversation list's existing real unreadCount field -- no backend call. 통화 (Calls)
// shows real call history from GET /api/v1/calls/history (see
// NetworkClient+Calling.swift) -- listing only, no call-placing UI here; that's a
// separate, later pass.
enum TalkListFilter: CaseIterable {
    case all, unread, calls

    var label: String {
        switch self {
        case .all: return "전체"
        case .unread: return "안읽음"
        case .calls: return "통화"
        }
    }
}

struct TalkFilterTabsBar: View {
    @Binding var selection: TalkListFilter

    var body: some View {
        HStack(spacing: 8) {
            ForEach(TalkListFilter.allCases, id: \.self) { filter in
                Button(action: { selection = filter }) {
                    Text(filter.label)
                        .font(IDS.Typography.bodyBold)
                        .foregroundColor(selection == filter ? .white : IDS.Colors.textPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(selection == filter ? IDS.Colors.brand : IDS.Colors.chipBackground)
                        .cornerRadius(16)
                }
            }
            Spacer()
        }
        .padding(.horizontal, IDS.Layout.screenHorizontal)
        .padding(.top, 8)
    }
}

// Real call-log tab (KakaoTalk 통화 tab reference) -- read-only history from a real,
// persisted CallSession row per call, never derived/fabricated. endedAt == nil means
// the call is still in progress or was abandoned mid-signaling.
struct CallHistoryList: View {
    let calls: [CallSessionDto]?
    let error: String?
    let onRetry: () -> Void
    var hasMore = false
    var loadingMore = false
    var onLoadMore: () -> Void = {}

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry", action: onRetry)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                } else if calls == nil {
                    SkeletonBlock(height: 80).padding(.horizontal, IDS.Layout.screenHorizontal).padding(.top, 12)
                } else if calls!.isEmpty {
                    EmptyStateView("No calls yet.")
                        .padding(.horizontal, IDS.Layout.screenHorizontal)
                        .padding(.top, 40)
                } else {
                    ForEach(calls!) { call in
                        CallHistoryRow(call: call)
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                            .padding(.vertical, 12)
                        Divider().padding(.leading, IDS.Layout.screenHorizontal + 44)
                    }
                    if hasMore {
                        Button(loadingMore ? "Loading…" : "Load more", action: onLoadMore)
                            .disabled(loadingMore)
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                            .padding(.vertical, 12)
                    }
                }
            }
        }
    }
}

private struct CallHistoryRow: View {
    let call: CallSessionDto

    private var isVideo: Bool { call.callType == "VIDEO" }
    private var isMissed: Bool { call.endReason == "MISSED" || call.endReason == "DECLINED" }

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(IDS.Colors.chipBackground).frame(width: 40, height: 40)
                Image(systemName: isVideo ? "video.fill" : "phone.fill")
                    .font(.system(size: 15))
                    .foregroundColor(isMissed ? IDS.Colors.danger : IDS.Colors.brand)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(isVideo ? "Video call" : "Voice call")
                    .font(IDS.Typography.bodyBold)
                    .foregroundColor(isMissed ? IDS.Colors.danger : IDS.Colors.textPrimary)
                Text(call.endReason ?? (call.endedAt == nil ? "In progress" : "Completed"))
                    .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
        }
    }
}
