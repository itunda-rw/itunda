import SwiftUI
import CoreDesignSystem
import CoreNetwork

private enum GroupOrderMode { case closed, create, joinScan, joinManual, active }

// Real 배달의민족 함께주문 (Baemin "Together Order") -- ported from bank-mfe/Android
// (2026-09-03), see GroupEatsOrderDto's own doc comment on the network layer for the
// full sourced account. A join-code-shared cart in front of the same real
// checkout/payment path the normal single-buyer order flow uses. Reuses the same
// QR-generate/scan-first join convention OpenChatCard already established
// (docs/UI_UX_GUIDELINES.md's own "no manual-code UX" rule) rather than inventing a
// second one.
struct GroupEatsOrderView: View {
    let restaurants: [ShoppingMerchantDto]?

    @State private var mode: GroupOrderMode = .closed
    @State private var groupOrderId: String?
    @State private var detail: GroupEatsOrderDetailResponse?
    @State private var menu: [MerchantProductDto] = []
    @State private var restaurantId = ""
    @State private var address = ""
    @State private var joinCode = ""
    @State private var qrImage: UIImage?
    @State private var scanUnavailable = false
    @State private var busy = false
    @State private var error: String?
    @State private var placedOrder: EatsOrderDto?

    private var currentUserId: String? { KeychainTokenStore.shared.getUserId() }

    var body: some View {
        switch mode {
        case .closed:
            HStack(spacing: 10) {
                Button(action: { mode = .create }) {
                    Text("Start together order").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
                }
                Button(action: { mode = .joinScan }) {
                    Text("Join together order").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
                }
            }

        case .create:
            VStack(alignment: .leading, spacing: 10) {
                Text("Start a together order").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                Text("Share one restaurant's cart with friends -- everyone adds their own items, you place one real order, and itunda asks each of them for their own share afterward.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(restaurants ?? []) { r in
                            let selected = r.merchantId == restaurantId
                            Text(r.businessName).font(.subheadline).bold()
                                .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .background(selected ? IDS.Colors.brand : IDS.Colors.card).clipShape(Capsule())
                                .onTapGesture { restaurantId = r.merchantId }
                        }
                    }
                }
                IdsTextField("Delivery address", text: $address)
                HStack(spacing: 10) {
                    Button(action: { mode = .closed }) {
                        Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
                    }
                    Button(action: { Task { await createGroupOrder() } }) {
                        Text(busy ? "Starting…" : "Start").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(restaurantId.isEmpty || address.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(busy || restaurantId.isEmpty || address.isEmpty)
                }
                if let error { Text(error).font(.caption).foregroundColor(.red) }
            }

        case .joinScan:
            VStack(alignment: .leading, spacing: 10) {
                Text("Scan to join").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                if !scanUnavailable && !busy {
                    QrScanCameraView(onDetect: { raw in Task { await submitJoinCode(parseQrParam(raw, key: "code")) } }, onUnavailable: { scanUnavailable = true })
                        .frame(height: 220).cornerRadius(12).clipped()
                }
                if busy { Text("Joining…").font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                HStack(spacing: 10) {
                    Button(action: { mode = .closed }) {
                        Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
                    }
                    Button(action: { mode = .joinManual }) {
                        Text(scanUnavailable ? "Enter code manually" : "No camera? Enter code").bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
                    }
                }
                if let error { Text(error).font(.caption).foregroundColor(.red) }
            }

        case .joinManual:
            VStack(alignment: .leading, spacing: 10) {
                Text("Join by code").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                IdsTextField("Join code", text: Binding(get: { joinCode }, set: { joinCode = $0.uppercased() }))
                HStack(spacing: 10) {
                    Button(action: { mode = .closed }) {
                        Text("Cancel").bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
                    }
                    Button(action: { Task { await submitJoinCode(joinCode) } }) {
                        Text(busy ? "Joining…" : "Join").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(joinCode.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(busy || joinCode.isEmpty)
                }
                if let error { Text(error).font(.caption).foregroundColor(.red) }
            }

        case .active:
            if let placedOrder {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Order placed").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Real order #\(placedOrder.id.suffix(8)) placed for \(Int(placedOrder.totalAmount).formatted()) RWF. Every other participant with items in the cart has been sent a real Dutch-pay request via Split Bill.")
                        .font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                }
            } else {
                activeBody
            }
        }
    }

    private var activeBody: some View {
        let order = detail?.groupOrder
        let isHost = order != nil && currentUserId != nil && order?.hostUserId == currentUserId
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                if let qrImage { Image(uiImage: qrImage).interpolation(.none).resizable().frame(width: 72, height: 72).cornerRadius(8) }
                VStack(alignment: .leading, spacing: 2) {
                    Text("Order together -- \(order?.joinCode ?? "")").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Send friends a link to join instantly, or let someone nearby scan the code.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
            }
            Button(action: { shareJoinCode(order?.joinCode ?? "") }) {
                Text("Share join code").font(.subheadline).bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12).background(IDS.Colors.brand).cornerRadius(12)
            }

            Text("Add your own item").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            ForEach(menu) { item in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Text("\(Int(item.price).formatted()) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Button("Add") { Task { await addItem(item.id) } }.font(.subheadline).bold().foregroundColor(IDS.Colors.brand).disabled(busy)
                }
            }

            Text("Everyone's items -- \(Int(detail?.grandTotal ?? 0).formatted()) RWF total").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            ForEach(detail?.participants ?? []) { p in
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(p.userId == order?.hostUserId ? "Host" : "Participant") -- \(Int(p.subtotal).formatted()) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    if p.items.isEmpty {
                        Text("No items yet").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(p.items) { i in
                            Text("\(i.quantity)x \(i.productName) -- \(Int(i.lineTotal).formatted()) RWF").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                }
            }
            Button(action: { Task { await refresh() } }) {
                Text("Refresh").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                    .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
            }

            if isHost {
                Button(action: { Task { await finalizeOrder() } }) {
                    Text(busy ? "…" : "Place the real order").font(.subheadline).bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background((detail?.grandTotal ?? 0) <= 0 ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                }
                .disabled(busy || (detail?.grandTotal ?? 0) <= 0)
                Button(action: { Task { await cancelOrder() } }) {
                    Text("Cancel together order").font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                        .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color(.tertiarySystemBackground)).cornerRadius(12)
                }
                .disabled(busy)
            }
            if let error { Text(error).font(.caption).foregroundColor(.red) }
        }
    }

    private func createGroupOrder() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            let res = try await NetworkClient.shared.createGroupEatsOrder(restaurantId: restaurantId, deliveryAddress: address.trimmingCharacters(in: .whitespaces))
            await openOrder(res.groupOrder)
        } catch {
            self.error = "Could not start this together order."
        }
    }

    private func submitJoinCode(_ raw: String) async {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        busy = true; error = nil
        defer { busy = false }
        do {
            let res = try await NetworkClient.shared.joinGroupEatsOrder(joinCode: trimmed)
            await openOrder(res.groupOrder)
        } catch {
            self.error = "No together order found for this code."
        }
    }

    private func openOrder(_ order: GroupEatsOrderDto) async {
        groupOrderId = order.id
        qrImage = generateQrImage(from: "itunda://join-eats?code=\(order.joinCode)", size: 280)
        mode = .active
        await refresh()
        menu = (try? await NetworkClient.shared.getMerchantProducts(merchantId: order.restaurantId).products) ?? []
    }

    private func refresh() async {
        guard let groupOrderId else { return }
        detail = try? await NetworkClient.shared.getGroupEatsOrder(id: groupOrderId)
    }

    private func addItem(_ menuItemId: String) async {
        guard let groupOrderId else { return }
        busy = true
        defer { busy = false }
        do {
            detail = try await NetworkClient.shared.setGroupEatsOrderItems(id: groupOrderId, items: [GroupEatsOrderItemRequest(menuItemId: menuItemId, quantity: 1)])
        } catch {
            self.error = "Could not add this item."
        }
    }

    private func finalizeOrder() async {
        guard let groupOrderId else { return }
        busy = true; error = nil
        defer { busy = false }
        do {
            let res = try await NetworkClient.shared.finalizeGroupEatsOrder(id: groupOrderId)
            placedOrder = res.order
        } catch {
            self.error = "Could not place this order."
        }
    }

    private func cancelOrder() async {
        guard let groupOrderId else { return }
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.cancelGroupEatsOrder(id: groupOrderId)
            self.groupOrderId = nil
            detail = nil
            mode = .closed
        } catch {
            self.error = "Could not cancel this together order."
        }
    }

    private func shareJoinCode(_ code: String) {
        let text = "Order together on itunda -- use code \(code) in the Eats tab."
        let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.windows.first?.rootViewController {
            var top = root
            while let presented = top.presentedViewController { top = presented }
            top.present(activityVC, animated: true)
        }
    }
}
