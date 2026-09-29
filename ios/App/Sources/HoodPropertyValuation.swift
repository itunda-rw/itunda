import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Extracted from HoodProperty.swift (2026-09-09, file-size-lint extraction -- that
// file crossed 500 lines for the first time while adding the pagination-discard
// fix). PropertyValuationCard is a genuinely self-contained tool (its own real
// backend call, its own local form state) only ever shown from the Valuation tab,
// not tightly coupled to PropertyContent's own browse/mine/acquired/neighborhood
// state -- a real, natural feature boundary, not just line-shaving.

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see the backend's
// PropertyListingService.estimateValue doc comment. Read-only: enter a location + size,
// get a real comparable-listings-based estimate, nothing persisted. bank-mfe/Android
// already have this; this is the first iOS client.
struct PropertyValuationCard: View {
    let propertyTypes: [PropertyTypeDto]

    @State private var latitude = ""
    @State private var longitude = ""
    @State private var propertyType: String?
    @State private var listingType = "SALE"
    @State private var sizeSqm = ""
    @State private var estimate: PropertyValuationEstimateDto?
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("우리집 시세 — Estimate my home's value").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text("A real estimate based on comparable listings near you, not a fabricated number.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            IdsTextField("Latitude", text: $latitude, keyboardType: .decimalPad)
            IdsTextField("Longitude", text: $longitude, keyboardType: .decimalPad)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(propertyTypes) { t in
                        let active = propertyType == t.id
                        Text(t.label)
                            .font(.caption).bold()
                            .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(active ? IDS.Colors.brand : Color.clear)
                            .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                            .cornerRadius(999)
                            .onTapGesture { propertyType = t.id }
                    }
                }
            }
            HStack(spacing: 6) {
                ForEach([("SALE", "For sale"), ("RENT", "For rent")], id: \.0) { v, label in
                    let active = listingType == v
                    Text(label)
                        .font(.caption).bold()
                        .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(active ? IDS.Colors.brand : Color.clear)
                        .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                        .cornerRadius(999)
                        .onTapGesture { listingType = v }
                }
            }
            IdsTextField("Size (sqm)", text: $sizeSqm, keyboardType: .decimalPad)
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            Button(action: { Task { await estimateValue() } }) {
                Text(loading ? "Estimating…" : "Estimate value")
                    .font(.caption).bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(loading)
            if let estimate {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(formatAmount(Int(estimate.estimatedValue))) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Based on \(estimate.comparableCount) comparable listings within \(Int(estimate.radiusKm)) km (\(formatAmount(Int(estimate.averagePricePerSqm))) RWF/sqm avg)")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
            }
        }
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- this
        // screen's own main content (the whole "Valuation" tab).
        .padding(.vertical, 10)
    }

    private func estimateValue() async {
        guard let propertyType, let lat = Double(latitude), let lng = Double(longitude), let size = Double(sizeSqm), size > 0 else {
            error = "Fill in a real location, property type, and size."
            return
        }
        loading = true
        error = nil
        estimate = nil
        defer { loading = false }
        do {
            estimate = try await NetworkClient.shared.getPropertyValuation(latitude: lat, longitude: lng, propertyType: propertyType, listingType: listingType, sizeSqm: size).estimate
        } catch let NetworkError.httpError(statusCode) where statusCode == 422 {
            error = "Not enough comparable listings nearby to estimate a value."
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
