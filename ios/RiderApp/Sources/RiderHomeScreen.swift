import SwiftUI

private enum HomeTab { case available, mine }

struct RiderHomeScreen: View {
    let onOpenDelivery: (String) -> Void
    let onLogout: () -> Void

    @State private var rider: RiderDto?
    @State private var tab: HomeTab = .available
    @State private var available: [EatsOrderDto]?
    @State private var mine: [EatsOrderDto]?
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
                .background(Color.blue.opacity(0.15))
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

            let list = tab == .available ? available : mine
            if list == nil {
                Spacer()
                ProgressView()
                Spacer()
            } else if list!.isEmpty {
                Spacer()
                Text(tab == .available ? "No open deliveries right now." : "No deliveries yet.")
                    .foregroundColor(.secondary)
                Spacer()
            } else {
                ScrollView {
                    if tab == .mine {
                        let delivered = list!.filter { $0.status == "DELIVERED" }
                        if !delivered.isEmpty {
                            Text("Recent earnings: \(formattedRWF(delivered.reduce(0) { $0 + $1.deliveryFee })) RWF (\(delivered.count) deliveries)")
                                .bold()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 16)
                        }
                    }
                    LazyVStack(spacing: 10) {
                        ForEach(list!) { order in
                            DeliveryRow(
                                order: order,
                                restaurantName: restaurantNames[order.restaurantId] ?? order.restaurantId,
                                showClaim: tab == .available,
                                onClaim: { Task { await claim(order) } },
                                onOpen: { onOpenDelivery(order.id) }
                            )
                        }
                    }
                    .padding(16)
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
    }

    private func refreshDeliveries() async {
        do {
            available = try await RiderNetworkClient.shared.getAvailableDeliveries().orders
            mine = try await RiderNetworkClient.shared.getRiderDeliveries().orders
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
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

    private func claim(_ order: EatsOrderDto) async {
        do {
            _ = try await RiderNetworkClient.shared.claimDelivery(order.id)
            await refreshDeliveries()
            onOpenDelivery(order.id)
        } catch {
            self.error = "Someone else just claimed this delivery."
            await refreshDeliveries()
        }
    }
}

private struct DeliveryRow: View {
    let order: EatsOrderDto
    let restaurantName: String
    let showClaim: Bool
    let onClaim: () -> Void
    let onOpen: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(restaurantName).bold()
                Spacer()
                Text("\(formattedRWF(order.deliveryFee)) RWF").bold().foregroundColor(.blue)
            }
            Text(order.deliveryAddress).font(.footnote)
            if let distanceKm = order.distanceKm {
                Text(String(format: "%.1f km away", distanceKm)).font(.footnote)
            }
            StatusBadge(status: order.status)
            if showClaim {
                Button(action: onClaim) {
                    Text("Claim this delivery").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(Color.blue).cornerRadius(10)
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
        case "DELIVERED": return "Delivered"
        default: return status
        }
    }

    var body: some View {
        Text(label)
            .font(.caption2).bold()
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(Color.blue.opacity(0.15))
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
