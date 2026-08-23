import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation


// Real Coupang-style post-delivery Return & Exchange request (반품/교환 신청) (item 166/175)
// -- see OrderReturnService's own doc comment for the full account: a real 7-day window
// from delivery, an approved RETURN triggers a real refund via reversed ledger legs, an
// approved EXCHANGE moves no money. Merchant-side approve/reject queue has zero iOS
// client anywhere (Shop has no merchant order-management screen on this platform at all,
// same gap as Android) -- a real, separate, not-yet-started gap; this is the buyer-side
// request form only, mirroring bank-mfe's (item 166) and Android's (item 174) own shape.
struct ReturnExchangeAction: View {
    let orderId: String

    @State private var open = false
    @State private var done = false
    @State private var type = "RETURN"
    @State private var reasonCode = orderReturnReasonCodes[0]
    @State private var note = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("Return/exchange requested -- the seller will review it.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Return or exchange").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    ForEach([("RETURN", "Return"), ("EXCHANGE", "Exchange")], id: \.0) { value, label in
                        Button(action: { type = value }) {
                            Text(label).font(.caption).bold().foregroundColor(type == value ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .background(type == value ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(12)
                        }
                    }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(orderReturnReasonCodes, id: \.self) { code in
                            Button(action: { reasonCode = code }) {
                                Text(code).font(IDS.scaledFont(size: 11, weight: .regular, relativeTo: .caption2)).foregroundColor(reasonCode == code ? .white : IDS.Colors.textPrimary)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(reasonCode == code ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(12)
                            }
                        }
                    }
                }
                TextField("Add a note (optional)", text: $note)
                    .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                HStack(spacing: 10) {
                    Button(action: { open = false }) {
                        Text("Cancel").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.chipBackground).cornerRadius(12)
                    }
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "Submitting…" : "Submit request").font(.subheadline).bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(submitting ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.requestOrderReturn(
                orderId: orderId, type: type, reasonCode: reasonCode,
                reasonNote: note.trimmingCharacters(in: .whitespaces).isEmpty ? nil : note
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

struct MyReturnRequestsView: View {
    @State private var requests: [OrderReturnRequestDto]?

    private static let statusLabel = ["REQUESTED": "Pending review", "APPROVED": "Approved", "REJECTED": "Rejected"]

    var body: some View {
        Group {
            if let requests, !requests.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("My return/exchange requests").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(requests) { r in
                        HStack {
                            Text(r.type == "RETURN" ? "Return" : "Exchange").font(.subheadline).bold()
                            Spacer()
                            Text(Self.statusLabel[r.status] ?? r.status)
                                .font(.caption).bold()
                                .foregroundColor(r.status == "APPROVED" ? IDS.Colors.brand : r.status == "REJECTED" ? .red : IDS.Colors.textSecondary)
                        }
                        .padding(14).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                }
            }
        }
        .task {
            do {
                requests = try await NetworkClient.shared.getMyReturnRequests().returnRequests
            } catch {
                requests = []
            }
        }
    }
}

