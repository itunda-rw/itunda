import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork


// Hood tab entry point + shared helpers (image picker, radius control,
// neighborhood-setup prompt, skeleton). Domain content lives in
// HoodMarketplace*.swift / HoodCommunity*.swift / HoodJobs*.swift /
// HoodProperty*.swift (split 2026-08-19 for real file-size decomposition).

/// Real photo/document picker (2026-08-01) -- UIImagePickerController wrapped for
/// SwiftUI rather than the iOS-16-only PhotosPicker, since this project's deployment
/// target isn't pinned to 16+ anywhere and this is the app's first photo-picker use.
/// Used by PropertyListingCard's real ownership-verification upload; a plain, reusable
/// callback-based wrapper if another flow needs a real picker later.
struct ImagePickerView: UIViewControllerRepresentable {
    let onPicked: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .photoLibrary
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onPicked: (UIImage?) -> Void
        init(onPicked: @escaping (UIImage?) -> Void) { self.onPicked = onPicked }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            onPicked(info[.originalImage] as? UIImage)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onPicked(nil)
        }
    }
}

/// Real device-location fetch, shared by NewListingForm's "share my location" toggle and
/// ListingCard's "directions to this seller" -- same runtime-permission-gated
/// CLLocationManager technique MapScreenView.swift's own LocationFetcher already
/// established.
final class HoodLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var errorMessage: String?
    private let manager = CLLocationManager()
    var onLocation: ((CLLocationCoordinate2D) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
    }

    func requestLocation() {
        errorMessage = nil
        let status = manager.authorizationStatus
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if status == .denied || status == .restricted {
            errorMessage = "Location permission was denied."
        } else {
            manager.requestLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else { return }
        self.coordinate = coordinate
        onLocation?(coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        errorMessage = "Could not access your real location right now."
    }
}

/// Freshness is a practical trust signal in a local marketplace: it distinguishes a
/// current offer from a stale listing without inventing any reputation data.
func hoodRelativeTime(_ isoTimestamp: String) -> String {
    let standard = ISO8601DateFormatter()
    standard.formatOptions = [.withInternetDateTime]
    let fractional = ISO8601DateFormatter()
    fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let date = fractional.date(from: isoTimestamp) ?? standard.date(from: isoTimestamp) else { return "" }
    let seconds = max(0, Date().timeIntervalSince(date))
    switch seconds {
    case ..<60: return "Just now"
    case ..<3_600: return "\(Int(seconds / 60))m ago"
    case ..<86_400: return "\(Int(seconds / 3_600))h ago"
    case ..<604_800: return "\(Int(seconds / 86_400))d ago"
    default: return "\(Int(seconds / 604_800))w ago"
    }
}

// Real 당근모임-style structured event date display (2026-07-25 backend/Android;
// ported to iOS 2026-07-28) -- falls back to the raw ISO string on any parse failure,
// never a fabricated date.
func formatMeetupDate(_ iso: String) -> String {
    guard let date = ISO8601DateFormatter().date(from: iso) else { return iso }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
    guard let year = components.year, let month = components.month, let day = components.day,
          let hour = components.hour, let minute = components.minute else { return iso }
    return String(format: "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
}

/// The selected distance is sent to the existing nearby endpoints; it is not a
/// cosmetic filter. Small preset choices keep the control usable on a phone.
struct HoodRadiusControl: View {
    @Binding var radiusKm: Double
    let onChanged: () -> Void
    var body: some View {
        HStack(spacing: 6) {
            ForEach([1.0, 3.0, 5.0, 10.0], id: \.self) { radius in
                let selected = radiusKm == radius
                Button("\(Int(radius)) km") { radiusKm = radius; onChanged() }
                    .font(.caption).bold().foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(999)
            }
            Spacer()
        }
    }
}

/// Real hyperlocal neighborhood setup (2026-07-20) -- shared across every Hood-mode
/// content view (Marketplace/Community/Jobs/Property), mirroring bank-mfe's
/// NeighborhoodSetupPrompt and Android's own composable of the same name exactly.
/// Reuses HoodLocationFetcher, the same real CLLocationManager wrapper
/// NewListingForm's own "share my location" already established -- one location
/// permission flow, not a second one invented for this.
struct NeighborhoodSetupPrompt: View {
    // Real second neighborhood (2026-08-04) -- see NetworkClient.setSecondNeighborhood's
    // own doc comment; mirrors Android HoodShared.kt's own isSecond param and copy exactly.
    var isSecond: Bool = false
    let onDone: (String) -> Void

    @State private var busy = false
    @State private var error: String?
    @StateObject private var locationFetcher = HoodLocationFetcher()

    var body: some View {
        VStack(spacing: 8) {
            Text(isSecond ? "Add a second neighborhood" : "Set your neighborhood").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text(isSecond ? "Share a second real place -- like work -- to see what's happening there too." : "Share your real location once to see what's happening near you.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary).multilineTextAlignment(.center)
            Button(action: { if !busy { locationFetcher.requestLocation() } }) {
                Text(busy ? "Finding your neighborhood…" : "📍 Share my location")
                    .font(.subheadline).bold().foregroundColor(.white)
                    .padding(.horizontal, 20).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            .disabled(busy)
            if let error {
                IdsErrorText(error)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(28)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                busy = true
                Task {
                    do {
                        let res = isSecond
                            ? try await NetworkClient.shared.setSecondNeighborhood(latitude: coordinate.latitude, longitude: coordinate.longitude)
                            : try await NetworkClient.shared.setNeighborhood(latitude: coordinate.latitude, longitude: coordinate.longitude)
                        busy = false
                        let value = isSecond ? res.user.secondNeighborhood : res.user.neighborhood
                        if let value { onDone(value) }
                    } catch let NetworkError.httpError(statusCode) {
                        busy = false
                        error = TalkScreen.errorMessage(statusCode)
                    } catch {
                        busy = false
                        self.error = "Couldn't reach itunda. Check your connection and try again."
                    }
                }
            }
        }
        .onChange(of: locationFetcher.errorMessage) { newValue in
            if let newValue { error = newValue }
        }
    }
}

// Real dual-neighborhood switcher (2026-08-04) -- mirrors Android SuperAppTabs.kt's
// HoodTab showNeighborhoodPrompt overlay exactly: the primary NeighborhoodSetupPrompt
// plus an Add/Change/Remove row for the optional second neighborhood (e.g. home + work).
struct NeighborhoodSwitcherOverlay: View {
    let secondNeighborhoodName: String?
    let onPrimaryDone: (String) -> Void
    let onAddSecondTapped: () -> Void
    let onRemoveSecond: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.32).ignoresSafeArea()
                .onTapGesture { onDismiss() }
            VStack(spacing: 10) {
                NeighborhoodSetupPrompt(onDone: onPrimaryDone)
                HStack {
                    Text(secondNeighborhoodName.map { "Second: \($0)" } ?? "Add a second neighborhood")
                        .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    HStack(spacing: 12) {
                        Button(secondNeighborhoodName != nil ? "Change" : "Add") { onAddSecondTapped() }
                            .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                        if secondNeighborhoodName != nil {
                            Button("Remove") { onRemoveSecond() }
                                .font(.caption).bold().foregroundColor(.red)
                        }
                    }
                }
                .padding(16)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
        }
    }
}

/// Card-shaped loading placeholders keep every Hood feed legible while its real
/// neighborhood data is loading, instead of showing a disconnected spinner.
struct HoodFeedSkeleton: View {
    var body: some View {
        VStack(spacing: IDS.Layout.cardGap) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 10) {
                    RoundedRectangle(cornerRadius: 5).fill(IDS.Colors.chipBackground).frame(width: 92, height: 12)
                    RoundedRectangle(cornerRadius: 6).fill(IDS.Colors.chipBackground).frame(maxWidth: .infinity).frame(height: 18)
                    RoundedRectangle(cornerRadius: 5).fill(IDS.Colors.chipBackground).frame(maxWidth: .infinity).frame(height: 12)
                    RoundedRectangle(cornerRadius: 5).fill(IDS.Colors.chipBackground).frame(width: 160, height: 12)
                }
                .padding(18)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
            }
        }
        .accessibilityLabel("Loading Hood content")
    }
}

/// Real 당근마켓 (Danggeun/Karrot Market)-style neighborhood marketplace (2026-07-18) --
/// iOS mirror of Android's HoodTab (SuperAppTabs.kt). See NetworkClient.swift's
/// Marketplace extension and rw.itunda.marketplace.MarketplaceService's own doc
/// comment for the full backend account, including the honest "no real location data"
/// scope this screen inherits unchanged.
// HoodScreen's own segmented Picker (Market/Life/Jobs/Home) was retired 2026-08-10:
// real user correction -- nesting Marketplace/Community/Jobs/Property behind one
// Explore row with an internal switcher is a tab bar inside a tab, noise a flat
// catalog shouldn't have (same fix applied to ShopScreen's Shop/Eats toggle and to
// Android's identical HoodTab chip row -- see SuperAppTabs.kt's HoodSectionScreen
// doc comment for the full account). The shared 당근-style shell below (neighborhood
// name row, switcher overlay, dual-neighborhood prompt) is real, deliberately
// Karrot-sourced UI, not a simple toggle -- kept, just parameterized by a fixed
// `mode` instead of internal switchable state, mounted once per flat destination
// (ContentView.swift's showMarketplace/showCommunity/showJobs/showProperty).
enum HoodMode { case marketplace, community, jobs, property }

struct HoodSectionScreen: View {
    let mode: HoodMode
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    @State private var neighborhoodName: String?
    @State private var neighborhoodVerificationCount = 0
    // Real dual-neighborhood support (2026-08-04) -- see NetworkClient.setSecondNeighborhood's
    // own doc comment; mirrors Android SuperAppTabs.kt's HoodSectionScreen exactly.
    @State private var secondNeighborhoodName: String?
    @State private var showNeighborhoodSwitcher = false
    @State private var showSecondNeighborhoodPrompt = false

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                Text("📍")
                Text(
                    [neighborhoodName, secondNeighborhoodName].compactMap { $0 }.isEmpty
                        ? "Choose your neighborhood in any Hood service"
                        : [neighborhoodName, secondNeighborhoodName].compactMap { $0 }.joined(separator: " · ")
                        + (neighborhoodVerificationCount > 0 ? " · confirmed \(neighborhoodVerificationCount)×" : "")
                )
                    .font(IDS.Typography.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
                Spacer()
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, 10)
            .contentShape(Rectangle())
            .onTapGesture { showNeighborhoodSwitcher = true }

            switch mode {
            case .marketplace:
                MarketplaceContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .community:
                CommunityContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .jobs:
                JobsContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .property:
                PropertyContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            if let profile = try? await NetworkClient.shared.getProfile() {
                neighborhoodName = profile.user.neighborhood
                neighborhoodVerificationCount = profile.user.neighborhoodVerificationCount ?? 0
                secondNeighborhoodName = profile.user.secondNeighborhood
            }
        }
        .overlay {
            if showNeighborhoodSwitcher {
                NeighborhoodSwitcherOverlay(
                    secondNeighborhoodName: secondNeighborhoodName,
                    onPrimaryDone: { name in
                        neighborhoodName = name
                        showNeighborhoodSwitcher = false
                    },
                    onAddSecondTapped: { showNeighborhoodSwitcher = false; showSecondNeighborhoodPrompt = true },
                    onRemoveSecond: {
                        Task {
                            if let res = try? await NetworkClient.shared.clearSecondNeighborhood() {
                                secondNeighborhoodName = res.user.secondNeighborhood
                            }
                        }
                    },
                    onDismiss: { showNeighborhoodSwitcher = false }
                )
            }
        }
        .overlay {
            if showSecondNeighborhoodPrompt {
                ZStack {
                    Color.black.opacity(0.32).ignoresSafeArea()
                        .onTapGesture { showSecondNeighborhoodPrompt = false }
                    NeighborhoodSetupPrompt(isSecond: true, onDone: { name in
                        secondNeighborhoodName = name
                        showSecondNeighborhoodPrompt = false
                    })
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                }
            }
        }
    }
}

