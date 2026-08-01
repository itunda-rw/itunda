import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
/// rw.itunda.vehicle.VehicleValuationService's own doc comment for the full sourced
/// account and honest scope boundary: no real used-car pricing database partnership
/// exists here, this is itunda's own documented general depreciation estimate.
/// bank-mfe/Android already have this; this is the first iOS client. Mirrors bank-mfe's
/// own register/list/valuation/remove flow, including its own "Update km" action
/// (bank-mfe uses a raw browser `window.prompt`, ported here as a real `.alert` with a
/// text field instead -- found real on Android's own `ApiService.kt` but never actually
/// wired to a screen there either, closed on both native platforms together).
struct VehicleValuationScreenView: View {
    var onBack: () -> Void = {}

    @State private var vehicles: [VehicleDto] = []
    @State private var valuations: [String: VehicleValuationDto] = [:]
    @State private var showCreate = false
    @State private var make = ""
    @State private var model = ""
    @State private var modelYear = String(Calendar.current.component(.year, from: Date()))
    @State private var purchasePrice = ""
    @State private var purchaseDate = ""
    @State private var mileageKm = ""
    @State private var busy = false
    @State private var busyId: String?
    @State private var error: String?
    @State private var editingMileageVehicle: VehicleDto?
    @State private var editMileageText = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("My vehicles").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top) {
                        Text("Estimated resale value based on age and mileage — itunda's own general estimate, not a market comp.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        Spacer()
                        Button(action: { showCreate.toggle() }) {
                            Text(showCreate ? "Cancel" : "+ Add").bold().font(.caption)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(Color(.secondarySystemBackground)).cornerRadius(8)
                        }
                    }

                    if showCreate {
                        VStack(spacing: 8) {
                            TextField("Make (e.g. Toyota)", text: $make)
                                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
                            TextField("Model (e.g. RAV4)", text: $model)
                                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
                            TextField("Model year", text: $modelYear)
                                .keyboardType(.numberPad)
                                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
                            TextField("Purchase price (RWF)", text: $purchasePrice)
                                .keyboardType(.decimalPad)
                                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
                            TextField("Purchase date (YYYY-MM-DD)", text: $purchaseDate)
                                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
                            TextField("Current mileage (km)", text: $mileageKm)
                                .keyboardType(.numberPad)
                                .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
                            if let error {
                                Text(error).font(.caption).foregroundColor(.red)
                            }
                            Button(action: { Task { await register() } }) {
                                Text(busy ? "Adding…" : "Add vehicle").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busy)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    } else if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }

                    if vehicles.isEmpty {
                        Text("No vehicles added yet.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(vehicles) { v in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(v.modelYear) \(v.make) \(v.model)").bold().font(.subheadline)
                                        if let valuation = valuations[v.id] {
                                            Text("\(Int(v.mileageKm)) km · \(valuation.ageYears) \(valuation.ageYears == 1 ? "year" : "years") old · expected \(valuation.expectedMileageKm) km")
                                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        } else {
                                            Text("\(Int(v.mileageKm)) km").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                        }
                                    }
                                    Spacer()
                                    Button(action: { editingMileageVehicle = v; editMileageText = String(v.mileageKm) }) {
                                        Text("Update km").bold().font(.caption)
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                    }
                                    Button(action: { Task { await remove(v.id) } }) {
                                        Text(busyId == v.id ? "…" : "Remove").bold().font(.caption)
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                    }
                                    .disabled(busyId == v.id)
                                }
                                if let valuation = valuations[v.id] {
                                    HStack(spacing: 12) {
                                        Text("Now: \(Int(valuation.currentEstimatedValue))").font(.caption2).bold()
                                        Text("+1y: \(Int(valuation.estimatedValueIn1Year))").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                        Text("+2y: \(Int(valuation.estimatedValueIn2Years))").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                        Text("+3y: \(Int(valuation.estimatedValueIn3Years))").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                }
                            }
                            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground)).cornerRadius(12)
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
        .alert("Update mileage", isPresented: Binding(get: { editingMileageVehicle != nil }, set: { if !$0 { editingMileageVehicle = nil } })) {
            TextField("Current mileage (km)", text: $editMileageText).keyboardType(.numberPad)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                if let v = editingMileageVehicle, let mileage = Int(editMileageText), mileage >= 0 {
                    Task { await updateMileage(v.id, mileage) }
                }
            }
        }
    }

    private func load() async {
        do {
            let list = try await NetworkClient.shared.getMyVehicles().vehicles
            vehicles = list
            var results: [String: VehicleValuationDto] = [:]
            for v in list {
                if let valuation = try? await NetworkClient.shared.getVehicleValuation(v.id).valuation {
                    results[v.id] = valuation
                }
            }
            valuations = results
        } catch {
            vehicles = []
        }
    }

    private func register() async {
        guard !make.isEmpty, !model.isEmpty,
              let price = Double(purchasePrice), price > 0,
              let year = Int(modelYear), !purchaseDate.isEmpty,
              let mileage = Int(mileageKm), mileage >= 0 else {
            error = "Fill in every field with a real value."
            return
        }
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.registerVehicle(make: make.trimmingCharacters(in: .whitespaces), model: model.trimmingCharacters(in: .whitespaces), modelYear: year, purchasePrice: price, purchaseDate: purchaseDate.trimmingCharacters(in: .whitespaces), mileageKm: mileage)
            make = ""; model = ""; purchasePrice = ""; purchaseDate = ""; mileageKm = ""
            showCreate = false
            await load()
        } catch {
            self.error = "Could not register this vehicle."
        }
    }

    private func updateMileage(_ id: String, _ mileage: Int) async {
        do {
            _ = try await NetworkClient.shared.updateVehicleMileage(id, mileageKm: mileage)
            editingMileageVehicle = nil
            await load()
        } catch {
            self.error = "Could not update mileage."
        }
    }

    private func remove(_ id: String) async {
        busyId = id
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.removeVehicle(id)
            await load()
        } catch {
            self.error = "Could not remove this vehicle."
        }
    }
}
