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

private enum IkiminaMode { case list, intro, create }

struct IkiminaScreenView: View {
    var onBack: () -> Void = {}
    @State private var selectedId: String?
    @State private var mode: IkiminaMode = .list

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: {
                    if selectedId != nil { selectedId = nil }
                    else if mode == .create { mode = .intro }
                    else if mode == .intro { mode = .list }
                    else { onBack() }
                }) {
                    IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body)
                }.accessibilityLabel("Back")
                Spacer()
                Text(selectedId != nil ? "Ikimina detail" : mode == .create ? "New ikimina" : "Ikimina").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            if let id = selectedId {
                IkiminaDetailContent(id: id)
            } else if mode == .intro {
                IkiminaIntroContent(onContinue: { mode = .create })
            } else if mode == .create {
                IkiminaCreateContent(onCreated: { mode = .list })
            } else {
                ScrollView {
                    IkiminaListContent(onOpen: { selectedId = $0 }, onStartCreate: { mode = .intro })
                }
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

// Real Toss product-intro pattern (docs/UI_UX_GUIDELINES.md rule 13) -- ikimina is
// itunda's own real ROSCA product, "genuinely the first feature in this codebase not
// sourced from Toss/Kakao/Naver/Coupang" (see this file's own header doc comment), so
// unlike Grow31/WeeklySavings a user has no prior mental model from those reference
// apps at all. Matches Android's identical IkiminaIntroContent.
private struct IkiminaIntroContent: View {
    let onContinue: () -> Void

    var body: some View {
        FixedBottomCTA {
            VStack(alignment: .leading, spacing: 20) {
                Text("A rotating savings group with people you trust")
                    .font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Everyone contributes the same fixed amount")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Every member pays in the same amount, on the same weekly or monthly schedule you set when you create the group.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("One member takes home the full pot each round")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("The payout rotates in a pre-agreed order until every member has been paid exactly once -- not itunda deciding who's next, the group's own real turn order.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("You choose the size and schedule")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Set a max member count and a weekly or monthly contribution cycle -- the group only starts once you're ready.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            .padding()
        } cta: {
            IdsButton(text: "Continue", action: onContinue)
        }
    }
}

private struct IkiminaCreateContent: View {
    let onCreated: () -> Void

    @State private var name = ""
    @State private var contributionAmount = ""
    @State private var cycleFrequencyDays = 30
    @State private var memberCap = "10"
    @State private var creating = false
    @State private var error: String?

    var body: some View {
        FixedBottomCTA {
            VStack(alignment: .leading, spacing: 8) {
                IdsTextField("Group name (e.g. Umuryango)", text: $name)
                IdsTextField("Contribution per round (RWF)", text: $contributionAmount, keyboardType: .numberPad)
                Picker("Frequency", selection: $cycleFrequencyDays) {
                    Text("Weekly").tag(7)
                    Text("Monthly").tag(30)
                }
                .pickerStyle(.segmented)
                IdsTextField("Max members", text: $memberCap, keyboardType: .numberPad)
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
            }
            .padding()
        } cta: {
            IdsButton(text: creating ? "Creating…" : "Create", isEnabled: !creating, action: { Task { await create() } })
        }
    }

    private func create() async {
        guard !name.isEmpty, let amount = Double(contributionAmount), amount > 0, let cap = Int(memberCap), cap >= 2 else { return }
        creating = true
        do {
            _ = try await NetworkClient.shared.createIkimina(name: name, contributionAmount: amount, cycleFrequencyDays: cycleFrequencyDays, memberCap: cap)
            ToastCenter.shared.show("Ikimina group created.")
            onCreated()
        } catch {
            self.error = "Could not create this ikimina."
        }
        creating = false
    }
}

private struct IkiminaListContent: View {
    let onOpen: (String) -> Void
    let onStartCreate: () -> Void

    @State private var ikiminas: [IkiminaDto] = []
    @State private var loaded = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 10) {
            Text("Everyone contributes the same amount each round; one member takes home the full pot, in turn.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)

            IdsButton(text: "+ New ikimina", action: onStartCreate)

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
                        CountUpText("\(formatMoney(current.balance)) RWF").font(.title).bold().foregroundColor(IDS.Colors.textPrimary)
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
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "ALREADY_MEMBER" {
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
        payoutMessage = nil
        do {
            let result = try await NetworkClient.shared.contributeToIkimina(id: id)
            // Real gap found 2026-09-05 (matches bank-mfe's own already-correct
            // handleContribute, and Android's identical IkiminaScreen.kt fix) --
            // this contribution may have just completed the round, in which case
            // the backend already auto-triggered the payout (see
            // IkiminaService.contributeThisRound's own doc comment); this used to
            // discard the response entirely, silently leaving the member to
            // wonder why the round advanced with no visible payout.
            if let payout = result.payout {
                payoutMessage = "\(formatMoney(payout.amount)) RWF paid out for round \(payout.ikimina.currentRound - 1)."
            }
            error = nil
            await load()
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "ALREADY_CONTRIBUTED" {
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

