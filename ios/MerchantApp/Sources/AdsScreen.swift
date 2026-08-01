import SwiftUI
import CoreDesignSystem

private let AD_VALID_RADII_METERS = [300, 700, 1000, 1500]
private struct AdDurationTier { let days: Int; let price: Int }
private let AD_DURATION_TIERS = [AdDurationTier(days: 3, price: 1500), AdDurationTier(days: 7, price: 3000), AdDurationTier(days: 14, price: 5500)]

/// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 147) -- see
/// MerchantAdController.kt's own doc comment. merchant-mfe already has this
/// (AdsScreen.tsx); this is the first iOS client. A merchant's own registered
/// location is required first (the real "pull, not push" model MerchantAd.kt's own
/// doc comment establishes). Honest v1 scope-down: manual lat/lng entry rather than
/// real GPS capture, since ItundaMerchantApp has no location-permission plumbing yet
/// -- same class of scope-down RideScreen/VehicleInspectionScreen already use.
struct AdsTab: View {
    @State private var merchant: MerchantDto?
    @State private var ad: MerchantAdDto?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if let ad, isActive(ad) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Your active ad").font(.headline)
                        Text(ad.title).bold().font(.subheadline)
                        if let description = ad.description, !description.isEmpty {
                            Text(description).font(.footnote)
                        }
                        Text("\(ad.radiusMeters)m radius · runs until \(String(ad.activeUntil.prefix(10)))")
                            .font(.footnote).foregroundColor(.secondary)
                    }
                    .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground)).cornerRadius(12)
                }
                if let merchant {
                    if merchant.latitude == nil || merchant.longitude == nil {
                        LocationSetupCard(onSaved: { Task { await load() } })
                    } else {
                        CreateOrExtendAdCard(onCreated: { Task { await load() } })
                    }
                } else {
                    Text("Loading…").font(.footnote).foregroundColor(.secondary)
                }
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func isActive(_ ad: MerchantAdDto) -> Bool {
        guard let until = ISO8601DateFormatter().date(from: ad.activeUntil) ?? isoWithFraction(ad.activeUntil) else { return false }
        return until > Date()
    }

    private func isoWithFraction(_ s: String) -> Date? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.date(from: s)
    }

    private func load() async {
        error = nil
        do {
            merchant = try await MerchantNetworkClient.shared.getMyMerchant().merchant
        } catch {
            self.error = "Could not load your business account."
        }
        ad = try? await MerchantNetworkClient.shared.getMyAd().ad
    }
}

private struct LocationSetupCard: View {
    let onSaved: () -> Void

    @State private var latitude = ""
    @State private var longitude = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Set your business location").font(.headline)
            Text("A radius-targeted ad needs your business's real location to match nearby customers.")
                .font(.footnote).foregroundColor(.secondary)
            TextField("Latitude (-1.9536)", text: $latitude)
                .keyboardType(.numbersAndPunctuation)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
            TextField("Longitude (30.0605)", text: $longitude)
                .keyboardType(.numbersAndPunctuation)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            Button(action: { Task { await submit() } }) {
                Text(submitting ? "Saving…" : "Save location")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }

    private func submit() async {
        guard let lat = Double(latitude), let lng = Double(longitude) else {
            error = "Enter a real latitude and longitude."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await MerchantNetworkClient.shared.setMerchantLocation(SetMerchantLocationRequest(latitude: lat, longitude: lng))
            onSaved()
        } catch {
            self.error = "Could not save your location."
        }
    }
}

private struct CreateOrExtendAdCard: View {
    let onCreated: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var radiusMeters = AD_VALID_RADII_METERS[0]
    @State private var days = AD_DURATION_TIERS[0].days
    @State private var error: String?
    @State private var submitting = false

    private var selectedTier: AdDurationTier { AD_DURATION_TIERS.first { $0.days == days } ?? AD_DURATION_TIERS[0] }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Run a local ad").font(.headline)
            TextField("Title (e.g. Fresh bread every morning)", text: $title)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
            TextField("Description (optional)", text: $description)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
            Text("Radius").font(.footnote).foregroundColor(.secondary)
            Picker("Radius", selection: $radiusMeters) {
                ForEach(AD_VALID_RADII_METERS, id: \.self) { r in
                    Text(r >= 1000 ? String(format: "%.1fkm", Double(r) / 1000) : "\(r)m").tag(r)
                }
            }
            .pickerStyle(.segmented)
            Text("Duration").font(.footnote).foregroundColor(.secondary)
            Picker("Duration", selection: $days) {
                ForEach(AD_DURATION_TIERS, id: \.days) { t in
                    Text("\(t.days)d").tag(t.days)
                }
            }
            .pickerStyle(.segmented)
            Text("\(selectedTier.price) RWF will be charged from your wallet. If you already have an active ad, this extends it.")
                .font(.caption).foregroundColor(.secondary)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            Button(action: { Task { await submit() } }) {
                Text(submitting ? "Starting…" : "Pay \(selectedTier.price) RWF & run ad")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }

    private func submit() async {
        guard !title.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real title."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await MerchantNetworkClient.shared.createOrExtendAd(CreateAdRequest(
                title: title.trimmingCharacters(in: .whitespaces),
                description: description.trimmingCharacters(in: .whitespaces).isEmpty ? nil : description.trimmingCharacters(in: .whitespaces),
                radiusMeters: radiusMeters,
                days: days
            ))
            title = ""; description = ""
            onCreated()
        } catch {
            self.error = "Could not create this ad."
        }
    }
}
