import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See the
// backend's Ikimina.kt doc comment for the full sourced account (real ROSCA
// literature, and a real existing Rwandan startup, smartikimina.rw, already
// digitizing this exact mechanic via mobile money). Distinct from
// GroupAccountScreenView.swift (Kakao Bank 모임통장): that feature has one permanent
// owner with sole withdrawal authority; an ikimina rotates the full pot to a different
// member each real round, until everyone has been paid exactly once. Genuinely the
// first feature in this codebase not sourced from Toss/Kakao/Naver/Coupang. Mirrors
// bank-mfe's IkiminaSection/IkiminaDetailView/CreateIkiminaForm exactly, same
// no-ViewModel, "call NetworkClient.shared directly from Task {} blocks" convention
// GroupAccountScreenView.swift already established.

struct IkiminaScreenView: View {
    var onBack: () -> Void = {}
    @State private var selectedId: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { selectedId != nil ? (selectedId = nil) : onBack() }) {
                    Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary)
                }
                Spacer()
                Text("Ikimina").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            if let id = selectedId {
                IkiminaDetailContent(id: id)
            } else {
                ScrollView {
                    IkiminaListContent(onOpen: { selectedId = $0 })
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct IkiminaListContent: View {
    let onOpen: (String) -> Void

    @State private var ikiminas: [IkiminaDto] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var showCreate = false
    @State private var name = ""
    @State private var contributionAmount = ""
    @State private var cycleFrequencyDays = 30
    @State private var memberCap = "10"
    @State private var creating = false

    var body: some View {
        VStack(spacing: 10) {
            Text("Everyone contributes the same amount each round; one member takes home the full pot, in turn.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)

            if showCreate {
                VStack(spacing: 8) {
                    IdsTextField("Group name (e.g. Umuryango)", text: $name)
                    IdsTextField("Contribution per round (RWF)", text: $contributionAmount, keyboardType: .numberPad)
                    Picker("Frequency", selection: $cycleFrequencyDays) {
                        Text("Weekly").tag(7)
                        Text("Monthly").tag(30)
                    }
                    .pickerStyle(.segmented)
                    IdsTextField("Max members", text: $memberCap, keyboardType: .numberPad)
                    HStack(spacing: 8) {
                        Button("Cancel") { showCreate = false }
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.secondarySystemBackground)).cornerRadius(10)
                        Button(action: { Task { await create() } }) {
                            Text(creating ? "Creating…" : "Create").foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(creating)
                    }
                }
            } else {
                Button(action: { showCreate = true }) {
                    Text("+ New ikimina").bold()
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(Color(.secondarySystemBackground)).cornerRadius(10)
                }
            }

            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }

            if !loaded {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            } else if ikiminas.isEmpty {
                EmptyStateView("No ikimina groups yet -- start one with people you trust.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary).padding()
            } else {
                ForEach(ikiminas, id: \.id) { k in
                    Button(action: { onOpen(k.id) }) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(k.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text(statusLabel(for: k)).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color(.secondarySystemBackground)).cornerRadius(10)
                    }
                }
            }
        }
        .padding(.horizontal)
        .task { await load() }
    }

    private func statusLabel(for k: IkiminaDto) -> String {
        switch k.status {
        case "FORMING": return "Forming — invite members before starting"
        case "ACTIVE": return "Round \(k.currentRound)"
        default: return "Completed"
        }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyIkiminas()
            if res.success { ikiminas = res.ikiminas }
            error = nil
        } catch {
            self.error = "Could not load your ikimina groups."
        }
        loaded = true
    }

    private func create() async {
        guard !name.isEmpty, let amount = Double(contributionAmount), amount > 0, let cap = Int(memberCap), cap >= 2 else { return }
        creating = true
        do {
            _ = try await NetworkClient.shared.createIkimina(name: name, contributionAmount: amount, cycleFrequencyDays: cycleFrequencyDays, memberCap: cap)
            name = ""; contributionAmount = ""; memberCap = "10"
            showCreate = false
            await load()
        } catch {
            self.error = "Could not create this ikimina."
        }
        creating = false
    }
}

private struct IkiminaDetailContent: View {
    let id: String

    @State private var detail: IkiminaDetailResponse?
    @State private var error: String?
    @State private var phoneNumber = ""
    @State private var busy = false
    @State private var payoutMessage: String?

    private var myUserId: String? { KeychainTokenStore.shared.getUserId() }

    private var isOrganizer: Bool {
        guard let detail, let myUserId else { return false }
        return detail.ikimina.organizerId == myUserId
    }
    private var iContributed: Bool {
        detail?.currentRoundContributions.first(where: { $0.userId == myUserId })?.contributed ?? false
    }
    private var allContributed: Bool {
        guard let detail, !detail.currentRoundContributions.isEmpty else { return false }
        return detail.currentRoundContributions.allSatisfy { $0.contributed }
    }
    private var pot: Double {
        guard let detail else { return 0 }
        return detail.ikimina.contributionAmount * Double(detail.members.count)
    }

    var body: some View {
        ScrollView {
            if let current = detail {
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(current.ikimina.name).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Text("\(formatMoney(current.balance)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text(statusSummary(current)).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Members & payout order").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        ForEach(current.members.sorted(by: { $0.payoutOrder < $1.payoutOrder }), id: \.userId) { m in
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(m.payoutOrder). \(m.firstName) \(m.lastName)\(m.userId == myUserId ? " (you)" : "")")
                                        .font(.footnote)
                                    if current.ikimina.status == "ACTIVE" {
                                        let contributed = current.currentRoundContributions.first(where: { $0.userId == m.userId })?.contributed ?? false
                                        Text(contributed ? "✓ Contributed this round" : "Not yet contributed")
                                            .font(.caption2).foregroundColor(contributed ? .green : IDS.Colors.textSecondary)
                                    }
                                }
                                Spacer()
                                if m.hasReceivedPayout {
                                    Text("✓ Paid").font(.caption).bold().foregroundColor(.green)
                                } else if m.isOrganizer {
                                    Text("Organizer").font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }

                    if current.ikimina.status == "FORMING" && isOrganizer {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Invite a member").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            HStack(spacing: 8) {
                                IdsTextField("Phone number", text: $phoneNumber, keyboardType: .phonePad)
                                Button(action: { Task { await invite() } }) {
                                    Text(busy ? "…" : "Invite").bold().foregroundColor(.white)
                                        .padding(.horizontal, 16).padding(.vertical, 12)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy || phoneNumber.isEmpty)
                            }
                            Button(action: { Task { await start() } }) {
                                Text(busy ? "…" : "Start the cycle").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || current.members.count < 2)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }

                    if current.ikimina.status == "ACTIVE" {
                        VStack(spacing: 8) {
                            Button(action: { Task { await contribute() } }) {
                                Text(iContributed ? "✓ You've contributed this round" : (busy ? "…" : "Contribute \(formatMoney(current.ikimina.contributionAmount)) RWF"))
                                    .bold().foregroundColor(iContributed ? IDS.Colors.textPrimary : .white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(iContributed ? Color(.tertiarySystemBackground) : IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy || iContributed)

                            if allContributed {
                                Button(action: { Task { await triggerPayout() } }) {
                                    Text(busy ? "…" : "Trigger this round's payout").bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy)
                            }
                            if let payoutMessage {
                                Text(payoutMessage).font(.footnote).foregroundColor(.green)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
                .padding(.horizontal)
            } else {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            }
        }
        .task { await load() }
    }

    private func statusSummary(_ current: IkiminaDetailResponse) -> String {
        switch current.ikimina.status {
        case "FORMING": return "Forming · \(current.members.count) member\(current.members.count == 1 ? "" : "s")"
        case "ACTIVE": return "Round \(current.ikimina.currentRound) · pot \(formatMoney(pot)) RWF"
        default: return "Completed"
        }
    }

    private func load() async {
        do {
            detail = try await NetworkClient.shared.getIkimina(id: id)
            error = nil
        } catch {
            self.error = "Could not load this ikimina."
        }
    }

    private func invite() async {
        guard !phoneNumber.isEmpty else { return }
        busy = true
        do {
            _ = try await NetworkClient.shared.inviteIkiminaMember(id: id, phoneNumber: phoneNumber)
            phoneNumber = ""
            error = nil
            await load()
        } catch {
            self.error = "Could not invite this member."
        }
        busy = false
    }

    private func start() async {
        busy = true
        do {
            _ = try await NetworkClient.shared.startIkiminaCycle(id: id)
            error = nil
            await load()
        } catch {
            self.error = "Could not start this cycle."
        }
        busy = false
    }

    private func contribute() async {
        busy = true
        do {
            _ = try await NetworkClient.shared.contributeToIkimina(id: id)
            error = nil
            await load()
        } catch {
            self.error = "Could not contribute."
        }
        busy = false
    }

    private func triggerPayout() async {
        busy = true
        payoutMessage = nil
        do {
            let result = try await NetworkClient.shared.triggerIkiminaPayout(id: id)
            payoutMessage = "\(formatMoney(result.amount)) RWF paid out for round \(result.ikimina.currentRound - 1)."
            error = nil
            await load()
        } catch {
            self.error = "Could not trigger the payout yet."
        }
        busy = false
    }
}

private func formatMoney(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
