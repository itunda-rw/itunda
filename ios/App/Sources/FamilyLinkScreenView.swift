import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same per-file shape TransactionHistoryScreen.swift
// already established.
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

/// Real Toss 유스 (Toss Youth)-style guardian-child account link -- see
/// rw.itunda.family.FamilyLinkService's own doc comment for the full sourced account and
/// honest scope boundary: real read-only spending oversight only, no new allowance
/// mechanism (point an existing AutoTransfer/ScheduledTransfer at the child's phone
/// number instead). bank-mfe/Android already have this; this is the first iOS client,
/// mirroring bank-mfe's `FamilyLinkCard` exactly.
struct FamilyLinkScreenView: View {
    var onBack: () -> Void = {}

    @State private var invites: [FamilyLinkDto] = []
    @State private var children: [FamilyLinkViewDto] = []
    @State private var guardians: [FamilyLinkViewDto] = []
    @State private var showInvite = false
    @State private var childPhone = ""
    @State private var busy = false
    @State private var busyId: String?
    @State private var error: String?
    @State private var openOverviewFor: String?
    @State private var overview: ChildOverviewDto?

    private var hasAnything: Bool { !invites.isEmpty || !children.isEmpty || !guardians.isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Family").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Link a family member").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Button(action: { showInvite.toggle() }) {
                            Text(showInvite ? "Cancel" : "+ Link").bold().font(.caption)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(Color(.secondarySystemBackground)).cornerRadius(8)
                        }
                    }

                    if showInvite {
                        HStack(spacing: 10) {
                            IdsTextField("Phone number", text: $childPhone)
                            Button(action: { Task { await invite() } }) {
                                Text(busy ? "…" : "Invite").bold().foregroundColor(.white)
                                    .padding(.horizontal, 16).padding(.vertical, 14)
                                    .background(busy || childPhone.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                                    .cornerRadius(10)
                            }
                            .disabled(busy || childPhone.isEmpty)
                        }
                    }

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }

                    if !invites.isEmpty {
                        Text("Pending invitations").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        ForEach(invites) { inv in
                            HStack {
                                Text("Family link request").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Button(action: { Task { await respond(inv.id, accept: true) } }) {
                                    Text("Accept").bold().foregroundColor(.white).font(.caption)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(IDS.Colors.brand).cornerRadius(8)
                                }
                                .disabled(busyId == inv.id)
                                Button(action: { Task { await respond(inv.id, accept: false) } }) {
                                    Text("Decline").bold().font(.caption)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                }
                                .disabled(busyId == inv.id)
                            }
                            .padding(14).background(Color(.secondarySystemBackground)).cornerRadius(12)
                        }
                    }

                    if !children.isEmpty {
                        Text("Linked children").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        ForEach(children) { c in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(c.childName).bold().font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                    Spacer()
                                    Button(action: { Task { await toggleOverview(c.link.childUserId) } }) {
                                        Text(openOverviewFor == c.link.childUserId ? "Hide" : "View").bold().font(.caption)
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                    }
                                    Button(action: { Task { await revoke(c.link.id) } }) {
                                        Text("Unlink").bold().font(.caption)
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                    }
                                    .disabled(busyId == c.link.id)
                                }
                                if openOverviewFor == c.link.childUserId, let overview, overview.childUserId == c.link.childUserId {
                                    Text("Balance: \(formatAmount(Int(overview.accountBalance))) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                    if overview.recentTransactions.isEmpty {
                                        Text("No transactions yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    } else {
                                        ForEach(overview.recentTransactions.prefix(5), id: \.id) { t in
                                            Text("\(t.description) · \(formatAmount(Int(t.amount))) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        }
                                    }
                                }
                            }
                            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground)).cornerRadius(12)
                        }
                    }

                    if !guardians.isEmpty {
                        Text("Your guardians").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                        ForEach(guardians) { g in
                            HStack {
                                Text(g.guardianName).font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Button(action: { Task { await revoke(g.link.id) } }) {
                                    Text("Unlink").bold().font(.caption)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                }
                                .disabled(busyId == g.link.id)
                            }
                            .padding(14).background(Color(.secondarySystemBackground)).cornerRadius(12)
                        }
                    }

                    if !hasAnything {
                        EmptyStateView("No family members linked yet — invite one above.")
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        invites = (try? await NetworkClient.shared.getMyFamilyInvites())?.invites ?? []
        children = (try? await NetworkClient.shared.getMyFamilyChildren())?.children ?? []
        guardians = (try? await NetworkClient.shared.getMyFamilyGuardians())?.guardians ?? []
    }

    private func invite() async {
        guard !childPhone.isEmpty else { return }
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.inviteFamilyChild(childPhoneNumber: childPhone.trimmingCharacters(in: .whitespaces))
            childPhone = ""
            showInvite = false
            await load()
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = statusCode == 409 ? "A link with this account already exists or is pending." : (message ?? "Could not send this invitation.")
        } catch {
            self.error = "Could not send this invitation."
        }
    }

    private func respond(_ id: String, accept: Bool) async {
        busyId = id
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.respondToFamilyInvite(id, accept: accept)
            await load()
        } catch {
            self.error = "Could not respond to this invitation."
        }
    }

    private func revoke(_ id: String) async {
        busyId = id
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.revokeFamilyLink(id)
            openOverviewFor = nil
            await load()
        } catch {
            self.error = "Could not unlink this account."
        }
    }

    private func toggleOverview(_ childUserId: String) async {
        if openOverviewFor == childUserId {
            openOverviewFor = nil
            return
        }
        openOverviewFor = childUserId
        do {
            overview = try await NetworkClient.shared.getChildOverview(childUserId).overview
        } catch {
            self.error = "Could not load this overview."
        }
    }
}
