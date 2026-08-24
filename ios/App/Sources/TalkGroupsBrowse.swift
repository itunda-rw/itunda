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
                // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                // the whole screen's content at this point (docs/UI_UX_GUIDELINES.md §10).
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            } else if contacts == nil {
                SkeletonBlock(height: 120)
            } else if contacts!.isEmpty {
                EmptyStateView("No friends yet -- save someone's contact and they'll show up here once they're on itunda.")
            } else {
                ScrollView {
                    // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                    // a people list, matching GroupAccountScreen's/FamilyLinkScreen's
                    // identical entity-list conversion, no divider.
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
                                .padding(.vertical, 10)
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
                // Real fix (2026-08-24, flat-design sweep): dropped this Card and the
                // error-state Card below -- OpenChatCard (a real, distinct card widget,
                // confirmed KEEP) already provides visual separation on both sides
                // (docs/UI_UX_GUIDELINES.md §10).
                .padding(20)

                OpenChatCard(onCreated: onCreated, onJoined: onCreated)

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry", action: onRetry)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                } else if groups == nil {
                    SkeletonBlock(height: 120)
                } else if groups!.isEmpty {
                    EmptyStateView("No groups yet — start one to chat with more than one person at a time.")
                } else {
                    // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                    // a groups list, matching GroupAccountScreen's/FamilyLinkScreen's
                    // identical entity-list conversion, no divider.
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
        .padding(.vertical, 10)
    }
}

// Real KakaoTalk 오픈채팅-style open group (Talk-parity port, §243) -- see
// GroupMessagingService.createOpenGroup's own doc comment for the full sourced
// feature. bank-mfe already has this (2026-08-19, including a real fix there:
// joining used to require typing the raw 6-character code by hand -- exactly
// the "asking user code, instead use qr code" anti-pattern -- fixed with real
// camera QR scanning); this is the first iOS client. Reuses `QrScanCameraView`/
// `generateQrImage`/`parseQrParam` from `QrScanCamera.swift` -- the same real
// scanning/generation infrastructure §237/§239 built for Pay, not duplicated.
//
// Honest scope-down vs bank-mfe's own "share invite link" step: bank-mfe's
// `buildJoinUrl` builds a tap-to-join link back to the WEB app's own URL
// (`window.location`-based) -- itunda has no real universal-link/app-link
// association set up for iOS (same gap `MerchantDetailView`'s own affiliate-
// link share already named: "no deep-link precedent exists on this app"), so a
// shared link here would just open Safari, not the app. Shares the plain join
// CODE via the real native share sheet instead -- honest, not a fabricated
// working deep link.
private enum OpenChatMode { case closed, create, join }

struct OpenChatCard: View {
    let onCreated: (String) -> Void
    let onJoined: (String) -> Void

    @State private var mode: OpenChatMode = .closed
    @State private var name = ""
    @State private var joinCode = ""
    @State private var created: OpenGroupDto?
    @State private var qrImage: UIImage?
    @State private var error: String?
    @State private var submitting = false
    @State private var scanUnavailable = false
    @State private var manualJoinEntry = false

    var body: some View {
        Group {
            if mode == .closed {
                HStack(spacing: 10) {
                    Button(action: { mode = .create }) {
                        HStack(spacing: 6) {
                            GlobeGlyph(size: 15)
                            Text("Start an open chat")
                        }
                            .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(12)
                    }
                    Button(action: { mode = .join }) {
                        HStack(spacing: 6) {
                            CameraGlyph(size: 15)
                            Text("Join an open chat")
                        }
                            .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(12)
                    }
                }
            } else if let created {
                VStack(spacing: 10) {
                    Text("Send friends the code — they can join instantly, wherever they are")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary).multilineTextAlignment(.center)
                    Button(action: { shareCode(created.joinCode) }) {
                        HStack(spacing: 6) {
                            LinkGlyph(size: 15)
                            Text("Share code")
                        }
                            .font(.subheadline).bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(IDS.Colors.brand).cornerRadius(14)
                    }
                    Text("Or, if they're standing right next to you:").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let qrImage {
                        Image(uiImage: qrImage).interpolation(.none).resizable().frame(width: 140, height: 140).cornerRadius(12)
                    }
                    Text("Or read them this code:").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    Text(created.joinCode).font(IDS.scaledFont(size: 22, weight: .bold, relativeTo: .title2)).tracking(4).foregroundColor(IDS.Colors.textPrimary)
                    Button(action: { let id = created.id; self.created = nil; mode = .closed; onCreated(id) }) {
                        Text("Done").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(12)
                    }
                }
                .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    if mode == .create {
                        Text("Start an open chat").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("Anyone with the code can join — no phone numbers needed.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        IdsTextField("Open chat name", text: $name)
                        HStack(spacing: 10) {
                            Button(action: { mode = .closed }) {
                                Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(12)
                            }
                            Button(action: { Task { await createOpenChat() } }) {
                                Text(submitting ? "Creating…" : "Create").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(name.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                            }
                            .disabled(submitting || name.isEmpty)
                        }
                    } else if !manualJoinEntry {
                        Text("Scan to join").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        if !scanUnavailable && !submitting {
                            QrScanCameraView(onDetect: handleScanJoin, onUnavailable: { scanUnavailable = true })
                                .frame(height: 220).cornerRadius(12).clipped()
                        }
                        if submitting { Text("Joining…").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                        HStack(spacing: 10) {
                            Button(action: { mode = .closed }) {
                                Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(12)
                            }
                            Button(action: { manualJoinEntry = true }) {
                                Text(scanUnavailable ? "Enter code manually" : "No camera? Enter code")
                                    .bold().foregroundColor(IDS.Colors.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(12)
                            }
                        }
                    } else {
                        Text("Join by code").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        IdsTextField("6-character code", text: Binding(get: { joinCode }, set: { joinCode = $0.uppercased() }))
                        HStack(spacing: 10) {
                            Button(action: { mode = .closed }) {
                                Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(12)
                            }
                            Button(action: { Task { await submitJoinCode(joinCode) } }) {
                                Text(submitting ? "Joining…" : "Join").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(joinCode.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                            }
                            .disabled(submitting || joinCode.isEmpty)
                        }
                    }
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                }
                .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
            }
        }
    }

    private func handleScanJoin(_ raw: String) {
        Task { await submitJoinCode(parseQrParam(raw, key: "code")) }
    }

    private func submitJoinCode(_ raw: String) async {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        error = nil
        submitting = true
        defer { submitting = false }
        do {
            let result = try await NetworkClient.shared.joinGroupByCode(joinCode: trimmed)
            joinCode = ""
            mode = .closed
            onJoined(result.group.id)
        } catch {
            self.error = "No open chat found for this code."
        }
    }

    private func createOpenChat() async {
        error = nil
        submitting = true
        defer { submitting = false }
        do {
            let result = try await NetworkClient.shared.createOpenGroup(name: name.trimmingCharacters(in: .whitespaces))
            name = ""
            created = result.group
            // Real contract: unlike the Pay codes, this QR encodes a real
            // itunda://join-chat?code=... URL, not the raw code -- matches
            // bank-mfe's own identical QR payload exactly (parseQrParam
            // extracts the `code` param below).
            qrImage = generateQrImage(from: "itunda://join-chat?code=\(result.group.joinCode)", size: 280)
        } catch {
            self.error = "Could not create this open chat."
        }
    }

    private func shareCode(_ code: String) {
        let text = "Join my open chat on itunda — use code \(code) in the Talk tab."
        let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.windows.first?.rootViewController {
            var top = root
            while let presented = top.presentedViewController { top = presented }
            top.present(activityVC, animated: true)
        }
    }
}

