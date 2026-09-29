import Foundation
import CoreLocation

/// Real "silent" one-shot location fetch -- no UI, no error surfaced to the user (a
/// customer who denies/lacks location just never sees the location-gated content),
/// same discipline RideScreenView's own RideLocationFetcher (App/Sources-only, not
/// promoted) establishes for an interactive fetch. Promoted from App/Sources/
/// ShopScreen.swift into CoreDesignSystem (2026-09-06, Eats product-completeness
/// pass) -- EatsDishGrid.swift needed the same real utility once Eats moved into its
/// own Feature module, and ShopScreen.swift (still App-only) is its other real
/// consumer, so this follows the same promote-instead-of-duplicate precedent
/// RouteMiniMap.swift's own move in this same pass already established.
public final class SilentLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published public var coordinate: CLLocationCoordinate2D?
    private let manager = CLLocationManager()

    override public init() {
        super.init()
        manager.delegate = self
    }

    public func requestLocation() {
        let status = manager.authorizationStatus
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.requestLocation()
        }
    }

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        coordinate = locations.last?.coordinate
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Real, non-critical -- the caller just won't get a location update.
    }
}
