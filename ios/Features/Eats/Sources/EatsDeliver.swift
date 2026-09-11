import SwiftUI
import CoreDesignSystem
import CoreNetwork


struct DeliverContent: View {
    @State private var rider: RiderDto?
    @State private var loadedRider = false
    @State private var error: String?
    @State private var registering = false
    @State private var available: [EatsOrderDto]?
    @State private var mine: [EatsOrderDto]?
    @State private var busyOrderId: String?
    // Real pagination fix (2026-09-11, ported from bank-mfe's own fix and
    // Android's port -- see project_itunda_pagination_discard_sweep memory):
    // both lists are polled every 4s, so page 0 must always stay a live,
    // page-0-only fetch. olderAvailable/olderMine are separate accumulators
    // populated only by their own "Load more" action, never touched by the
    // poll.
    @State private var olderAvailable: [EatsOrderDto] = []
    @State private var availablePage = 0
    @State private var availableHasMore = false
    @State private var loadingMoreAvailable = false
    @State private var olderMine: [EatsOrderDto] = []
    @State private var minePage = 0
    @State private var mineHasMore = false
    @State private var loadingMoreMine = false

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                if !loadedRider {
                    SkeletonBlock(height: 120)
                } else if rider == nil {
                    riderOnboarding
                } else {
                    riderDashboard
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadRider() }
        .task {
            while !Task.isCancelled {
                if rider != nil { await loadDeliveries() }
                try? await Task.sleep(nanoseconds: 4_000_000_000)
            }
        }
    }

    private var riderOnboarding: some View {
        VStack(spacing: 12) {
            Image(systemName: "bicycle").font(IDS.scaledFont(size: 30, weight: .regular, relativeTo: .title2)).foregroundColor(IDS.Colors.brand)
            Text("Deliver with Itunda").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text("Earn a real delivery fee for every order you deliver, paid straight to your account.")
                .font(.subheadline)
                .foregroundColor(IDS.Colors.textSecondary)
                .multilineTextAlignment(.center)
            Button(action: { Task { await register() } }) {
                Text(registering ? "Registering…" : "Become a rider")
                    .font(IDS.Typography.bodyBold).foregroundColor(.white)
                    .padding(.horizontal, 24).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(14)
            }
            .disabled(registering)
            if let error { Text(error).font(.caption).foregroundColor(.red) }
        }
        .frame(maxWidth: .infinity)
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- the
        // whole screen's content when shown (rider not yet onboarded).
        .padding(.vertical, 10)
    }

    private var riderDashboard: some View {
        let rider = rider!
        let allAvailable = (available ?? []) + olderAvailable
        let allMine = (mine ?? []) + olderMine
        let active = allMine.filter { $0.status != "DELIVERED" }
        let past = allMine.filter { $0.status == "DELIVERED" }

        return VStack(spacing: IDS.Layout.cardGap) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(rider.available ? "You're online" : "You're offline").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text(rider.available ? "Visible for new deliveries" : "Go online to see deliveries").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Button(action: { Task { await toggleAvailability() } }) {
                    Text(rider.available ? "Go offline" : "Go online")
                        .font(.subheadline).bold().foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(rider.available ? Color.red : IDS.Colors.brand)
                        .cornerRadius(14)
                }
            }
            // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
            // a sibling section shown together with the deliveries lists below, so a
            // real Divider marks the boundary instead (docs/UI_UX_GUIDELINES.md §10).
            .padding(.vertical, 10)
            Divider().overlay(IDS.Colors.divider)

            if let error { Text(error).font(.caption).foregroundColor(.red) }

            if !active.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Your active deliveries").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(active) { order in
                        EatsOrderRow(order: order) {
                            if let next = nextRiderStatus(order.status) {
                                Button(action: { Task { await advanceRider(order, to: next) } }) {
                                    Text(busyOrderId == order.id ? "Updating…" : "Mark \((eatsStatusLabel[next] ?? next).lowercased())")
                                        .font(.subheadline).bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(IDS.Colors.brand).cornerRadius(12)
                                }
                                .disabled(busyOrderId == order.id)
                            }
                        }
                    }
                }
            }

            if rider.available {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Available deliveries").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    if available == nil {
                        SkeletonBlock(height: 80)
                    } else if allAvailable.isEmpty {
                        EmptyStateView("No deliveries waiting right now — stay online and you'll be notified.")
                    } else {
                        ForEach(allAvailable) { order in
                            EatsOrderRow(order: order) {
                                Button(action: { Task { await claim(order) } }) {
                                    Text(busyOrderId == order.id ? "Claiming…" : "Claim delivery")
                                        .font(.subheadline).bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(IDS.Colors.brand).cornerRadius(12)
                                }
                                .disabled(busyOrderId == order.id)
                            }
                        }
                        if availableHasMore {
                            Button(loadingMoreAvailable ? "Loading…" : "Load more") {
                                Task { await loadMoreAvailable() }
                            }
                            .disabled(loadingMoreAvailable)
                        }
                    }
                }
            }

            if !past.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Completed").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(past) { EatsOrderRow(order: $0) }
                    if mineHasMore {
                        Button(loadingMoreMine ? "Loading…" : "Load more") {
                            Task { await loadMoreMine() }
                        }
                        .disabled(loadingMoreMine)
                    }
                }
            }
        }
    }

    private func loadRider() async {
        do {
            let res = try await NetworkClient.shared.getMyRiderProfile()
            rider = res.rider
            error = nil
        } catch let NetworkError.httpError(statusCode) where statusCode == 404 {
            rider = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        loadedRider = true
    }

    private func loadDeliveries() async {
        do {
            async let availableRes = NetworkClient.shared.getAvailableDeliveries(page: 0)
            async let mineRes = NetworkClient.shared.getRiderDeliveries(page: 0)
            let (a, m) = try await (availableRes, mineRes)
            available = a.orders
            availableHasMore = a.page + 1 < a.totalPages
            mine = m.orders
            mineHasMore = m.page + 1 < m.totalPages
        } catch {
            // Keep showing the last-known lists on a transient poll failure.
        }
    }

    private func loadMoreAvailable() async {
        let nextPage = availablePage + 1
        loadingMoreAvailable = true
        defer { loadingMoreAvailable = false }
        guard let res = try? await NetworkClient.shared.getAvailableDeliveries(page: nextPage) else { return }
        olderAvailable += res.orders
        availablePage = nextPage
        availableHasMore = res.page + 1 < res.totalPages
    }

    private func loadMoreMine() async {
        let nextPage = minePage + 1
        loadingMoreMine = true
        defer { loadingMoreMine = false }
        guard let res = try? await NetworkClient.shared.getRiderDeliveries(page: nextPage) else { return }
        olderMine += res.orders
        minePage = nextPage
        mineHasMore = res.page + 1 < res.totalPages
    }

    private func register() async {
        registering = true
        error = nil
        defer { registering = false }
        do {
            rider = try await NetworkClient.shared.registerRider().rider
        } catch let NetworkError.httpError(statusCode) {
            error = errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func toggleAvailability() async {
        guard let rider else { return }
        do {
            self.rider = try await NetworkClient.shared.setRiderAvailability(!rider.available).rider
        } catch let NetworkError.httpError(statusCode) {
            error = errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func claim(_ order: EatsOrderDto) async {
        busyOrderId = order.id
        error = nil
        defer { busyOrderId = nil }
        do {
            _ = try await NetworkClient.shared.claimDelivery(order.id)
            await loadDeliveries()
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = message ?? errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func advanceRider(_ order: EatsOrderDto, to status: String) async {
        busyOrderId = order.id
        error = nil
        defer { busyOrderId = nil }
        do {
            _ = try await NetworkClient.shared.updateRiderOrderStatus(order.id, status: status)
            await loadDeliveries()
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = message ?? errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
