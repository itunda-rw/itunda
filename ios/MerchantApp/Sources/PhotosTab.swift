import SwiftUI
import CoreDesignSystem

// Real gap found live (uncalled-endpoint sweep, 2026-08-29/30): MerchantService.
// setPhotoUrls (a real, up-to-20-photo gallery, distinct from the older single cover
// photo this app already edits in BusinessAccountScreen.swift) has been live since the
// itunda Maps redesign, and merchant-mfe/Android merchantapp already have this real UI
// -- this is the native-iOS-merchant-app port.
//
// Real wire-shape gotcha ALREADY found + fixed once porting this feature to
// merchant-mfe (2026-08-29): Merchant.photoUrls comes back from the backend as a
// single comma-joined STRING (matching the raw JPA column), not a JSON array -- only
// the composed GET /maps/places/{merchantId} splits it server-side. This screen
// fetches its OWN merchant via getMyMerchant() and splits it the same way, rather
// than trusting an array shape.
struct PhotosTab: View {
    @State private var urls: [String] = [""]
    @State private var saving = false
    @State private var error: String?
    @State private var saved = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Photo gallery").font(.headline)
                Text("Up to 20 photos — customers see these on your Maps page.")
                    .font(.caption).foregroundColor(.secondary)
                if let error { Text(error).font(.caption).foregroundColor(.red) }
                if saved { Text("Saved.").font(.caption).foregroundColor(.accentColor) }

                ForEach(urls.indices, id: \.self) { index in
                    HStack {
                        IdsTextField("Photo URL", text: Binding(
                            get: { urls[index] },
                            set: { urls[index] = $0 }
                        ))
                        Button(action: { urls.remove(at: index) }) {
                            Text("Remove").font(.caption).foregroundColor(.red)
                        }
                    }
                }

                HStack(spacing: 8) {
                    Button(action: { urls.append("") }) {
                        Text("Add photo").font(.caption).bold()
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Color(.systemGray5)).cornerRadius(8)
                    }
                    .disabled(urls.count >= 20)
                    Button(action: { Task { await save() } }) {
                        Text(saving ? "Saving…" : "Save").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .background(Color.accentColor).cornerRadius(10)
                    }
                    .disabled(saving)
                }
            }
            .padding(16)
        }
        .task { await load() }
    }

    private func load() async {
        guard let merchant = try? await MerchantNetworkClient.shared.getMyMerchant().merchant else { return }
        let existing = (merchant.photoUrls ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if !existing.isEmpty { urls = existing }
    }

    private func save() async {
        saving = true
        error = nil
        saved = false
        do {
            _ = try await MerchantNetworkClient.shared.setMerchantPhotoUrls(urls.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
            saved = true
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not save your photos."
        } catch {
            self.error = "Could not save your photos."
        }
        saving = false
    }
}
