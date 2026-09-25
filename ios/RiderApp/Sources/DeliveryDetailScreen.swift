import SwiftUI
import CoreDesignSystem

/// A single active (or just-completed) delivery. Pushes this rider's real live
/// coordinates every 15s while the order is in an in-progress status
/// (RIDER_ASSIGNED or PICKED_UP) -- closes a real, previously-unused backend
/// endpoint (POST /riders/location) that powers both nearest-rider dispatch
/// ranking and the buyer's own "watch your order arrive" view. Only pushes while
/// this screen is open and the app is in the foreground -- background tracking is
/// a real, named v1 scope cut, not attempted this pass.
struct DeliveryDetailScreen: View {
    let orderId: String
    let onBack: () -> Void

    @State private var order: EatsOrderDto?
    @State private var restaurantName: String?
    @State private var error: String?
    @State private var advancing = false
    @StateObject private var locationFetcher = RiderLocationFetcher()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Text("Delivery").font(.headline)
                Spacer()
            }
            .padding(12)

            if let current = order {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(restaurantName ?? current.restaurantId).font(.title3).bold()
                        StatusBadge(status: current.status)

                        Text("Deliver to").font(.caption).foregroundColor(.secondary)
                        Text(current.deliveryAddress).font(.body)

                        if let notes = current.deliveryNotes, !notes.isEmpty {
                            Text("Note: \(notes)").font(.subheadline)
                        }
                        if let distanceKm = current.distanceKm {
                            Text(String(format: "%.1f km", distanceKm)).font(.subheadline)
                        }

                        HStack {
                            Text("Order total")
                            Spacer()
                            Text("\(formattedRWF(current.itemsSubtotal)) RWF")
                        }
                        HStack {
                            Text("Your delivery fee").bold()
                            Spacer()
                            Text("\(formattedRWF(current.deliveryFee)) RWF").bold().foregroundColor(.blue)
                        }
                    }
                    .padding(16)
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(16)

                    if !locationFetcher.isAuthorized {
                        Text("Location permission is needed so the buyer can see you're on the way.")
                            .foregroundColor(.red).font(.footnote)
                            .padding(.horizontal, 16)
                    }

                    if let error {
                        Text(error).foregroundColor(IDS.Colors.danger).font(.footnote).padding(.horizontal, 16)
                    }

                    if let (nextStatus, label) = nextAction(for: current.status) {
                        Button(action: { Task { await advance(to: nextStatus) } }) {
                            Text(advancing ? "Updating…" : label)
                                .bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(12)
                        }
                        .disabled(advancing)
                        .padding(16)
                    } else if current.status == "DELIVERED" {
                        Text("Delivered -- \(formattedRWF(current.deliveryFee)) RWF paid to your account.")
                            .bold().foregroundColor(.blue)
                            .padding(16)
                    }
                }
            } else {
                Spacer()
                if let error {
                    Text(error).foregroundColor(IDS.Colors.danger)
                } else {
                    SkeletonBlock(height: 96)
                }
                Spacer()
            }
        }
        .task { await refresh() }
        .task {
            locationFetcher.requestLocation()
            locationFetcher.onLocation = { coordinate in
                Task { try? await RiderNetworkClient.shared.updateRiderLocation(latitude: coordinate.latitude, longitude: coordinate.longitude) }
            }
            while true {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                if let status = order?.status, status == "RIDER_ASSIGNED" || status == "PICKED_UP" {
                    locationFetcher.requestLocation()
                }
            }
        }
    }

    private func nextAction(for status: String) -> (String, String)? {
        switch status {
        case "RIDER_ASSIGNED": return ("PICKED_UP", "I've picked up the order")
        case "PICKED_UP": return ("DELIVERED", "I've delivered the order")
        default: return nil
        }
    }

    private func refresh() async {
        do {
            let fetched = try await RiderNetworkClient.shared.getOrder(orderId).order
            order = fetched
            if restaurantName == nil {
                restaurantName = (try? await RiderNetworkClient.shared.getShoppingMerchants().merchants.first { $0.merchantId == fetched.restaurantId }?.businessName) ?? fetched.restaurantId
            }
            error = nil
        } catch {
            self.error = "Couldn't load this delivery."
        }
    }

    private func advance(to nextStatus: String) async {
        advancing = true
        defer { advancing = false }
        do {
            order = try await RiderNetworkClient.shared.updateRiderOrderStatus(orderId, status: nextStatus).order
            if nextStatus == "DELIVERED" { onBack() }
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Couldn't update this delivery's status. Try again."
        } catch {
            self.error = "Couldn't update this delivery's status. Try again."
        }
    }
}
