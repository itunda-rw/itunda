import SwiftUI
import CoreDesignSystem
import CoreNetwork


// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// PlatformMembershipDto's own doc comment. bank-mfe/Android already have this; this is
// the iOS client. Deliberately a separate card from EatsMembershipCard below, not a
// replacement: waives the fee at every restaurant, no merchant opt-in required.
struct PlatformMembershipCard: View {
    @State private var membership: PlatformMembershipDto?
    @State private var loaded = false
    @State private var busy = false
    @State private var error: String?

    private func load() async {
        do {
            membership = try await NetworkClient.shared.getMyPlatformMembership().membership
        } catch {
            // Real, non-critical -- the rest of Eats still works without this card.
        }
        loaded = true
    }

    private var isActive: Bool {
        guard let membership, let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil) else { return false }
        return activeUntil > Date()
    }

    var body: some View {
        Group {
            if loaded {
                VStack(alignment: .leading, spacing: 6) {
                    Text("⚡ itunda Plus").font(.headline).bold().foregroundColor(.white)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.white)
                    }
                    if isActive, let membership {
                        let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil)
                        Text("Free delivery active until \(activeUntil.map { $0.formatted(date: .abbreviated, time: .omitted) } ?? "") at every restaurant, no participation required.")
                            .font(.caption).foregroundColor(.white.opacity(0.9))
                    } else {
                        Text("Free delivery at every restaurant -- no minimum order, no restaurant opt-in required.")
                            .font(.caption).foregroundColor(.white.opacity(0.9))
                        HStack(spacing: 8) {
                            ForEach(platformMembershipTiers, id: \.days) { tier in
                                Button(action: { Task { await subscribe(days: tier.days) } }) {
                                    Text(busy ? "…" : "\(tier.days) days -- \(tier.priceRwf) RWF")
                                        .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(Color.white).clipShape(Capsule())
                                }
                                .disabled(busy)
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                // Real bold gradient hero banner (itunda Eats redesign, 2026-08-28,
                // direct user reference: real Coupang 와우(WOW) membership banner) --
                // matches web/Android's own just-built treatment. No "darker brand"
                // token exists in IDS.Colors, so this hardcodes the exact same real hex
                // value web's own --itunda-indigo-active reuses (see
                // packages/design-tokens/tokens.css) rather than inventing a new shade.
                .background(LinearGradient(colors: [IDS.Colors.brand, Color(red: 0x5C / 255, green: 0x55 / 255, blue: 0xD8 / 255)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .cornerRadius(IDS.Layout.cardCornerRadius)
            }
        }
        .task { await load() }
    }

    private func subscribe(days: Int) async {
        busy = true
        error = nil
        do {
            _ = try await NetworkClient.shared.subscribePlatformMembership(days: days)
            await load()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        busy = false
    }
}

// Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- see
// EatsMembershipDto's own doc comment. bank-mfe/Android already have this; this is the
// iOS client.
struct EatsMembershipCard: View {
    @State private var membership: EatsMembershipDto?
    @State private var loaded = false
    @State private var busy = false
    @State private var error: String?

    private func load() async {
        do {
            membership = try await NetworkClient.shared.getMyEatsMembership().membership
        } catch {
            // Real, non-critical -- the rest of Eats still works without this card.
        }
        loaded = true
    }

    private var isActive: Bool {
        guard let membership, let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil) else { return false }
        return activeUntil > Date()
    }

    var body: some View {
        Group {
            if loaded {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Eats Club").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if isActive, let membership {
                        let activeUntil = ISO8601DateFormatter().date(from: membership.activeUntil) ?? isoDateFormatterFractional.date(from: membership.activeUntil)
                        Text("Free delivery active until \(activeUntil.map { $0.formatted(date: .abbreviated, time: .omitted) } ?? "") at participating restaurants.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        Text("Free delivery at participating restaurants -- no minimum order.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack(spacing: 8) {
                            ForEach(eatsMembershipTiers, id: \.days) { tier in
                                Button(action: { Task { await subscribe(days: tier.days) } }) {
                                    Text(busy ? "…" : "\(tier.days) days -- \(tier.priceRwf) RWF")
                                        .font(.caption).bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busy)
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
            }
        }
        .task { await load() }
    }

    private func subscribe(days: Int) async {
        busy = true
        error = nil
        do {
            _ = try await NetworkClient.shared.subscribeEatsMembership(days: days)
            await load()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        busy = false
    }
}

