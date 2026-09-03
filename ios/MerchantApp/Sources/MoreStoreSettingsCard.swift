import SwiftUI
import CoreDesignSystem

private let weekdayLabels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

/// Real merchant phone/hours/prep-time/pickup-discount/accepting-orders/closed-
/// weekdays settings -- ported from merchant-mfe (2026-09-03), found via a scripted
/// lib/merchant.ts parity scan: all six were fully built on the backend and wired on
/// merchant-mfe, with zero client on either native merchant app. Extracted into its
/// own file rather than growing StoreSettingsCard's own BusinessAccountScreen.swift,
/// which was already sitting exactly at its file-size-lint baseline.
struct MoreStoreSettingsCard: View {
    let merchant: MerchantDto
    let onUpdated: (MerchantDto) -> Void

    @State private var phoneNumber = ""
    @State private var openingHours = ""
    @State private var prepTime = ""
    @State private var pickupDiscount = ""
    @State private var closedWeekdays: Set<Int> = []
    @State private var busy = false
    @State private var error: String?
    @State private var saved = false
    @State private var acceptingBusy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Contact & schedule").bold()
            IdsTextField("Phone number", text: $phoneNumber).onChange(of: phoneNumber) { _ in saved = false }
            IdsTextField("Opening hours (e.g. Mon-Sat 8am-9pm)", text: $openingHours).onChange(of: openingHours) { _ in saved = false }
            IdsTextField("Average prep time (minutes)", text: $prepTime, keyboardType: .numberPad).onChange(of: prepTime) { _ in saved = false }
            // Real Baemin 포장할인 (pickup discount) -- a customer collecting their own
            // order skips the real delivery-fee cost, so merchants can pass some of
            // that saving back as a percent off.
            IdsTextField("Pickup discount % (blank = none)", text: $pickupDiscount, keyboardType: .numberPad).onChange(of: pickupDiscount) { _ in saved = false }

            Text("Closed on").bold().font(.subheadline)
            HStack(spacing: 6) {
                ForEach(Array(weekdayLabels.enumerated()), id: \.offset) { index, label in
                    let weekday = index + 1 // java.time.DayOfWeek numbering, 1=Monday
                    let selected = closedWeekdays.contains(weekday)
                    Button {
                        saved = false
                        if selected { closedWeekdays.remove(weekday) } else { closedWeekdays.insert(weekday) }
                    } label: {
                        Text(label)
                            .font(.caption2).bold()
                            .foregroundColor(selected ? .white : .primary)
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .background(selected ? IDS.Colors.brand : Color(.tertiarySystemBackground))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(PressScaleButtonStyle())
                }
            }
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            if saved {
                Text("Saved.").font(.footnote).foregroundColor(IDS.Colors.brand)
            }
            Button(action: { Task { await save() } }) {
                Text(busy ? "Saving…" : "Save")
                    .bold().foregroundColor(.white)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(IDS.Colors.brand).cornerRadius(8)
            }
            .disabled(busy)
            Divider()
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Accepting orders").bold().font(.subheadline)
                    Text("Temporarily pause your store without changing your hours.").font(.caption).foregroundColor(.secondary)
                }
                Spacer()
                Button(action: { Task { await toggleAccepting() } }) {
                    Text(acceptingBusy ? "…" : (merchant.isAcceptingOrders ? "On" : "Off"))
                        .bold().font(.caption)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                .disabled(acceptingBusy)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
        .onAppear {
            phoneNumber = merchant.phoneNumber ?? ""
            openingHours = merchant.openingHours ?? ""
            prepTime = merchant.avgPrepTimeMinutes.map { String($0) } ?? ""
            pickupDiscount = merchant.pickupDiscountPercent.map { String($0) } ?? ""
            closedWeekdays = Set((merchant.closedWeekdays ?? "").split(separator: ",").compactMap { Int($0) })
        }
    }

    private func save() async {
        let trimmedPrepTime = prepTime.trimmingCharacters(in: .whitespaces)
        let prepTimeValue: Int?
        if trimmedPrepTime.isEmpty {
            prepTimeValue = nil
        } else if let value = Int(trimmedPrepTime) {
            prepTimeValue = value
        } else {
            error = "Enter a real prep time."
            return
        }
        let trimmedPickupDiscount = pickupDiscount.trimmingCharacters(in: .whitespaces)
        let pickupDiscountValue: Int?
        if trimmedPickupDiscount.isEmpty {
            pickupDiscountValue = nil
        } else if let value = Int(trimmedPickupDiscount) {
            pickupDiscountValue = value
        } else {
            error = "Enter a real pickup discount percent."
            return
        }
        busy = true
        error = nil
        saved = false
        defer { busy = false }
        do {
            var updated = try await MerchantNetworkClient.shared.setMerchantPhoneNumber(phoneNumber.trimmingCharacters(in: .whitespaces).isEmpty ? nil : phoneNumber.trimmingCharacters(in: .whitespaces)).merchant
            updated = try await MerchantNetworkClient.shared.setMerchantOpeningHours(openingHours.trimmingCharacters(in: .whitespaces).isEmpty ? nil : openingHours.trimmingCharacters(in: .whitespaces)).merchant
            updated = try await MerchantNetworkClient.shared.setMerchantAvgPrepTimeMinutes(prepTimeValue).merchant
            updated = try await MerchantNetworkClient.shared.setMerchantPickupDiscount(pickupDiscountValue).merchant
            updated = try await MerchantNetworkClient.shared.setClosedWeekdays(closedWeekdays.sorted()).merchant
            onUpdated(updated)
            saved = true
        } catch {
            self.error = "Could not save."
        }
    }

    private func toggleAccepting() async {
        acceptingBusy = true
        defer { acceptingBusy = false }
        do {
            onUpdated(try await MerchantNetworkClient.shared.setAcceptingOrders(!merchant.isAcceptingOrders).merchant)
        } catch {
            self.error = "Could not save."
        }
    }
}
