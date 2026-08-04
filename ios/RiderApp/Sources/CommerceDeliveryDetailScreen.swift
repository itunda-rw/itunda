import SwiftUI
import CoreDesignSystem

/// A single active (or just-completed) Commerce/Shop package delivery -- see
/// NetworkClient.swift's CommerceOrderDto doc comment for the real backend account
/// this ports (2026-08-04). Simpler than DeliveryDetailScreen's Eats flow: Commerce
/// models only PACKED -> SHIPPED (claim) -> DELIVERED (complete), no
/// RIDER_ASSIGNED/PICKED_UP midpoint, so there's exactly one advance action here.
/// Received as a full object (not re-fetched by id) since OrderService.getOrderDetail
/// only authorizes the buyer or seller, never the rider -- the claim response and the
/// my-deliveries list are the only two places a rider legitimately sees this order's
/// data, and both already hand over the complete object.
struct CommerceDeliveryDetailScreen: View {
    let initialOrder: CommerceOrderDto
    let onBack: () -> Void

    @State private var order: CommerceOrderDto
    @State private var merchantName: String
    @State private var error: String?
    @State private var advancing = false
    @StateObject private var locationFetcher = RiderLocationFetcher()

    init(initialOrder: CommerceOrderDto, onBack: @escaping () -> Void) {
        self.initialOrder = initialOrder
        self.onBack = onBack
        _order = State(initialValue: initialOrder)
        _merchantName = State(initialValue: initialOrder.merchantId)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left") }
                Text("Package delivery").font(.headline)
                Spacer()
            }
            .padding(12)

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(merchantName).font(.title3).bold()
                    StatusBadge(status: order.status)

                    Text("Deliver to").font(.caption).foregroundColor(.secondary)
                    Text(order.deliveryAddress).font(.body)

                    HStack {
                        Text("Order total").bold()
                        Spacer()
                        Text("\(formattedRWF(order.totalAmount)) RWF").bold().foregroundColor(.blue)
                    }
                }
                .padding(16)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(12)
                .padding(16)

                if !locationFetcher.isAuthorized && order.status == "SHIPPED" {
                    Text("Location permission is needed so the buyer can see you're on the way.")
                        .foregroundColor(.red).font(.footnote)
                        .padding(.horizontal, 16)
                }

                if let error {
                    Text(error).foregroundColor(.red).font(.footnote).padding(.horizontal, 16)
                }

                if order.status == "SHIPPED" {
                    Button(action: { Task { await complete() } }) {
                        Text(advancing ? "Updating…" : "I've delivered this order")
                            .bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(advancing)
                    .padding(16)
                } else if order.status == "DELIVERED" {
                    Text("Delivered.")
                        .bold().foregroundColor(.blue)
                        .padding(16)
                }
            }
        }
        .task {
            merchantName = (try? await RiderNetworkClient.shared.getShoppingMerchants().merchants.first { $0.merchantId == order.merchantId }?.businessName) ?? order.merchantId
        }
        .task {
            // Real live rider-location push while this delivery is in transit --
            // reuses the same shared Rider.currentLatitude/currentLongitude endpoint
            // Eats deliveries already push to, which is what
            // OrderService.getRiderLocation reads for the buyer's own "watch it
            // arrive" view.
            guard order.status == "SHIPPED" else { return }
            locationFetcher.requestLocation()
            locationFetcher.onLocation = { coordinate in
                Task { try? await RiderNetworkClient.shared.updateRiderLocation(latitude: coordinate.latitude, longitude: coordinate.longitude) }
            }
            while true {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                if order.status == "SHIPPED" { locationFetcher.requestLocation() }
            }
        }
    }

    private func complete() async {
        advancing = true
        defer { advancing = false }
        do {
            order = try await RiderNetworkClient.shared.completeCommerceDelivery(order.id).order
            onBack()
        } catch {
            self.error = "Couldn't mark this delivered. Try again."
        }
    }
}
