import SwiftUI
import CoreDesignSystem

private enum HomeTab { case available, mine }

// Real itunda-own-fleet Commerce (Shop) delivery claim/tracking -- see
// NetworkClient.swift's own CommerceOrderDto doc comment. A rider can now see and
// claim both food (Eats) and package (Shop) deliveries from this one screen,
// distinguished by this tag since the two are separate real backend order streams
// with different status vocabularies.
enum DeliverySource { case eats, commerce }

enum RiderDelivery: Identifiable {
    case eats(EatsOrderDto)
    case commerce(CommerceOrderDto)

    var id: String {
        switch self {
        case .eats(let o): return "eats:\(o.id)"
        case .commerce(let o): return "commerce:\(o.id)"
        }
    }
    var orderId: String {
        switch self {
        case .eats(let o): return o.id
        case .commerce(let o): return o.id
        }
    }
    var status: String {
        switch self {
        case .eats(let o): return o.status
        case .commerce(let o): return o.status
        }
    }
    var merchantId: String {
        switch self {
        case .eats(let o): return o.restaurantId
        case .commerce(let o): return o.merchantId
        }
    }
    var deliveryAddress: String {
        switch self {
        case .eats(let o): return o.deliveryAddress
        case .commerce(let o): return o.deliveryAddress
        }
    }
    var source: DeliverySource {
        switch self {
        case .eats: return .eats
        case .commerce: return .commerce
        }
    }
}

struct RiderHomeScreen: View {
    let onOpenDelivery: (String) -> Void
    // Real Commerce/Shop package delivery -- see CommerceDeliveryDetailScreen's own
    // doc comment on why this hands over the full order object rather than an id
    // (the rider is never authorized to GET a Commerce order by id directly).
    let onOpenCommerceDelivery: (CommerceOrderDto) -> Void
    let onLogout: () -> Void

    @State private var rider: RiderDto?
    // Real rider rating (item 142) -- see NetworkClient.swift's own doc comment on
    // getRiderRating.
    @State private var rating: RiderRatingResponse?
    @State private var tab: HomeTab = .available
    @State private var available: [EatsOrderDto]?
    @State private var mine: [EatsOrderDto]?
    @State private var availableCommerce: [CommerceOrderDto]?
    @State private var mineCommerce: [CommerceOrderDto]?
    @State private var offers: [(NotificationDto, String)] = []
    @State private var restaurantNames: [String: String] = [:]
    @State private var error: String?
    @State private var togglingAvailability = false
    @StateObject private var locationFetcher = RiderLocationFetcher()

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Itunda Rider").font(.title2).bold()
                    Text(rider?.available == true ? "You're online" : "You're offline")
                        .font(.caption)
                        .foregroundColor(rider?.available == true ? .blue : .secondary)
                    if let rating, let average = rating.average {
                        Text("⭐ \(String(format: "%.1f", average)) (\(rating.count))")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                Toggle("", isOn: Binding(
                    get: { rider?.available == true },
                    set: { newValue in toggleAvailability(newValue) }
                ))
                .labelsHidden()
                .disabled(togglingAvailability || rider == nil)
                Button(action: { RiderKeychainTokenStore.shared.clearSession(); onLogout() }) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                }
            }
            .padding(16)

            ForEach(offers, id: \.0.id) { (notification, orderId) in
                VStack(alignment: .leading, spacing: 8) {
                    Text(notification.title).bold()
                    Text(notification.body).font(.footnote)
                    HStack {
                        Button("Accept") { Task { await acceptOffer(notification: notification, orderId: orderId) } }
                            .buttonStyle(.borderedProminent)
                        Button("Decline") { Task { await declineOffer(notification: notification, orderId: orderId) } }
                            .buttonStyle(.bordered)
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(IDS.Colors.brand.opacity(0.15))
                .cornerRadius(12)
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }

            Picker("", selection: $tab) {
                Text("Available").tag(HomeTab.available)
                Text("My deliveries").tag(HomeTab.mine)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            if let error {
                Text(error).foregroundColor(.red).font(.footnote).padding(.horizontal, 16)
            }

            let eatsList = tab == .available ? available : mine
            let commerceList = tab == .available ? availableCommerce : mineCommerce
            // Real combined delivery feed (2026-08-04): Eats (food) + Commerce (Shop
            // packages) are two separate real backend order streams sharing this
            // one rider's claim queue -- loading is "both requests have come back,"
            // not "either one has."
            if eatsList == nil && commerceList == nil {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                let combined: [RiderDelivery] = (eatsList ?? []).map { RiderDelivery.eats($0) } + (commerceList ?? []).map { RiderDelivery.commerce($0) }
                if combined.isEmpty {
                    Spacer()
                    // Real copy-voice fix (item 244, round 8), matching Android's
                    // same-day fix: available is genuinely passive, mine has a real fix.
                    Text(tab == .available ? "No open deliveries right now — check back soon, new ones appear automatically." : "You haven't claimed any deliveries yet — switch to Available to claim your first one.")
                        .foregroundColor(.secondary)
                    Spacer()
                } else {
                    ScrollView {
                        if tab == .mine {
                            let deliveredEatsFees = (eatsList ?? []).filter { $0.status == "DELIVERED" }.reduce(0) { $0 + $1.deliveryFee }
                            let deliveredCount = (eatsList ?? []).filter { $0.status == "DELIVERED" }.count + (commerceList ?? []).filter { $0.status == "DELIVERED" }.count
                            if deliveredCount > 0 {
                                Text("Recent earnings: \(formattedRWF(deliveredEatsFees)) RWF (\(deliveredCount) deliveries)")
                                    .bold()
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 16)
                            }
                        }
                        LazyVStack(spacing: 10) {
                            ForEach(combined) { delivery in
                                RiderDeliveryRow(
                                    delivery: delivery,
                                    merchantName: restaurantNames[delivery.merchantId] ?? delivery.merchantId,
                                    showClaim: tab == .available,
                                    onClaim: { Task { await claim(delivery) } },
                                    onOpen: { open(delivery) }
                                )
                            }
                        }
                        .padding(16)
                    }
                }
            }
        }
        .task {
            await refreshMerchants()
            await refreshRiderProfile()
            await refreshDeliveries()
            await refreshOffers()
        }
        .task {
            while true {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                await refreshOffers()
                await refreshDeliveries()
            }
        }
    }

    private func toggleAvailability(_ newValue: Bool) {
        togglingAvailability = true
        Task {
            do {
                rider = try await RiderNetworkClient.shared.setRiderAvailability(newValue).rider
                if newValue {
                    locationFetcher.onLocation = { coordinate in
                        Task { try? await RiderNetworkClient.shared.updateRiderLocation(latitude: coordinate.latitude, longitude: coordinate.longitude) }
                    }
                    locationFetcher.requestLocation()
                }
            } catch {
                self.error = "Couldn't update your availability. Try again."
            }
            togglingAvailability = false
        }
    }

    private func refreshMerchants() async {
        if let merchants = try? await RiderNetworkClient.shared.getShoppingMerchants().merchants {
            restaurantNames = Dictionary(uniqueKeysWithValues: merchants.map { ($0.merchantId, $0.businessName) })
        }
    }

    private func refreshRiderProfile() async {
        rider = try? await RiderNetworkClient.shared.getMyRiderProfile().rider
        if let riderId = rider?.id {
            rating = try? await RiderNetworkClient.shared.getRiderRating(riderId: riderId)
        }
    }

    private func refreshDeliveries() async {
        do {
            available = try await RiderNetworkClient.shared.getAvailableDeliveries().orders
            mine = try await RiderNetworkClient.shared.getRiderDeliveries().orders
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        // Real, separate Commerce delivery stream (2026-08-04) -- kept in its own
        // do/catch so an outage in one order type never blanks the other.
        do {
            availableCommerce = try await RiderNetworkClient.shared.getAvailableCommerceDeliveries().orders
            mineCommerce = try await RiderNetworkClient.shared.getMyCommerceDeliveries().orders
        } catch { /* package deliveries just won't show this tick */ }
    }

    private func refreshOffers() async {
        guard let notifications = try? await RiderNetworkClient.shared.getNotifications().notifications else { return }
        offers = notifications
            .filter { !$0.isRead && $0.type == "DELIVERY_OFFER" }
            .compactMap { n -> (NotificationDto, String)? in
                guard let dataJson = n.dataJson, let data = dataJson.data(using: .utf8),
                      let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let orderId = obj["orderId"] as? String else { return nil }
                return (n, orderId)
            }
    }

    private func acceptOffer(notification: NotificationDto, orderId: String) async {
        do {
            _ = try await RiderNetworkClient.shared.claimDelivery(orderId)
            try? await RiderNetworkClient.shared.markNotificationRead(notification.id)
            await refreshOffers(); await refreshDeliveries()
            onOpenDelivery(orderId)
        } catch {
            self.error = "This delivery is no longer available."
            await refreshOffers(); await refreshDeliveries()
        }
    }

    private func declineOffer(notification: NotificationDto, orderId: String) async {
        _ = try? await RiderNetworkClient.shared.declineDelivery(orderId)
        try? await RiderNetworkClient.shared.markNotificationRead(notification.id)
        await refreshOffers()
    }

    private func claim(_ delivery: RiderDelivery) async {
        do {
            switch delivery {
            case .eats(let order):
                _ = try await RiderNetworkClient.shared.claimDelivery(order.id)
                await refreshDeliveries()
                onOpenDelivery(order.id)
            case .commerce(let order):
                let claimed = try await RiderNetworkClient.shared.claimCommerceDelivery(order.id).order
                await refreshDeliveries()
                onOpenCommerceDelivery(claimed)
            }
        } catch {
            self.error = "Someone else just claimed this delivery."
            await refreshDeliveries()
        }
    }

    private func open(_ delivery: RiderDelivery) {
        switch delivery {
        case .eats(let order): onOpenDelivery(order.id)
        case .commerce(let order): onOpenCommerceDelivery(order)
        }
    }
}

private struct RiderDeliveryRow: View {
    let delivery: RiderDelivery
    let merchantName: String
    let showClaim: Bool
    let onClaim: () -> Void
    let onOpen: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    // Real combined feed source tag -- see RiderDelivery's own doc comment.
                    Text(delivery.source == .eats ? "🍔 Food" : "📦 Package").font(.caption2).foregroundColor(.secondary)
                    Text(merchantName).bold()
                }
                Spacer()
                switch delivery {
                case .eats(let order):
                    Text("\(formattedRWF(order.deliveryFee)) RWF").bold().foregroundColor(.blue)
                case .commerce(let order):
                    // Commerce's Order entity models no separate rider-payout field --
                    // showing the real order total rather than fabricating a
                    // delivery-fee figure the backend doesn't compute.
                    Text("\(formattedRWF(order.totalAmount)) RWF order").bold().foregroundColor(.blue)
                }
            }
            Text(delivery.deliveryAddress).font(.footnote)
            if case .eats(let order) = delivery, let distanceKm = order.distanceKm {
                Text(String(format: "%.1f km away", distanceKm)).font(.footnote)
            }
            StatusBadge(status: delivery.status)
            if showClaim {
                Button(action: onClaim) {
                    Text("Claim this delivery").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .contentShape(Rectangle())
        .onTapGesture { if !showClaim { onOpen() } }
    }
}

struct StatusBadge: View {
    let status: String

    private var label: String {
        switch status {
        case "RIDER_ASSIGNED": return "Heading to pickup"
        case "PICKED_UP": return "On the way"
        case "PACKED": return "Ready for pickup"
        case "SHIPPED": return "On the way"
        case "DELIVERED": return "Delivered"
        default: return status
        }
    }

    var body: some View {
        Text(label)
            .font(.caption2).bold()
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(IDS.Colors.brand.opacity(0.15))
            .cornerRadius(8)
    }
}

func formattedRWF(_ amount: Double) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.maximumFractionDigits = 0
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
}
