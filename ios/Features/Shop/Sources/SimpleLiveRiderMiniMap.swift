import SwiftUI
import MapLibre
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real live rider-location tracking for Commerce orders (item 230) -- found via a
/// defined-but-uncalled-endpoint sweep, see NetworkClient.getOrderRiderLocation's own
/// doc comment. A deliberately simpler sibling of LiveRiderMiniMap.swift (Eats): no
/// route line, no fixed from/to endpoints, since Commerce's OrderDto carries no
/// delivery coordinates to draw a route toward -- just the rider's own live position,
/// re-centered as it updates. Straight port of bank-mfe's SimpleLiveRiderMap.tsx /
/// Android's SimpleLiveRiderMiniMap.kt (shipped first, 2026-08-05).
private let simpleMiniTilesURL = "http://192.168.252.4:8090/rwanda/{z}/{x}/{y}.mvt"
private let simpleMiniStyleJSON = """
{
  "version": 8,
  "sources": {
    "rwanda": { "type": "vector", "tiles": ["\(simpleMiniTilesURL)"], "minzoom": 0, "maxzoom": 14 }
  },
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#f2efe9" } },
    { "id": "landcover", "type": "fill", "source": "rwanda", "source-layer": "landcover",
      "paint": { "fill-color": "#d8e8c8", "fill-opacity": 0.6 } },
    { "id": "water", "type": "fill", "source": "rwanda", "source-layer": "water",
      "paint": { "fill-color": "#a8d0e6" } },
    { "id": "transportation-minor", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["!", ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false]],
      "paint": { "line-color": "#ffffff", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 16, 3] } },
    { "id": "transportation-major", "type": "line", "source": "rwanda", "source-layer": "transportation",
      "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      "paint": { "line-color": "#f5c96b", "line-width": ["interpolate", ["linear"], ["zoom"], 6, 1, 16, 5] } }
  ]
}
"""

private func writeSimpleMiniStyleFile() -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent("itunda-simple-live-rider-mini-style.json")
    try? simpleMiniStyleJSON.write(to: url, atomically: true, encoding: .utf8)
    return url
}

private func simpleLiveRiderTimeAgo(_ iso: String) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else { return "" }
    let seconds = max(0, Int(Date().timeIntervalSince(date)))
    if seconds < 5 { return "just now" }
    if seconds < 60 { return "\(seconds)s ago" }
    return "\(seconds / 60)m ago"
}

struct SimpleLiveRiderMiniMap: View {
    let orderId: String

    @State private var location: RiderLocationDto?
    @State private var available: Bool?
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            SimpleLiveRiderMiniMapRepresentable(riderLocation: location)
                .frame(height: 200)
                .cornerRadius(12)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if available == false {
                Text("Waiting for your rider's real location…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if let location {
                Text("🛵 Rider location updated \(simpleLiveRiderTimeAgo(location.updatedAt))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .task(id: orderId) {
            while !Task.isCancelled {
                do {
                    let res = try await NetworkClient.shared.getOrderRiderLocation(orderId)
                    available = res.available
                    error = nil
                    if let loc = res.location { location = loc }
                } catch {
                    self.error = "Could not load your rider's location."
                }
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }
}

private struct SimpleLiveRiderMiniMapRepresentable: UIViewRepresentable {
    let riderLocation: RiderLocationDto?

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: writeSimpleMiniStyleFile())
        mapView.delegate = context.coordinator
        mapView.isUserInteractionEnabled = false
        mapView.setCenter(CLLocationCoordinate2D(latitude: -1.9441, longitude: 30.0619), zoomLevel: 12, animated: false)
        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.applyRiderLocation(riderLocation, to: mapView)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        private var riderAnnotation: MLNPointAnnotation?
        private var centered = false
        private static let riderAnnotationTitle = "itunda-simple-live-rider-mini-rider"

        func applyRiderLocation(_ location: RiderLocationDto?, to mapView: MLNMapView) {
            guard let location else { return }
            let coordinate = CLLocationCoordinate2D(latitude: location.latitude, longitude: location.longitude)
            if let riderAnnotation {
                riderAnnotation.coordinate = coordinate
            } else {
                let annotation = MLNPointAnnotation()
                annotation.coordinate = coordinate
                annotation.title = Self.riderAnnotationTitle
                riderAnnotation = annotation
                mapView.addAnnotation(annotation)
            }
            if !centered {
                centered = true
                mapView.setCenter(coordinate, zoomLevel: 14, animated: true)
            }
        }

        func mapView(_ mapView: MLNMapView, viewFor annotation: MLNAnnotation) -> MLNAnnotationView? {
            guard annotation.title == Self.riderAnnotationTitle else { return nil }
            let identifier = "itunda-simple-live-rider-mini-rider-view"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) ?? MLNAnnotationView(reuseIdentifier: identifier)
            view.frame = CGRect(x: 0, y: 0, width: 28, height: 28)
            view.backgroundColor = .clear
            let label = UILabel(frame: view.bounds)
            label.text = "🛵"
            label.font = .systemFont(ofSize: 22)
            label.textAlignment = .center
            view.subviews.forEach { $0.removeFromSuperview() }
            view.addSubview(label)
            return view
        }

        func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool { false }
    }
}
