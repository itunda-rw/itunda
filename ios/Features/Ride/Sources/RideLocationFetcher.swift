import SwiftUI
import CoreLocation

/// Real "my location" via Apple's own CLLocationManager, runtime-permission-gated --
/// same real technique HoodScreen.swift/MapScreenView.swift each already establish
/// their own copy of, reused here for the passenger's one-tap pickup location.
/// Internal (not private/fileprivate), not just file-scoped, since RideDriverContent
/// (moved to its own RideDriverContent.swift file, 2026-09-06 Rideshare
/// product-completeness pass) reuses this same copy for the driver's own
/// go-online location update.
final class RideLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var errorMessage: String?
    private let manager = CLLocationManager()

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
        coordinate = locations.last?.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        errorMessage = "Could not access your real location right now."
    }
}

