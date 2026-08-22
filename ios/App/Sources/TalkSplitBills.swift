import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


// Real KakaoPay-style split bill (2026-07-22) -- found fully built on the backend
// (rw.itunda.splitbill) with zero client UI anywhere, despite group chat itself being
// fully wired. A flat, even split among picked group members (excluding the
// organizer); each participant pays their own share directly to the organizer via a
// real account-to-account push, no escrow -- see SplitBill.kt's own doc comment.
struct GroupSplitBillsView: View {
    let groupConversationId: String
    let members: [GroupMemberDto]
    let currentUserId: String?
    @Environment(\.dismiss) private var dismiss

    @State private var splitBills: [SplitBillWithParticipants]?
    @State private var error: String?
    @State private var busyId: String?
    @State private var showNewForm = false
    @State private var amountText = ""
    @State private var descriptionText = ""
    @State private var selectedIds: Set<String> = []
    // Real KakaoPay 사다리타기 (ladder-game) mode (item, ported from Android's
    // TalkScreen.kt) -- see backend SplitBillService.ladderSplit's own doc comment.
    @State private var ladderMode = false
    @State private var varianceLevel = 1
    @State private var receiptUrlDrafts: [String: String] = [:]

    private var otherMembers: [GroupMemberDto] { members.filter { $0.userId != currentUserId } }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if !showNewForm {
                        Button(action: { showNewForm = true }) {
                            Text("Split a bill").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            IdsTextField("Total amount (RWF)", text: $amountText, keyboardType: .numberPad)
                            IdsTextField("What was it for?", text: $descriptionText)
                            Text("Split with").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            ForEach(otherMembers) { member in
                                HStack {
                                    Text(member.name)
                                    Spacer()
                                    Image(systemName: selectedIds.contains(member.userId) ? "checkmark.square.fill" : "square")
                                }
                                .onTapGesture {
                                    if selectedIds.contains(member.userId) { selectedIds.remove(member.userId) } else { selectedIds.insert(member.userId) }
                                }
                            }
                            HStack {
                                HStack(spacing: 4) {
                                    SplitBillDice(size: 13)
                                    Text("Ladder game (randomized split)").font(.caption)
                                }
                                Spacer()
                                Text(ladderMode ? "On" : "Off").font(.caption).bold().foregroundColor(ladderMode ? IDS.Colors.brand : IDS.Colors.textSecondary)
                            }
                            .contentShape(Rectangle())
                            .onTapGesture { ladderMode.toggle() }
                            if ladderMode {
                                HStack(spacing: 8) {
                                    ForEach([1, 2, 3], id: \.self) { level in
                                        Button(action: { varianceLevel = level }) {
                                            Text("Level \(level)").font(.caption).bold()
                                                .foregroundColor(varianceLevel == level ? .white : IDS.Colors.textPrimary)
                                                .padding(.horizontal, 12).padding(.vertical, 8)
                                                .background(varianceLevel == level ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(8)
                                        }
                                    }
                                }
                            }
                            Button(action: { Task { await create() } }) {
                                Text(busyId == "new" ? "Creating…" : "Create").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12)
                                    .background(selectedIds.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busyId == "new" || selectedIds.isEmpty || amountText.isEmpty || descriptionText.isEmpty)
                        }
                        .padding(12).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                    if let splitBills {
                        if splitBills.isEmpty {
                            Text("No split bills in this group yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        ForEach(splitBills) { entry in
                            let myShare = entry.participants.first { $0.userId == currentUserId }
                            let isOrganizer = entry.splitBill.organizerId == currentUserId
                            let hasPending = entry.participants.contains { $0.status == "PENDING" }
                            let roundLabel = entry.splitBill.currentRound > 1 ? " · Round \(entry.splitBill.currentRound)" : ""
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.splitBill.description).bold()
                                HStack(spacing: 3) {
                                    Text("Total \(Int(entry.splitBill.totalAmount)) RWF · \(entry.splitBill.status)")
                                    if entry.splitBill.mode == "LADDER" {
                                        Text("·")
                                        SplitBillDice(size: 10)
                                        Text("Ladder L\(entry.splitBill.ladderVarianceLevel ?? 0)")
                                    }
                                    Text(roundLabel)
                                }
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                ForEach(entry.participants) { p in
                                    let name = members.first(where: { $0.userId == p.userId })?.name ?? String(p.userId.prefix(8))
                                    Text("\(name): \(Int(p.shareAmount)) RWF (\(p.status))").font(.caption)
                                }
                                if let url = entry.splitBill.receiptImageUrl {
                                    HStack(spacing: 4) {
                                        GiftThemeGlyph(theme: "SETTLE_UP", size: 11)
                                        Text("Receipt: \(url)").lineLimit(1)
                                    }
                                    .font(.caption2).foregroundColor(IDS.Colors.brand)
                                }
                                if let myShare, myShare.status == "PENDING" {
                                    Button(action: { Task { await pay(entry.splitBill.id) } }) {
                                        Text(busyId == entry.splitBill.id ? "Paying…" : "Pay my share (\(Int(myShare.shareAmount)) RWF)")
                                            .bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                                    }
                                    .disabled(busyId != nil)
                                }
                                if isOrganizer && entry.splitBill.receiptImageUrl == nil {
                                    HStack(spacing: 6) {
                                        IdsTextField("Receipt photo URL", text: Binding(
                                            get: { receiptUrlDrafts[entry.splitBill.id] ?? "" },
                                            set: { receiptUrlDrafts[entry.splitBill.id] = $0 }
                                        ))
                                        Button(action: { Task { await attachReceipt(entry.splitBill.id) } }) {
                                            Text("Attach").font(.caption).bold().padding(.horizontal, 12).padding(.vertical, 8)
                                                .background(IDS.Colors.chipBackground).cornerRadius(8)
                                        }
                                        .disabled(busyId != nil || (receiptUrlDrafts[entry.splitBill.id] ?? "").trimmingCharacters(in: .whitespaces).isEmpty)
                                    }
                                }
                                if isOrganizer && entry.splitBill.status == "OPEN" && hasPending && entry.splitBill.currentRound < 5 {
                                    Button(action: { Task { await nextRound(entry.splitBill.id) } }) {
                                        Text("Nudge unpaid → round \(entry.splitBill.currentRound + 1)").font(.caption).bold()
                                            .frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                    }
                                    .disabled(busyId != nil)
                                }
                            }
                            .padding(12).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
            .navigationTitle("Split bills")
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Close") { dismiss() } } }
            .task { await refresh() }
        }
    }

    private func refresh() async {
        do { splitBills = try await NetworkClient.shared.getSplitBillsForGroup(groupConversationId: groupConversationId).splitBills }
        catch { self.error = "Could not load split bills." }
    }

    private func create() async {
        guard let amount = Double(amountText) else { return }
        busyId = "new"; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.createSplitBill(
                groupConversationId: groupConversationId, totalAmount: amount, description: descriptionText, participantUserIds: Array(selectedIds),
                mode: ladderMode ? "LADDER" : "EVEN", ladderVarianceLevel: ladderMode ? varianceLevel : nil
            )
            amountText = ""; descriptionText = ""; selectedIds = []; showNewForm = false; ladderMode = false
            await refresh()
        } catch { self.error = "That split bill could not be created." }
    }

    private func pay(_ splitBillId: String) async {
        busyId = splitBillId; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.paySplitBillShare(splitBillId: splitBillId)
            await refresh()
        } catch { self.error = "That payment could not be completed." }
    }

    private func attachReceipt(_ splitBillId: String) async {
        guard let url = receiptUrlDrafts[splitBillId], !url.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        busyId = splitBillId; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.attachSplitBillReceipt(splitBillId: splitBillId, imageUrl: url)
            receiptUrlDrafts[splitBillId] = nil
            await refresh()
        } catch { self.error = "That receipt could not be attached." }
    }

    private func nextRound(_ splitBillId: String) async {
        busyId = splitBillId; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.requestSplitBillNextRound(splitBillId: splitBillId)
            await refresh()
        } catch { self.error = "Could not start the next settlement round." }
    }
}

// Real 1:1-chat split-bill view (2026-08-09) -- see backend
// SplitBillService.createDirectSplitBill's own doc comment. Same shape as
// GroupSplitBillsView above, minus the member-picker: a 1:1 split always has exactly
// one other participant, fixed by which conversation this was opened from.
struct DirectSplitBillsView: View {
    let otherUserId: String
    let otherUserName: String
    let currentUserId: String?
    @Environment(\.dismiss) private var dismiss

    @State private var splitBills: [SplitBillWithParticipants]?
    @State private var error: String?
    @State private var busyId: String?
    @State private var showNewForm = false
    @State private var amountText = ""
    @State private var descriptionText = ""
    @State private var ladderMode = false
    @State private var varianceLevel = 1
    @State private var receiptUrlDrafts: [String: String] = [:]

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if !showNewForm {
                        Button(action: { showNewForm = true }) {
                            Text("Split a bill").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            IdsTextField("Total amount (RWF)", text: $amountText, keyboardType: .numberPad)
                            IdsTextField("What was it for?", text: $descriptionText)
                            Text("Split with \(otherUserName)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            HStack {
                                HStack(spacing: 4) {
                                    SplitBillDice(size: 13)
                                    Text("Ladder game (randomized split)").font(.caption)
                                }
                                Spacer()
                                Text(ladderMode ? "On" : "Off").font(.caption).bold().foregroundColor(ladderMode ? IDS.Colors.brand : IDS.Colors.textSecondary)
                            }
                            .contentShape(Rectangle())
                            .onTapGesture { ladderMode.toggle() }
                            if ladderMode {
                                HStack(spacing: 8) {
                                    ForEach([1, 2, 3], id: \.self) { level in
                                        Button(action: { varianceLevel = level }) {
                                            Text("Level \(level)").font(.caption).bold()
                                                .foregroundColor(varianceLevel == level ? .white : IDS.Colors.textPrimary)
                                                .padding(.horizontal, 12).padding(.vertical, 8)
                                                .background(varianceLevel == level ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(8)
                                        }
                                    }
                                }
                            }
                            Button(action: { Task { await create() } }) {
                                Text(busyId == "new" ? "Creating…" : "Create").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12)
                                    .background((amountText.isEmpty || descriptionText.isEmpty) ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busyId == "new" || amountText.isEmpty || descriptionText.isEmpty)
                        }
                        .padding(12).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                    if let splitBills {
                        if splitBills.isEmpty {
                            Text("No split bills with \(otherUserName) yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        ForEach(splitBills) { entry in
                            let myShare = entry.participants.first { $0.userId == currentUserId }
                            let isOrganizer = entry.splitBill.organizerId == currentUserId
                            let hasPending = entry.participants.contains { $0.status == "PENDING" }
                            let roundLabel = entry.splitBill.currentRound > 1 ? " · Round \(entry.splitBill.currentRound)" : ""
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.splitBill.description).bold()
                                HStack(spacing: 3) {
                                    Text("Total \(Int(entry.splitBill.totalAmount)) RWF · \(entry.splitBill.status)")
                                    if entry.splitBill.mode == "LADDER" {
                                        Text("·")
                                        SplitBillDice(size: 10)
                                        Text("Ladder L\(entry.splitBill.ladderVarianceLevel ?? 0)")
                                    }
                                    Text(roundLabel)
                                }
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                ForEach(entry.participants) { p in
                                    let name = p.userId == otherUserId ? otherUserName : "You"
                                    Text("\(name): \(Int(p.shareAmount)) RWF (\(p.status))").font(.caption)
                                }
                                if let url = entry.splitBill.receiptImageUrl {
                                    HStack(spacing: 4) {
                                        GiftThemeGlyph(theme: "SETTLE_UP", size: 11)
                                        Text("Receipt: \(url)").lineLimit(1)
                                    }
                                    .font(.caption2).foregroundColor(IDS.Colors.brand)
                                }
                                if let myShare, myShare.status == "PENDING" {
                                    Button(action: { Task { await pay(entry.splitBill.id) } }) {
                                        Text(busyId == entry.splitBill.id ? "Paying…" : "Pay my share (\(Int(myShare.shareAmount)) RWF)")
                                            .bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                                    }
                                    .disabled(busyId != nil)
                                }
                                if isOrganizer && entry.splitBill.receiptImageUrl == nil {
                                    HStack(spacing: 6) {
                                        IdsTextField("Receipt photo URL", text: Binding(
                                            get: { receiptUrlDrafts[entry.splitBill.id] ?? "" },
                                            set: { receiptUrlDrafts[entry.splitBill.id] = $0 }
                                        ))
                                        Button(action: { Task { await attachReceipt(entry.splitBill.id) } }) {
                                            Text("Attach").font(.caption).bold().padding(.horizontal, 12).padding(.vertical, 8)
                                                .background(IDS.Colors.chipBackground).cornerRadius(8)
                                        }
                                        .disabled(busyId != nil || (receiptUrlDrafts[entry.splitBill.id] ?? "").trimmingCharacters(in: .whitespaces).isEmpty)
                                    }
                                }
                                if isOrganizer && entry.splitBill.status == "OPEN" && hasPending && entry.splitBill.currentRound < 5 {
                                    Button(action: { Task { await nextRound(entry.splitBill.id) } }) {
                                        Text("Nudge unpaid → round \(entry.splitBill.currentRound + 1)").font(.caption).bold()
                                            .frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                                    }
                                    .disabled(busyId != nil)
                                }
                            }
                            .padding(12).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
            .navigationTitle("Split bills")
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Close") { dismiss() } } }
            .task { await refresh() }
        }
    }

    private func refresh() async {
        do { splitBills = try await NetworkClient.shared.getDirectSplitBills(otherUserId: otherUserId).splitBills }
        catch { self.error = "Could not load split bills." }
    }

    private func create() async {
        guard let amount = Double(amountText) else { return }
        busyId = "new"; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.createDirectSplitBill(
                otherUserId: otherUserId, totalAmount: amount, description: descriptionText,
                mode: ladderMode ? "LADDER" : "EVEN", ladderVarianceLevel: ladderMode ? varianceLevel : nil
            )
            amountText = ""; descriptionText = ""; showNewForm = false; ladderMode = false
            await refresh()
        } catch { self.error = "That split bill could not be created." }
    }

    private func pay(_ splitBillId: String) async {
        busyId = splitBillId; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.paySplitBillShare(splitBillId: splitBillId)
            await refresh()
        } catch { self.error = "That payment could not be completed." }
    }

    private func attachReceipt(_ splitBillId: String) async {
        guard let url = receiptUrlDrafts[splitBillId], !url.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        busyId = splitBillId; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.attachSplitBillReceipt(splitBillId: splitBillId, imageUrl: url)
            receiptUrlDrafts[splitBillId] = nil
            await refresh()
        } catch { self.error = "That receipt could not be attached." }
    }

    private func nextRound(_ splitBillId: String) async {
        busyId = splitBillId; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.requestSplitBillNextRound(splitBillId: splitBillId)
            await refresh()
        } catch { self.error = "Could not start the next settlement round." }
    }
}

