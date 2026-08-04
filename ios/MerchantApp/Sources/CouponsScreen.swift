import SwiftUI
import CoreDesignSystem

/// Real merchant coupons + 단골 (regular customer) loyalty gating -- see
/// rw.itunda.merchant.MerchantCouponService's own doc comment. Merchant-owner-facing
/// create/list/deactivate half only; a coupon redeems against a real Pay-by-code
/// payment, not here. merchant-mfe/Android already have this; this is the first iOS
/// client.
struct CouponsTab: View {
    @State private var coupons: [MerchantCouponDto]?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                CreateCouponCard(onCreated: { Task { await load() } })

                VStack(alignment: .leading, spacing: 8) {
                    Text("Your coupons").font(.headline)
                    Text("A customer applies a coupon when paying by code — it's redeemed once per customer.")
                        .font(.footnote).foregroundColor(.secondary)
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    if let coupons {
                        if coupons.isEmpty {
                            Text("No coupons yet.").font(.footnote).foregroundColor(.secondary)
                        } else {
                            ForEach(coupons) { coupon in
                                CouponRow(coupon: coupon, onChanged: { Task { await load() } })
                            }
                        }
                    } else {
                        Text("Loading…").font(.footnote).foregroundColor(.secondary)
                    }
                }
                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground)).cornerRadius(12)
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        error = nil
        do {
            coupons = try await MerchantNetworkClient.shared.getMyCoupons().coupons
        } catch {
            self.error = "Could not load your coupons."
        }
    }
}

private struct CreateCouponCard: View {
    let onCreated: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var percentType = true
    @State private var discountValue = ""
    @State private var regularsOnly = false
    @State private var expiresAt = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Create a coupon").font(.headline)
            IdsTextField("Title (e.g. 10% off your next visit)", text: $title)
            IdsTextField("Description (optional)", text: $description)
            Picker("Discount type", selection: $percentType) {
                Text("Percent off").tag(true)
                Text("Fixed amount off").tag(false)
            }
            .pickerStyle(.segmented)
            IdsTextField(percentType ? "Percent (1-100)" : "Amount (RWF)", text: $discountValue, keyboardType: .decimalPad)
            IdsTextField("Expires (YYYY-MM-DD, optional)", text: $expiresAt)
            Toggle("Reserve for regular customers only (3+ past payments)", isOn: $regularsOnly)
                .font(.footnote)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            Button(action: { Task { await submit() } }) {
                Text(submitting ? "Creating…" : "Create coupon")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }

    private func submit() async {
        guard let value = Double(discountValue), value > 0, !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real title and discount value."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        let trimmedExpiry = expiresAt.trimmingCharacters(in: .whitespaces)
        do {
            _ = try await MerchantNetworkClient.shared.createCoupon(CreateCouponRequest(
                title: title.trimmingCharacters(in: .whitespaces),
                description: description.trimmingCharacters(in: .whitespaces).isEmpty ? nil : description.trimmingCharacters(in: .whitespaces),
                discountType: percentType ? "PERCENT" : "FIXED_AMOUNT",
                discountValue: value,
                regularsOnly: regularsOnly,
                expiresAt: trimmedExpiry.isEmpty ? nil : "\(trimmedExpiry)T00:00:00Z"
            ))
            title = ""; description = ""; discountValue = ""; regularsOnly = false; expiresAt = ""
            onCreated()
        } catch {
            self.error = "Could not create this coupon."
        }
    }
}

private struct CouponRow: View {
    let coupon: MerchantCouponDto
    let onChanged: () -> Void

    @State private var deactivating = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(coupon.title).bold().font(.subheadline)
                Spacer()
                Text(coupon.active ? "Active" : "Deactivated")
                    .font(.caption).bold()
                    .foregroundColor(coupon.active ? IDS.Colors.brand : .secondary)
            }
            if let description = coupon.description, !description.isEmpty {
                Text(description).font(.footnote)
            }
            Text(discountLabel).font(.footnote).foregroundColor(.secondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if coupon.active {
                Button(action: { Task { await deactivate() } }) {
                    Text(deactivating ? "…" : "Deactivate").bold().font(.caption)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                .disabled(deactivating)
            }
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
    }

    private var discountLabel: String {
        var label = coupon.discountType == "PERCENT" ? "\(Int(coupon.discountValue))% off" : "\(Int(coupon.discountValue)) RWF off"
        if coupon.regularsOnly { label += " · Regulars only" }
        if let expiresAt = coupon.expiresAt { label += " · Expires \(String(expiresAt.prefix(10)))" }
        return label
    }

    private func deactivate() async {
        deactivating = true
        error = nil
        defer { deactivating = false }
        do {
            _ = try await MerchantNetworkClient.shared.deactivateCoupon(coupon.id)
            onChanged()
        } catch {
            self.error = "Could not deactivate this coupon."
        }
    }
}
