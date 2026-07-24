import SwiftUI
import CoreDesignSystem

private enum MerchantTab { case orders, catalog, register, reports }

struct MerchantHomeScreen: View {
    let merchant: MerchantDto
    let onLogout: () -> Void

    @State private var tab: MerchantTab = .orders

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Itunda Merchant").font(.title2).bold()
                    Text(merchant.businessName).font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                Button(action: { MerchantKeychainTokenStore.shared.clearSession(); onLogout() }) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                }
            }
            .padding(16)

            Picker("", selection: $tab) {
                Text("Orders").tag(MerchantTab.orders)
                Text("Catalog").tag(MerchantTab.catalog)
                Text("Register").tag(MerchantTab.register)
                Text("Reports").tag(MerchantTab.reports)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            switch tab {
            case .orders: OrdersTab()
            case .catalog: CatalogTab()
            case .register: PosTab()
            case .reports: ReportsTab()
            }
        }
    }
}

/// Real incoming Eats orders (restaurant side) -- previously only ever exposed in
/// the consumer app's own bank-mfe (a real structural gap: a merchant had no way to
/// manage incoming orders except through their buyer account). Restaurant-driven
/// statuses only: PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP.
private struct OrdersTab: View {
    @State private var orders: [EatsOrderDto]?
    @State private var error: String?

    var body: some View {
        Group {
            if let error {
                Text(error).foregroundColor(.red).padding(16)
            }
            if let orders {
                let active = orders.filter { ["PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP"].contains($0.status) }
                if active.isEmpty {
                    Spacer()
                    Text("No open orders right now.").foregroundColor(.secondary)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(active) { order in
                                OrderCard(order: order, onAdvanced: { Task { await refresh() } })
                            }
                        }
                        .padding(16)
                    }
                }
            } else {
                Spacer()
                ProgressView()
                Spacer()
            }
        }
        .task {
            await refresh()
            while true {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                await refresh()
            }
        }
    }

    private func refresh() async {
        do {
            orders = try await MerchantNetworkClient.shared.getRestaurantOrders().orders
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct OrderCard: View {
    let order: EatsOrderDto
    let onAdvanced: () -> Void

    @State private var busy = false
    @State private var error: String?

    private func nextAction(for status: String) -> (String, String)? {
        switch status {
        case "PLACED": return ("ACCEPTED", "Accept order")
        case "ACCEPTED": return ("PREPARING", "Start preparing")
        case "PREPARING": return ("READY_FOR_PICKUP", "Mark ready for pickup")
        default: return nil
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                MerchantStatusBadge(status: order.status)
                Spacer()
                Text("\(formattedRWF(order.totalAmount)) RWF").bold()
            }
            Text(order.deliveryAddress).font(.footnote)
            if let notes = order.deliveryNotes, !notes.isEmpty {
                Text("Note: \(notes)").font(.footnote)
            }
            if let error {
                Text(error).foregroundColor(.red).font(.caption)
            }
            if let (nextStatus, label) = nextAction(for: order.status) {
                Button(action: {
                    busy = true
                    Task {
                        do {
                            _ = try await MerchantNetworkClient.shared.advanceRestaurantOrderStatus(order.id, status: nextStatus)
                            onAdvanced()
                        } catch {
                            self.error = "Couldn't update this order. Try again."
                        }
                        busy = false
                    }
                }) {
                    Text(label).bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(busy)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

struct MerchantStatusBadge: View {
    let status: String

    private var label: String {
        switch status {
        case "PLACED": return "New order"
        case "ACCEPTED": return "Accepted"
        case "PREPARING": return "Preparing"
        case "READY_FOR_PICKUP": return "Ready for pickup"
        case "RIDER_ASSIGNED": return "Rider on the way"
        case "PICKED_UP": return "Out for delivery"
        case "DELIVERED": return "Delivered"
        case "CANCELLED": return "Cancelled"
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
