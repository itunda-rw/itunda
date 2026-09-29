import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


struct ChatThreadScreen: View {
    let conversation: ConversationSummaryDto
    let onBack: () -> Void

    @State private var messages: [MessageDto]?
    @State private var offersByMessageId: [String: OfferBubbleData] = [:]
    @State private var giftsByMessageId: [String: GiftDto] = [:]
    @State private var draft = ""
    @State private var replyingTo: MessageDto?
    @State private var pinnedMessage: MessageDto?
    @State private var updatingPin = false
    // Real Forward -- see ForwardPickerView's own doc comment.
    @State private var forwarding: MessageDto?
    // Real Thread support (2026-08-05) -- see RepliesThreadView's own doc comment.
    @State private var openThreadFor: MessageDto?
    @State private var sending = false
    @State private var error: String?
    @State private var socketTask: URLSessionWebSocketTask?
    @State private var otherOnline: Bool?
    @State private var otherTyping = false
    @State private var typingClearTask: Task<Void, Never>?
    @State private var lastTypingSentAt: Date = .distantPast
    @State private var giftComposerOpen = false
    @State private var giftAmount = ""
    @State private var giftNote = ""
    @State private var giftTheme: String? = nil
    @State private var sendingGift = false
    // Real KakaoTalk Emoticon Store (item 136) -- see EmoticonPickerPanel's own doc
    // comment.
    @State private var emoticonPickerOpen = false
    // Real itundaface emoji picker -- see ItundaFaceEmoji.swift's own doc comment.
    @State private var emojiPickerOpen = false
    @State private var emoticonStoreOpen = false
    @State private var emoticonImageById: [String: String] = [:]
    // Real attach ("+") menu + photo send/gallery -- see GroupThreadScreen's own
    // identical doc comment.
    @State private var showPhotoPicker = false
    @State private var uploadingPhoto = false
    @State private var showMediaGallery = false
    // Real 1:1-chat split-bill (2026-08-09) -- see DirectSplitBillsView's own doc
    // comment; mirrors GroupThreadScreen's own identical showSplitBills toggle.
    @State private var showSplitBills = false
    // Real KakaoTalk-style 기프티콘 gift voucher (item 138) -- see
    // GiftVoucherComposerPanel's own doc comment.
    @State private var vouchersByMessageId: [String: GiftVoucherDto] = [:]
    @State private var voucherComposerOpen = false
    @State private var showingBlockConfirmation = false
    @State private var blocking = false
    @State private var isBlocked = false
    @State private var quiet = false
    @State private var updatingQuiet = false
    @State private var searchQuery = ""
    @State private var searchResults: [MessageDto]?
    @State private var searching = false
    // Real device binding step-up (2026-07-21) -- Gift send/claim was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but a
    // bare `catch { }` swallowed it into a generic error, same fix already applied to
    // Transfer/Savings via TransferFlowContainer/SavingsFlowContainer.
    @State private var needsDeviceVerification = false
    @State private var pendingGiftRetry: (() async -> Void)?
    @State private var showRoomInfo = false // Real Links tab + room settings -- see TalkRoomInfo.swift.
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                VStack(alignment: .leading, spacing: 0) {
                    Text(conversation.otherUserName).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                    if let otherOnline {
                        Text(otherOnline ? "Online" : "Offline")
                            .font(.caption)
                            .foregroundColor(otherOnline ? .green : IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                Button(action: { showMediaGallery = true }) {
                    Image(systemName: "photo.on.rectangle").font(IDS.scaledFont(size: 18, weight: .regular, relativeTo: .title3)).frame(width: 40, height: 40)
                }
                .accessibilityLabel("Shared photos")
                Button(action: { showRoomInfo = true }) { Image(systemName: "link").font(IDS.scaledFont(size: 18, weight: .regular, relativeTo: .title3)).frame(width: 40, height: 40) }.accessibilityLabel("Room info")
                Button(action: { showSplitBills = true }) {
                    Image(systemName: "receipt").font(IDS.scaledFont(size: 18, weight: .regular, relativeTo: .title3)).frame(width: 40, height: 40)
                }
                .accessibilityLabel("Split a bill")
                Button(blocking ? "…" : (isBlocked ? "Unblock" : "Block")) {
                    if isBlocked {
                        Task { await unblockParticipant() }
                    } else {
                        showingBlockConfirmation = true
                    }
                }
                    .disabled(blocking)
                    .foregroundColor(.red)
                Button(updatingQuiet ? "…" : (quiet ? "Resume alerts" : "Quiet room")) { Task { await setQuietRoom() } }
                    .disabled(updatingQuiet)
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            .padding(.horizontal, 8)
            .alert("Block \(conversation.otherUserName)?", isPresented: $showingBlockConfirmation) {
                Button("Block", role: .destructive) { Task { await blockParticipant() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("They will no longer be able to message you. You can unblock them later from this conversation.")
            }

            HStack(spacing: 8) {
                IdsTextField("Search this conversation", text: $searchQuery)
                Button(searching ? "…" : "Search") { Task { await search() } }
                    .disabled(searching || searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).count < 2)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            if let searchResults {
                Text("\(searchResults.count) matching message\(searchResults.count == 1 ? "" : "s")")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if let pinnedMessage {
                HStack(spacing: 8) {
                    HStack(spacing: 4) {
                        PinGlyph(size: 12)
                        Text(pinnedMessage.body).lineLimit(1)
                    }
                    .font(.caption)
                    Spacer()
                    Button("Unpin") { Task { await unpinMessage() } }
                        .font(.caption).disabled(updatingPin)
                }
                .padding(8).background(IDS.Colors.chipBackground).cornerRadius(10)
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        if let messages = searchResults ?? messages {
                            if messages.isEmpty {
                                Text("Say hello — no messages yet.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                            }
                            ForEach(Array(messages.enumerated()), id: \.element.id) { index, message in
                                let reactionHandler: (String) -> Void = { emoji in Task { await toggleReaction(message.id, emoji) } }
                                let offerHandler: (String, String, Double?) -> Void = { offerId, action, counterAmount in Task { await respondToOffer(offerId, action, counterAmount) } }
                                let giftHandler: (String) -> Void = { giftId in Task { await claimGift(giftId) } }
                                let reportHandler: (String, String) -> Void = { messageId, reason in Task { await reportMessage(messageId, reason) } }
                                let extendVoucherHandler: (String) -> Void = { voucherId in Task { await extendVoucher(voucherId) } }
                                MessageBubble(
                                    message: message, isMine: message.senderId == currentUserId, currentUserId: currentUserId,
                                    offer: offersByMessageId[message.id],
                                    gift: giftsByMessageId[message.id],
                                    voucher: vouchersByMessageId[message.id],
                                    emoticonImageUrl: message.emoticonId.flatMap { emoticonImageById[$0] },
                                    onToggleReaction: reactionHandler,
                                    onRespondToOffer: offerHandler,
                                    onClaimGift: giftHandler,
                                    onExtendVoucher: extendVoucherHandler,
                                    onReply: { replyingTo = $0 },
                                    onOpenThread: { openThreadFor = $0 },
                                    onDelete: { messageId in Task { await deleteMessage(messageId) } },
                                    onPin: { pinned in Task { await pinMessage(pinned) } },
                                    onForward: { forwarding = $0 },
                                    onReportMessage: reportHandler,
                                    // Never collapsed when showing search hits -- adjacent
                                    // results aren't temporally adjacent in the real
                                    // conversation, so each needs its own timestamp.
                                    showTimestamp: searchResults != nil || shouldShowChatTimestamp(messages, index, senderId: { $0.senderId }, sentAt: { $0.sentAt }),
                                )
                                .id(message.id)
                            }
                        } else {
                            ProgressView().padding(.top, 20)
                        }
                    }
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .padding(.top, 12)
                }
                .onChange(of: messages?.count) { _ in
                    if let last = messages?.last?.id {
                        withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
            }

            if otherTyping {
                Text("\(conversation.otherUserName) is typing…")
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            DeviceStepUpHost(
                visible: needsDeviceVerification,
                onDismiss: { needsDeviceVerification = false; pendingGiftRetry = nil },
                onVerified: {
                    needsDeviceVerification = false
                    let retry = pendingGiftRetry
                    pendingGiftRetry = nil
                    await retry?()
                }
            )

            if let error {
                Text(error).font(.caption).foregroundColor(.red).padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if giftComposerOpen {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 6) {
                        GiftThemeGlyph(theme: giftTheme, size: 16)
                        Text("Send a gift").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    }
                    IdsTextField("Amount (RWF)", text: $giftAmount, keyboardType: .numberPad)
                    IdsTextField("Add a note (optional)", text: $giftNote)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach([(nil as String?, "No theme")] + giftThemeLabels.sorted(by: { $0.key < $1.key }).map { ($0.key as String?, $0.value) }, id: \.0) { value, label in
                                let selected = giftTheme == value
                                Button(action: { giftTheme = value }) {
                                    HStack(spacing: 4) {
                                        GiftThemeGlyph(theme: value, size: 14)
                                        Text(value == nil ? label : String(label.drop(while: { !$0.isWhitespace }).dropFirst()))
                                            .font(.caption).bold()
                                    }
                                    .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(selected ? IDS.Colors.brand : IDS.Colors.card)
                                    .cornerRadius(10)
                                    .idsCardBorder(cornerRadius: 10)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    HStack(spacing: 8) {
                        Button(action: { Task { await sendGift() } }) {
                            Text(sendingGift ? "Sending…" : "Send gift").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(IDS.Colors.card)
                                .cornerRadius(10).idsCardBorder(cornerRadius: 10)
                        }
                        .buttonStyle(.plain)
                        .disabled(sendingGift || Double(giftAmount) == nil || (Double(giftAmount) ?? 0) <= 0)
                        Button(action: { giftComposerOpen = false }) {
                            Text("Cancel").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(IDS.Colors.card)
                                .cornerRadius(10).idsCardBorder(cornerRadius: 10)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if emojiPickerOpen {
                ItundaFaceEmojiPicker(onPick: { emoji in draft += emoji })
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if emoticonPickerOpen {
                EmoticonPickerPanel(
                    onSend: { emoticonId in
                        Task {
                            _ = try? await NetworkClient.shared.sendEmoticon(conversationId: conversation.conversationId, emoticonId: emoticonId)
                            emoticonPickerOpen = false
                            await refresh()
                        }
                    },
                    onOpenStore: { emoticonStoreOpen = true }
                )
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if voucherComposerOpen {
                GiftVoucherComposerPanel(
                    onSent: { voucherComposerOpen = false; Task { await refresh() } },
                    onCancel: { voucherComposerOpen = false }
                )
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            HStack {
                // Real attach ("+") menu (2026-08-04 on Android, ported to iOS) --
                // consolidates what used to be 3 separate always-visible icons
                // (gift/emoticon/gift-voucher), plus real photo send, matching Kakao's
                // own real "+"-opens-a-menu pattern.
                Menu {
                    Button("📷 Photo") { showPhotoPicker = true }
                    Button("Emoji") { emojiPickerOpen.toggle() }
                    Button("😊 Emoticon") { emoticonPickerOpen.toggle() }
                    Button("🎁 Gift") { giftComposerOpen.toggle() }
                    Button("🎟️ Gift voucher") { voucherComposerOpen.toggle() }
                } label: {
                    Text(uploadingPhoto ? "…" : "+")
                        .font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title3))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .frame(width: 44, height: 44)
                        .background(IDS.Colors.chipBackground)
                        .clipShape(Circle())
                }
                .disabled(uploadingPhoto)
                .accessibilityLabel("Attach")
                IdsTextField("Message", text: Binding(
                    get: { draft },
                    set: { newValue in
                        draft = newValue
                        // Real typing indicator send (2026-07-19), client-throttled to
                        // match the server's own 1-per-2s rate limit.
                        if Date().timeIntervalSince(lastTypingSentAt) > 2 {
                            lastTypingSentAt = Date()
                            if let socketTask {
                                NetworkClient.shared.sendTyping(socketTask, conversationId: conversation.conversationId)
                            }
                        }
                    }
                ))
                Button(action: { Task { await send() } }) {
                    IDS.Icons.send(size: 20, color: .white)
                        .frame(width: 44, height: 44)
                        .background(draft.isEmpty || sending ? IDS.Colors.textTertiary : IDS.Colors.brand)
                        .clipShape(Circle())
                }.accessibilityLabel("Send")
                .disabled(draft.isEmpty || sending)
            }
            .padding(IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
        .task {
            quiet = (try? await NetworkClient.shared.getConversationQuiet(conversationId: conversation.conversationId).quiet) ?? false
            pinnedMessage = try? await NetworkClient.shared.getPinnedConversationMessage(conversationId: conversation.conversationId).message
        }
        // Real KakaoTalk Emoticon Store (item 136) -- no GET-emoticon-by-id endpoint
        // exists, so rendering a received emoticon needs a client-built id->imageUrl
        // map across the small, curated, server-seeded catalog. Mirrors bank-mfe/
        // Android's own image-map loaders (items 133/135).
        .task {
            guard let packs = try? await NetworkClient.shared.getEmoticonPacks().packs else { return }
            var map: [String: String] = [:]
            for pack in packs {
                if let emoticons = try? await NetworkClient.shared.getPackEmoticons(packId: pack.id).emoticons {
                    for e in emoticons { map[e.id] = e.imageUrl }
                }
            }
            emoticonImageById = map
        }
        // Real poll, kept as an always-correct fallback delivery path alongside the
        // real WebSocket push below -- matches bank-mfe/Android exactly (poll interval
        // unchanged, push appended live on top).
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                await refresh()
            }
        }
        // Real online/offline presence (2026-07-19) -- initial fetch, then kept live via
        // the same WebSocket connection's presence push below.
        .task {
            do {
                otherOnline = try await NetworkClient.shared.getPresence(userIds: [conversation.otherUserId]).presence[conversation.otherUserId]
            } catch {
                // Real, non-critical -- only backs the header subtitle.
            }
        }
        // Real WebSocket live-transport (2026-07-18) -- see
        // NetworkClient.connectMessagingSocket's own doc comment.
        .onAppear {
            socketTask = NetworkClient.shared.connectMessagingSocket { push in
                switch push {
                case .directMessage(let conversationId, let pushedMessage) where conversationId == conversation.conversationId:
                    Task { @MainActor in
                        otherTyping = false
                        if !(messages ?? []).contains(where: { $0.id == pushedMessage.id }) {
                            messages = (messages ?? []) + [pushedMessage]
                        }
                        // A pushed message might be a real offer/counter/accept/reject --
                        // refresh so it renders as an offer bubble immediately.
                        await loadOffers()
                        await loadGifts()
                        await loadVouchers()
                    }
                case .presenceChange(let userId, let online) where userId == conversation.otherUserId:
                    Task { @MainActor in otherOnline = online }
                case .typingChange(let conversationId, _, let userId) where conversationId == conversation.conversationId && userId == conversation.otherUserId:
                    Task { @MainActor in
                        otherTyping = true
                        typingClearTask?.cancel()
                        typingClearTask = Task {
                            try? await Task.sleep(nanoseconds: 3_000_000_000)
                            if !Task.isCancelled { otherTyping = false }
                        }
                    }
                case .reactionChange(let conversationId, _, let messageId, let reactions) where conversationId == conversation.conversationId:
                    Task { @MainActor in
                        messages = messages?.map { $0.id == messageId ? $0.withReactions(reactions) : $0 }
                    }
                default:
                    break
                }
            }
        }
        .onDisappear {
            socketTask?.cancel(with: .goingAway, reason: nil)
            typingClearTask?.cancel()
        }
        .sheet(isPresented: $emoticonStoreOpen) {
            EmoticonStoreView(onClose: { emoticonStoreOpen = false })
        }
        .sheet(isPresented: $showPhotoPicker) {
            ImagePickerView { image in
                showPhotoPicker = false
                if let image { Task { await sendPhoto(image) } }
            }
        }
        .sheet(isPresented: $showMediaGallery) {
            MediaGalleryView(imageUrls: (messages ?? []).compactMap { $0.imageUrl }.reversed())
        }
        .sheet(isPresented: $showRoomInfo) { TalkRoomInfoView(roomId: conversation.conversationId, texts: (messages ?? []).map { $0.body }) }
        .sheet(isPresented: $showSplitBills) {
            DirectSplitBillsView(otherUserId: conversation.otherUserId, otherUserName: conversation.otherUserName, currentUserId: currentUserId)
        }
        .sheet(item: $forwarding) { message in
            ForwardPickerView(
                onForward: { destinationType, destinationId in
                    (try? await NetworkClient.shared.forwardDirectMessage(messageId: message.id, destinationType: destinationType, destinationId: destinationId).success) ?? false
                },
                onDismiss: { forwarding = nil }
            )
        }
        // Real Thread support (2026-08-05) -- see RepliesThreadView's own doc comment.
        .sheet(item: $openThreadFor) { root in
            RepliesThreadView(
                rootMessage: root,
                currentUserId: currentUserId,
                fetchThreadMessages: { try await NetworkClient.shared.getThread(conversationId: conversation.conversationId, messageId: root.id).messages },
                onSend: { body in try await NetworkClient.shared.sendMessage(conversationId: conversation.conversationId, body: body, replyToMessageId: root.id) },
                onDismiss: { openThreadFor = nil; Task { await refresh() } }
            )
        }
    }

    private func sendPhoto(_ image: UIImage) async {
        guard let jpegData = image.jpegData(compressionQuality: 0.8) else {
            error = "Couldn't read that photo."
            return
        }
        uploadingPhoto = true
        defer { uploadingPhoto = false }
        do {
            if let message = try await sendTalkThreadPhoto(conversationId: conversation.conversationId, jpegData: jpegData, replyToMessageId: replyingTo?.id) {
                replyingTo = nil
                messages = (messages ?? []) + [message]
            }
        } catch {
            self.error = "Couldn't upload that photo. Check your connection and try again."
        }
    }

    private func refresh() async {
        do {
            let res = try await NetworkClient.shared.getMessages(conversationId: conversation.conversationId)
            messages = res.messages.reversed()
        } catch {
            // Keep showing the last-known messages rather than blanking the thread on
            // a transient poll failure.
        }
        await loadOffers()
        await loadGifts()
        await loadVouchers()
    }

    // Real per-thread gift history -- fetched alongside a conversation's messages so
    // the thread can render gift bubbles for whichever messages carry one.
    private func loadGifts() async {
        giftsByMessageId = await loadTalkThreadGifts(conversation.conversationId)
    }

    // Real per-thread gift-voucher history -- see GiftVoucherComposerPanel's own doc
    // comment.
    private func loadVouchers() async {
        vouchersByMessageId = await loadTalkThreadVouchers(conversation.conversationId)
    }

    private func sendGift() async {
        guard let amount = Double(giftAmount), amount > 0 else { return }
        sendingGift = true
        error = nil
        needsDeviceVerification = false
        defer { sendingGift = false }
        do {
            try await sendTalkThreadGift(conversationId: conversation.conversationId, amount: amount, note: giftNote, theme: giftTheme)
            giftAmount = ""
            giftNote = ""
            giftTheme = nil
            giftComposerOpen = false
            await refresh()
        } catch NetworkError.deviceNotVerified {
            pendingGiftRetry = { await sendGift() }
            needsDeviceVerification = true
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't send this gift. Check your connection and try again."
        } catch {
            self.error = "Couldn't send this gift. Check your connection and try again."
        }
    }

    private func claimGift(_ giftId: String) async {
        do {
            _ = try await NetworkClient.shared.claimGift(giftId: giftId)
            await loadGifts()
        } catch NetworkError.deviceNotVerified {
            pendingGiftRetry = { await claimGift(giftId) }
            needsDeviceVerification = true
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "GIFT_ALREADY_RESOLVED" {
            await loadGifts()
        } catch let NetworkError.httpErrorWithCode(_, _, message) {
            self.error = message ?? "Couldn't open this gift. Check your connection and try again."
        } catch {
            self.error = "Couldn't open this gift. Check your connection and try again."
        }
    }

    // Real one-time gift-voucher expiry extension (item 195) -- found via a
    // defined-but-uncalled-method sweep: extendGiftVoucherExpiry existed on all 3
    // platforms' network layers, wired on bank-mfe since 2026-07-27 and Android the
    // same session (item 194), never called here.
    private func extendVoucher(_ voucherId: String) async {
        do {
            _ = try await NetworkClient.shared.extendGiftVoucherExpiry(voucherId: voucherId)
            await loadVouchers()
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "GIFT_VOUCHER_ALREADY_EXTENDED" {
            await loadVouchers()
        } catch let NetworkError.httpErrorWithCode(_, _, message) {
            self.error = message ?? "Couldn't extend this voucher. Try again."
        } catch {
            self.error = "Couldn't extend this voucher. Try again."
        }
    }

    // Real-fetches both Marketplace and Real Estate offer history for this conversation
    // -- a given real conversation only ever carries one type in practice, but fetching
    // both is cheap and correct rather than guessing which one applies (mirrors
    // bank-mfe's own ConversationThread.loadOffers).
    private func loadOffers() async {
        offersByMessageId = await loadTalkThreadOffers(conversation.conversationId)
    }

    private func respondToOffer(_ offerId: String, _ action: String, _ counterAmount: Double?) async {
        do {
            try await respondToTalkThreadOffer(offerId: offerId, action: action, counterAmount: counterAmount)
            await refresh()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't respond to this offer. Check your connection and try again."
        } catch {
            self.error = "Couldn't respond to this offer. Check your connection and try again."
        }
    }

    private func toggleReaction(_ messageId: String, _ emoji: String) async {
        do {
            let res = try await NetworkClient.shared.toggleReaction(messageId: messageId, emoji: emoji)
            messages = messages?.map { $0.id == messageId ? $0.withReactions(res.reactions) : $0 }
        } catch {
            // Best-effort -- a failed reaction toggle just leaves the badge as it was.
        }
    }

    private func pinMessage(_ message: MessageDto) async {
        updatingPin = true
        defer { updatingPin = false }
        do {
            _ = try await NetworkClient.shared.pinConversationMessage(conversationId: conversation.conversationId, messageId: message.id)
            pinnedMessage = message
        } catch { self.error = "Couldn't pin this message. Check your connection and try again." }
    }

    private func deleteMessage(_ messageId: String) async {
        do { _ = try await NetworkClient.shared.deleteMessage(conversationId: conversation.conversationId, messageId: messageId); await refresh() }
        catch { self.error = "Couldn't delete this message. Check your connection and try again." }
    }

    private func unpinMessage() async {
        updatingPin = true
        defer { updatingPin = false }
        do {
            _ = try await NetworkClient.shared.unpinConversationMessage(conversationId: conversation.conversationId)
            pinnedMessage = nil
        } catch { self.error = "Couldn't unpin this message. Check your connection and try again." }
    }

    private func blockParticipant() async {
        blocking = true
        defer { blocking = false }
        do {
            _ = try await NetworkClient.shared.blockConversationParticipant(conversationId: conversation.conversationId)
            isBlocked = true
            error = "\(conversation.otherUserName) is blocked."
        } catch {
            self.error = "Couldn't block this person. Check your connection and try again."
        }
    }

    // Real unblock (item 197) -- found via a defined-but-uncalled-method sweep on
    // NetworkClient.swift: unblockConversationParticipant existed with zero call sites,
    // matching bank-mfe's own gap before item 193 closed it there. blockParticipant's
    // own confirmation copy already promises "You can unblock them later from this
    // conversation" (see the Block confirmation alert below).
    private func unblockParticipant() async {
        blocking = true
        defer { blocking = false }
        do {
            _ = try await NetworkClient.shared.unblockConversationParticipant(conversationId: conversation.conversationId)
            isBlocked = false
            error = "You unblocked \(conversation.otherUserName)."
        } catch {
            self.error = "Couldn't unblock this person. Check your connection and try again."
        }
    }

    private func setQuietRoom() async {
        updatingQuiet = true
        defer { updatingQuiet = false }
        do {
            quiet = try await NetworkClient.shared.setConversationQuiet(conversationId: conversation.conversationId, quiet: !quiet).quiet
        } catch {
            self.error = "Couldn't update this quiet room. Check your connection and try again."
        }
    }

    private func reportMessage(_ messageId: String, _ reason: String) async {
        do {
            _ = try await NetworkClient.shared.reportChatMessage(messageId: messageId, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this message."
        } catch {
            self.error = "Couldn't send this report. Check your connection and try again."
        }
    }

    private func search() async {
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard query.count >= 2 else { return }
        searching = true
        defer { searching = false }
        do { searchResults = try await NetworkClient.shared.searchMessages(conversationId: conversation.conversationId, query: query).messages }
        catch { self.error = "Couldn't search this conversation. Check your connection and try again." }
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            let res = try await NetworkClient.shared.sendMessage(conversationId: conversation.conversationId, body: body, replyToMessageId: replyingTo?.id)
            draft = ""
            replyingTo = nil
            messages = (messages ?? []) + [res.message]
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

