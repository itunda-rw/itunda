import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


/// Real Kakao Friends-tab equivalent (item 237, sourced -- Kakao's Sept 2025 attempt
/// to bury this tab caused a rating collapse and was reverted within 3 months, per
/// docs/DESIGN_REFERENCES.md's Talk section recommendation #1). Talk only ever let a
/// user switch between chat-*history* views (Direct/Groups) -- no way to browse
/// contacts who are on itunda but you haven't messaged yet. The backend infra
/// (getTalkContacts/getPresence) was already fully real and already used inline in
/// the New-chat/add-member composers -- this is a client-only addition, no new
/// endpoint. bank-mfe/Android shipped this first (2026-08-06); this is the iOS port.
struct FriendsList: View {
    let onStarted: (String) -> Void

    @State private var contacts: [TalkContactDto]?
    @State private var presence: [String: Bool] = [:]
    @State private var error: String?
    @State private var startingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
            } else if contacts == nil {
                SkeletonBlock(height: 120)
            } else if contacts!.isEmpty {
                EmptyStateView("No friends yet -- save someone's contact and they'll show up here once they're on itunda.")
            } else {
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(contacts!) { contact in
                            Button(action: { Task { await startChat(contact) } }) {
                                HStack(spacing: 16) {
                                    ZStack(alignment: .bottomTrailing) {
                                        Circle().fill(IDS.Colors.brand.opacity(0.12)).frame(width: 44, height: 44)
                                            .overlay(Image(systemName: "person").foregroundColor(IDS.Colors.brand))
                                        if presence[contact.userId] == true {
                                            Circle().fill(IDS.Colors.success).frame(width: 12, height: 12)
                                                .overlay(Circle().stroke(IDS.Colors.card, lineWidth: 2))
                                        }
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(contact.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        if presence[contact.userId] == true {
                                            Text("Active now").font(.caption).bold().foregroundColor(IDS.Colors.success)
                                        }
                                    }
                                    Spacer()
                                    if startingId == contact.userId {
                                        Text("…").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                }
                                .padding(16)
                                .background(IDS.Colors.card)
                                .cornerRadius(IDS.Layout.cardCornerRadius)
                            }
                            .disabled(startingId == contact.userId)
                        }
                    }
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .padding(.top, 12)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        error = nil
        do {
            let list = try await NetworkClient.shared.getTalkContacts().contacts
            contacts = list
            if !list.isEmpty {
                presence = (try? await NetworkClient.shared.getPresence(userIds: list.map { $0.userId }).presence) ?? [:]
            }
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func startChat(_ contact: TalkContactDto) async {
        startingId = contact.userId
        error = nil
        defer { startingId = nil }
        do {
            let res = try await NetworkClient.shared.startConversation(otherUserId: contact.userId)
            onStarted(res.conversation.id)
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

struct GroupsList: View {
    let groups: [GroupSummaryDto]?
    let error: String?
    let onRetry: () -> Void
    let onCreated: (String) -> Void
    let onOpen: (GroupSummaryDto) -> Void

    @State private var name = ""
    @State private var phoneNumbers = ""
    @State private var createError: String?
    @State private var creating = false

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("New group").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("A group name and everyone's real phone number, comma-separated.")
                        .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                        .foregroundColor(IDS.Colors.textSecondary)
                    IdsTextField("Group name", text: $name)
                    IdsTextField("+250788123456, +250788987654", text: $phoneNumbers)
                    Button(action: { Task { await createGroup() } }) {
                        Text(creating ? "Creating…" : "Create group")
                            .font(IDS.Typography.bodyBold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background((creating || name.isEmpty || phoneNumbers.isEmpty) ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(14)
                    }
                    .disabled(creating || name.isEmpty || phoneNumbers.isEmpty)
                    if let createError {
                        Text(createError).font(.caption).foregroundColor(.red)
                    }
                }
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry", action: onRetry)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                } else if groups == nil {
                    SkeletonBlock(height: 120)
                } else if groups!.isEmpty {
                    EmptyStateView("No groups yet — start one to chat with more than one person at a time.")
                } else {
                    ForEach(groups!) { group in
                        Button(action: { onOpen(group) }) {
                            GroupRow(group: group)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
    }

    private func createGroup() async {
        creating = true
        createError = nil
        defer { creating = false }
        let numbers = phoneNumbers.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        do {
            let res = try await NetworkClient.shared.createGroup(name: name.trimmingCharacters(in: .whitespaces), memberPhoneNumbers: numbers)
            name = ""
            phoneNumbers = ""
            onCreated(res.group.groupId)
        } catch let NetworkError.httpError(statusCode) {
            createError = TalkScreen.errorMessage(statusCode)
        } catch {
            createError = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

struct GroupRow: View {
    let group: GroupSummaryDto

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(IDS.Colors.chipBackground)
                Image(systemName: "person.3.fill").foregroundColor(IDS.Colors.brand)
            }
            .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(group.name).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("\(group.memberCount) members").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                }
                Text(group.lastMessagePreview ?? "No messages yet")
                    .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                    .foregroundColor(IDS.Colors.textSecondary)
                    .lineLimit(1)
            }
            Spacer()
            if group.unreadCount > 0 {
                Text("\(group.unreadCount)")
                    .font(.caption).bold()
                    .foregroundColor(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(IDS.Colors.brand)
                    .clipShape(Capsule())
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

