import SwiftUI
import CoreDesignSystem

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

/// Real 배민오더-style table/QR in-store ordering, restaurant side (item 164) -- two
/// real, independent jobs on one tab: print/display a real per-table QR (top), and
/// watch + advance real incoming table orders (below), the same restaurant-driven-only
/// status chain (PLACED -> ACCEPTED -> PREPARING -> SERVED) OrdersTab's own Eats queue
/// already establishes, minus the rider-handoff step this order type never has.
/// Android's native merchantapp already has this (DineInScreen.kt); this is the iOS
/// port.
struct DineInTab: View {
    let restaurantId: String

    var body: some View {
        VStack(spacing: 0) {
            TableQrGeneratorView(restaurantId: restaurantId)
            Divider()
            DineInOrdersQueueView()
        }
    }
}

private struct TableQrGeneratorView: View {
    let restaurantId: String

    @State private var tableNumber = ""
    @State private var qrContent: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Table QR codes").bold()
            Text("Print this and leave it on a table -- a customer scans it to order straight to that table.")
                .font(.footnote).foregroundColor(.secondary)
            HStack {
                IdsTextField("Table number", text: $tableNumber)
                Button("Generate") { qrContent = dineInTableQrPayload(restaurantId: restaurantId, tableNumber: tableNumber.trimmingCharacters(in: .whitespaces)) }
                    .disabled(tableNumber.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            if let qrContent, let image = generateQrImage(content: qrContent, size: 200) {
                VStack {
                    image.resizable().interpolation(.none).frame(width: 200, height: 200)
                    Text("Table \(tableNumber)").bold()
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(16)
    }
}

private struct DineInOrdersQueueView: View {
    @State private var orders: [DineInOrderDto]?
    @State private var error: String?
    @State private var advancingId: String?

    private var active: [DineInOrderDto] {
        (orders ?? []).filter { ["PLACED", "ACCEPTED", "PREPARING"].contains($0.status) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Table orders").bold().padding(.horizontal, 16).padding(.top, 8)
            if let error {
                Text(error).foregroundColor(.red).font(.footnote).padding(.horizontal, 16)
            }
            if orders == nil {
                VStack { Spacer(); ProgressView(); Spacer() }.frame(maxWidth: .infinity)
            } else if active.isEmpty {
                VStack { Spacer(); Text("No open table orders right now.").foregroundColor(.secondary); Spacer() }
                    .frame(maxWidth: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(active) { order in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(statusLabel(order.status)).font(.caption).bold()
                                        .padding(.horizontal, 8).padding(.vertical, 2)
                                        .background(Color(.secondarySystemBackground)).cornerRadius(8)
                                    Spacer()
                                    Text("\(formatAmount(Int(order.totalAmount))) RWF").bold()
                                }
                                Text("Table \(order.tableNumber)").font(.subheadline).bold()
                                if let notes = order.notes, !notes.isEmpty {
                                    Text("Note: \(notes)").font(.footnote)
                                }
                                if let next = nextAction(order.status) {
                                    Button(action: { Task { await advance(order.id, to: next.0) } }) {
                                        Text(advancingId == order.id ? "Working…" : next.1)
                                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                                            .background(IDS.Colors.brand).foregroundColor(.white).cornerRadius(10)
                                    }
                                    .disabled(advancingId == order.id)
                                }
                            }
                            .padding(16)
                            .background(Color(.secondarySystemBackground))
                            .cornerRadius(12)
                        }
                    }
                    .padding(16)
                }
            }
        }
        .task {
            await refresh()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                await refresh()
            }
        }
    }

    private func refresh() async {
        do {
            orders = try await MerchantNetworkClient.shared.getDineInOrders().orders
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func advance(_ orderId: String, to status: String) async {
        advancingId = orderId
        defer { advancingId = nil }
        do {
            _ = try await MerchantNetworkClient.shared.advanceDineInOrderStatus(orderId, status: status)
            await refresh()
        } catch {
            self.error = "Couldn't update this order. Try again."
        }
    }

    private func nextAction(_ status: String) -> (String, String)? {
        switch status {
        case "PLACED": return ("ACCEPTED", "Accept order")
        case "ACCEPTED": return ("PREPARING", "Start preparing")
        case "PREPARING": return ("SERVED", "Mark served")
        default: return nil
        }
    }

    private func statusLabel(_ status: String) -> String {
        switch status {
        case "PLACED": return "New order"
        case "ACCEPTED": return "Accepted"
        case "PREPARING": return "Preparing"
        case "SERVED": return "Served"
        case "CANCELLED": return "Cancelled"
        default: return status
        }
    }
}
