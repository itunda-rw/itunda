import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real 당근카 (Karrot Vehicles) listing-creation fields (itunda Hood redesign,
// 2026-08-28) -- a new file since HoodMarketplace.swift/HoodMarketplaceCard.swift are
// both already near their 500-line new-file cap. A vehicle is still a regular Listing
// (see backend Listing.vehicleIsLeaseTakeover's own doc comment) -- this is purely the
// extra fields the create form shows/sends when a seller marks a listing as a vehicle,
// and the extra detail-card section shown when a listing carries them. Mirrors
// Android's MarketplaceVehicleFields.kt structure/copy exactly.

final class VehicleListingState: ObservableObject {
    @Published var isVehicle = false
    @Published var mileageKm = ""
    @Published var insuranceClaimCount = ""
    @Published var isLeaseTakeover = false
    @Published var leaseTotalAcquisitionCost = ""
    @Published var leaseRemainingMonths = ""
    @Published var leaseTotalMonths = ""
    @Published var leaseMonthlyPayment = ""
    @Published var leaseSubsidyAmount = ""
    @Published var leaseReturnFee = ""
}

private struct VehicleToggleChip: View {
    let label: String
    let active: Bool
    let action: () -> Void
    var body: some View {
        Text(label)
            .font(.caption).bold()
            .foregroundColor(active ? .white : IDS.Colors.textPrimary)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(active ? IDS.Colors.brand : IDS.Colors.chipBackground)
            .cornerRadius(999)
            .onTapGesture(perform: action)
    }
}

struct VehicleListingFieldsSection: View {
    @ObservedObject var state: VehicleListingState

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VehicleToggleChip(label: "🚗 This is a vehicle (당근카)", active: state.isVehicle) { state.isVehicle.toggle() }
            if state.isVehicle {
                HStack {
                    IdsTextField("Mileage (km)", text: $state.mileageKm, keyboardType: .numberPad)
                    IdsTextField("Insurance claims", text: $state.insuranceClaimCount, keyboardType: .numberPad)
                }
                VehicleToggleChip(label: "Lease takeover (렌트 승계)", active: state.isLeaseTakeover) { state.isLeaseTakeover.toggle() }
                if state.isLeaseTakeover {
                    HStack {
                        IdsTextField("Total acquisition cost", text: $state.leaseTotalAcquisitionCost, keyboardType: .numberPad)
                        IdsTextField("Monthly payment", text: $state.leaseMonthlyPayment, keyboardType: .numberPad)
                    }
                    HStack {
                        IdsTextField("Months remaining", text: $state.leaseRemainingMonths, keyboardType: .numberPad)
                        IdsTextField("Total lease months", text: $state.leaseTotalMonths, keyboardType: .numberPad)
                    }
                    HStack {
                        IdsTextField("Subsidy amount", text: $state.leaseSubsidyAmount, keyboardType: .numberPad)
                        IdsTextField("Return fee at end", text: $state.leaseReturnFee, keyboardType: .numberPad)
                    }
                }
            }
        }
    }
}

private struct VehicleInfoRow: View {
    let label: String
    let value: String
    var body: some View {
        HStack {
            Text(label).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            Spacer()
            Text(value).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
        }
    }
}

// Real lease-takeover cost breakdown shown on a vehicle listing's detail card --
// mirrors the reference's own 인수비/월 납입금/승계 지원금/만기후 반납 rows.
struct VehicleDetailSection: View {
    let listing: ListingDto

    var body: some View {
        if listing.vehicleMileageKm != nil || listing.vehicleInsuranceClaimCount != nil {
            VStack(alignment: .leading, spacing: 8) {
                Text("Vehicle info").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                if let mileage = listing.vehicleMileageKm { VehicleInfoRow(label: "Mileage", value: "\(mileage) km") }
                if let claims = listing.vehicleInsuranceClaimCount { VehicleInfoRow(label: "Insurance claims", value: "\(claims)") }
                if listing.vehicleIsLeaseTakeover == true {
                    Text("Lease takeover").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).padding(.top, 4)
                    if let cost = listing.leaseTotalAcquisitionCost { VehicleInfoRow(label: "Total acquisition cost", value: "\(Int(cost)) RWF") }
                    if let payment = listing.leaseMonthlyPayment { VehicleInfoRow(label: "Monthly payment", value: "\(Int(payment)) RWF") }
                    if let remaining = listing.leaseRemainingMonths, let total = listing.leaseTotalMonths {
                        VehicleInfoRow(label: "Remaining", value: "\(remaining) / \(total) months")
                    }
                    if let subsidy = listing.leaseSubsidyAmount { VehicleInfoRow(label: "Subsidy", value: "\(Int(subsidy)) RWF") }
                    if let returnFee = listing.leaseReturnFee { VehicleInfoRow(label: "Return fee at end", value: "\(Int(returnFee)) RWF") }
                }
            }
            .padding(12)
            .background(IDS.Colors.chipBackground)
            .cornerRadius(12)
        }
    }
}
